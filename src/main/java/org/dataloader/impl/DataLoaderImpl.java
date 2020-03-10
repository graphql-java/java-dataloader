/*
 * Copyright (c) 2016 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.dataloader.impl;

import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.CacheMap;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DispatchResult;
import org.dataloader.MappedBatchLoader;
import org.dataloader.MappedBatchLoaderWithContext;
import org.dataloader.Try;
import org.dataloader.annotations.Internal;
import org.dataloader.stats.Statistics;
import org.dataloader.stats.StatisticsCollector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.dataloader.impl.Assertions.assertState;
import static org.dataloader.impl.Assertions.nonNull;

@Internal
public class DataLoaderImpl<K, V> implements DataLoader<K, V> {

    private final Object batchLoadFunction;
    private final DataLoaderOptions loaderOptions;
    private final CacheMap<Object, CompletableFuture<V>> futureCache;
    private final CacheMap<Object, V> valueCache;
    private final List<LoaderQueueEntry<K, CompletableFuture<V>>> loaderQueue;
    private final StatisticsCollector stats;

    @Internal
    public DataLoaderImpl(Object batchLoadFunction, DataLoaderOptions options) {
        DataLoaderOptions loaderOptions = options == null ? new DataLoaderOptions() : options;
        // order of keys matter in data loader
        this.stats = nonNull(loaderOptions.getStatisticsCollector());
        this.batchLoadFunction = batchLoadFunction;
        this.loaderOptions = loaderOptions;
        this.futureCache = determinePromiseCacheMap(loaderOptions);
        this.valueCache = determineValueCacheMap(loaderOptions);
        this.loaderQueue = new ArrayList<>();
    }

    @Override
    public CompletableFuture<V> load(K key) {
        return load(key, null);
    }

    @Override
    public Optional<CompletableFuture<V>> getIfPresent(K key) {
        return getIfPresentImpl(key);
    }

    @Override
    public Optional<CompletableFuture<V>> getIfCompleted(K key) {
        return getIfCompletedImpl(key);
    }

    @Override
    public CompletableFuture<V> load(K key, Object keyContext) {
        return loadImpl(key, keyContext);
    }

    @Override
    public CompletableFuture<List<V>> loadMany(List<K> keys) {
        return loadMany(keys, Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<V>> loadMany(List<K> keys, List<Object> keyContexts) {
        nonNull(keys);
        nonNull(keyContexts);

        synchronized (this) {
            List<CompletableFuture<V>> collect = new ArrayList<>();
            for (int i = 0; i < keys.size(); i++) {
                K key = keys.get(i);
                Object keyContext = null;
                if (i < keyContexts.size()) {
                    keyContext = keyContexts.get(i);
                }
                collect.add(load(key, keyContext));
            }
            return CompletableFutureKit.allOf(collect);
        }
    }

    @Override
    public CompletableFuture<List<V>> dispatch() {
        return dispatchImpl().getPromisedResults();
    }

    @Override
    public DispatchResult<V> dispatchWithCounts() {
        return dispatchImpl();
    }

    @Override
    public List<V> dispatchAndJoin() {

        List<V> joinedResults = dispatch().join();
        List<V> results = new ArrayList<>(joinedResults);
        while (this.dispatchDepth() > 0) {
            joinedResults = dispatch().join();
            results.addAll(joinedResults);
        }
        return results;
    }

    @Override
    public int dispatchDepth() {
        synchronized (this) {
            return loaderQueue.size();
        }
    }

    public DataLoader<K, V> clear(K key) {
        Object cacheKey = getCacheKey(key);
        synchronized (this) {
            futureCache.delete(cacheKey);
            if (valueCache != null) {
                valueCache.delete(cacheKey);
            }
        }
        return this;
    }

    public DataLoader<K, V> clearAll() {
        synchronized (this) {
            futureCache.clear();
            if (valueCache != null) {
                valueCache.clear();
            }
        }
        return this;
    }

    public DataLoader<K, V> prime(K key, V value) {
        Object cacheKey = getCacheKey(key);
        synchronized (this) {
            if (!futureCache.containsKey(cacheKey)) {
                futureCache.set(cacheKey, CompletableFuture.completedFuture(value));
            }
            if (valueCache != null) {
                if (!valueCache.containsKey(cacheKey)) {
                    valueCache.set(cacheKey, value);
                }
            }
        }
        return this;
    }

    public DataLoader<K, V> prime(K key, Exception error) {
        Object cacheKey = getCacheKey(key);
        synchronized (this) {
            if (!futureCache.containsKey(cacheKey)) {
                futureCache.set(cacheKey, CompletableFutureKit.failedFuture(error));
            }
            if (valueCache != null) {
                valueCache.delete(cacheKey);
            }
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public Object getCacheKey(K key) {
        return loaderOptions.cacheKeyFunction()
                .map(cacheKeyFunction -> cacheKeyFunction.getKey(key))
                .orElse(key);
    }

    @Override
    public Statistics getStatistics() {
        return stats.getStatistics();
    }

    @SuppressWarnings("unchecked")
    private CacheMap<Object, CompletableFuture<V>> determinePromiseCacheMap(DataLoaderOptions loaderOptions) {
        CacheMap<?, ?> promiseMap = loaderOptions.promiseCacheMap().orElse(CacheMap.simpleMap());
        return (CacheMap<Object, CompletableFuture<V>>) promiseMap;
    }

    @SuppressWarnings("unchecked")
    private CacheMap<Object, V> determineValueCacheMap(DataLoaderOptions loaderOptions) {
        return loaderOptions.cacheMap().isPresent() ? (CacheMap<Object, V>) loaderOptions.cacheMap().get() : null;
    }

    Optional<CompletableFuture<V>> getIfPresentImpl(K key) {
        Object cacheKey = getCacheKey(nonNull(key));
        boolean cachingEnabled = loaderOptions.cachingEnabled();
        if (cachingEnabled) {
            synchronized (this) {
                if (futureCache.containsKey(cacheKey)) {
                    stats.incrementCacheHitCount();
                    return Optional.of(futureCache.get(cacheKey));
                }
            }
        }
        return Optional.empty();
    }

    Optional<CompletableFuture<V>> getIfCompletedImpl(K key) {
        return getIfPresent(key).filter(CompletableFuture::isDone);
    }

    CompletableFuture<V> loadImpl(K key, Object loadContext) {
        Object cacheKey = getCacheKey(nonNull(key));
        boolean batchingEnabled = loaderOptions.batchingEnabled();
        boolean cachingEnabled = loaderOptions.cachingEnabled();

        synchronized (this) {
            stats.incrementLoadCount();

            if (cachingEnabled) {
                if (futureCache.containsKey(cacheKey)) {
                    stats.incrementCacheHitCount();
                    return futureCache.get(cacheKey);
                }
                if (valueCache != null) {
                    V value = valueCache.get(cacheKey);
                    if (value != null) {
                        stats.incrementCacheHitCount();
                        CompletableFuture<V> completedValue = CompletableFuture.completedFuture(value);
                        futureCache.set(cacheKey, completedValue);
                        return completedValue;
                    }
                }
            }

            CompletableFuture<V> future = new CompletableFuture<>();
            if (batchingEnabled) {
                loaderQueue.add(new LoaderQueueEntry<>(key, future, loadContext));
            } else {
                stats.incrementBatchLoadCountBy(1);
                // immediate execution of batch function
                future = invokeLoaderImmediately(key, loadContext);
            }
            if (cachingEnabled) {
                futureCache.set(cacheKey, future);
                if (valueCache != null) {
                    // when the promise finally finishes back update the value cache
                    future.thenAccept(value -> valueCache.set(cacheKey, value));
                }
            }
            return future;
        }
    }

    DispatchResult<V> dispatchImpl() {
        boolean batchingEnabled = loaderOptions.batchingEnabled();
        //
        // we copy the pre-loaded set of futures ready for dispatch
        final List<K> keys = new ArrayList<>();
        final List<Object> callContexts = new ArrayList<>();
        final List<CompletableFuture<V>> queuedFutures = new ArrayList<>();
        synchronized (this) {
            loaderQueue.forEach(entry -> {
                keys.add(entry.key);
                queuedFutures.add(entry.value);
                callContexts.add(entry.callContext);
            });
            loaderQueue.clear();
        }
        if (!batchingEnabled || keys.isEmpty()) {
            return new DispatchResult<>(CompletableFuture.completedFuture(emptyList()), 0);
        }
        final int totalEntriesHandled = keys.size();
        //
        // order of keys -> values matter in data loader hence the use of linked hash map
        //
        // See https://github.com/facebook/dataloader/blob/master/README.md for more details
        //

        //
        // when the promised list of values completes, we transfer the values into
        // the previously cached future objects that the client already has been given
        // via calls to load("foo") and loadMany(["foo","bar"])
        //
        int maxBatchSize = loaderOptions.maxBatchSize();
        CompletableFuture<List<V>> futureList;
        if (maxBatchSize > 0 && maxBatchSize < keys.size()) {
            futureList = sliceIntoBatchesOfBatches(keys, queuedFutures, callContexts, maxBatchSize);
        } else {
            futureList = dispatchQueueBatch(keys, callContexts, queuedFutures);
        }
        return new DispatchResult<>(futureList, totalEntriesHandled);
    }

    private CompletableFuture<List<V>> sliceIntoBatchesOfBatches(List<K> keys, List<CompletableFuture<V>> queuedFutures, List<Object> callContexts, int maxBatchSize) {
        // the number of keys is > than what the batch loader function can accept
        // so make multiple calls to the loader
        List<CompletableFuture<List<V>>> allBatches = new ArrayList<>();
        int len = keys.size();
        int batchCount = (int) Math.ceil(len / (double) maxBatchSize);
        for (int i = 0; i < batchCount; i++) {

            int fromIndex = i * maxBatchSize;
            int toIndex = Math.min((i + 1) * maxBatchSize, len);

            List<K> subKeys = keys.subList(fromIndex, toIndex);
            List<CompletableFuture<V>> subFutures = queuedFutures.subList(fromIndex, toIndex);
            List<Object> subCallContexts = callContexts.subList(fromIndex, toIndex);

            allBatches.add(dispatchQueueBatch(subKeys, subCallContexts, subFutures));
        }
        //
        // now reassemble all the futures into one that is the complete set of results
        return CompletableFuture.allOf(allBatches.toArray(new CompletableFuture[0]))
                .thenApply(v -> allBatches.stream()
                        .map(CompletableFuture::join)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<List<V>> dispatchQueueBatch(List<K> keys, List<Object> callContexts, List<CompletableFuture<V>> queuedFutures) {
        stats.incrementBatchLoadCountBy(keys.size());
        CompletionStage<List<V>> batchLoad = invokeLoader(keys, callContexts);
        return batchLoad
                .toCompletableFuture()
                .thenApply(values -> {
                    assertResultSize(keys, values);

                    List<K> clearCacheKeys = new ArrayList<>();
                    for (int idx = 0; idx < queuedFutures.size(); idx++) {
                        V value = values.get(idx);
                        CompletableFuture<V> future = queuedFutures.get(idx);
                        if (value instanceof Throwable) {
                            stats.incrementLoadErrorCount();
                            future.completeExceptionally((Throwable) value);
                            clearCacheKeys.add(keys.get(idx));
                        } else if (value instanceof Try) {
                            // we allow the batch loader to return a Try so we can better represent a computation
                            // that might have worked or not.
                            Try<V> tryValue = (Try<V>) value;
                            if (tryValue.isSuccess()) {
                                future.complete(tryValue.get());
                            } else {
                                stats.incrementLoadErrorCount();
                                future.completeExceptionally(tryValue.getThrowable());
                                clearCacheKeys.add(keys.get(idx));
                            }
                        } else {
                            future.complete(value);
                        }
                    }
                    possiblyClearCacheEntriesOnExceptions(clearCacheKeys);
                    return values;
                }).exceptionally(ex -> {
                    stats.incrementBatchLoadExceptionCount();
                    for (int idx = 0; idx < queuedFutures.size(); idx++) {
                        K key = keys.get(idx);
                        CompletableFuture<V> future = queuedFutures.get(idx);
                        future.completeExceptionally(ex);
                        // clear any cached view of this key because they all failed
                        clear(key);
                    }
                    return emptyList();
                });
    }

    private void assertResultSize(List<K> keys, List<V> values) {
        assertState(keys.size() == values.size(), "The size of the promised values MUST be the same size as the key list");
    }

    private void possiblyClearCacheEntriesOnExceptions(List<K> keys) {
        if (keys.isEmpty()) {
            return;
        }
        // by default we don't clear the cached view of this entry to avoid
        // frequently loading the same error.  This works for short lived request caches
        // but might work against long lived caches.  Hence we have an option that allows
        // it to be cleared
        if (!loaderOptions.cachingExceptionsEnabled()) {
            keys.forEach(this::clear);
        }
    }

    CompletableFuture<V> invokeLoaderImmediately(K key, Object keyContext) {
        List<K> keys = singletonList(key);
        CompletionStage<V> singleLoadCall;
        try {
            Object context = loaderOptions.getBatchLoaderContextProvider().getContext();
            BatchLoaderEnvironment environment = BatchLoaderEnvironment.newBatchLoaderEnvironment()
                    .context(context).keyContexts(keys, singletonList(keyContext)).build();
            if (isMapLoader()) {
                singleLoadCall = invokeMapBatchLoader(keys, environment).thenApply(list -> list.get(0));
            } else {
                singleLoadCall = invokeListBatchLoader(keys, environment).thenApply(list -> list.get(0));
            }
            return singleLoadCall.toCompletableFuture();
        } catch (Exception e) {
            return CompletableFutureKit.failedFuture(e);
        }
    }

    CompletionStage<List<V>> invokeLoader(List<K> keys, List<Object> keyContexts) {
        CompletionStage<List<V>> batchLoad;
        try {
            Object context = loaderOptions.getBatchLoaderContextProvider().getContext();
            BatchLoaderEnvironment environment = BatchLoaderEnvironment.newBatchLoaderEnvironment()
                    .context(context).keyContexts(keys, keyContexts).build();
            if (isMapLoader()) {
                batchLoad = invokeMapBatchLoader(keys, environment);
            } else {
                batchLoad = invokeListBatchLoader(keys, environment);
            }
        } catch (Exception e) {
            batchLoad = CompletableFutureKit.failedFuture(e);
        }
        return batchLoad;
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<List<V>> invokeListBatchLoader(List<K> keys, BatchLoaderEnvironment environment) {
        CompletionStage<List<V>> loadResult;
        if (batchLoadFunction instanceof BatchLoaderWithContext) {
            loadResult = ((BatchLoaderWithContext<K, V>) batchLoadFunction).load(keys, environment);
        } else {
            loadResult = ((BatchLoader<K, V>) batchLoadFunction).load(keys);
        }
        return nonNull(loadResult, "Your batch loader function MUST return a non null CompletionStage promise");
    }

    /*
     * Turns a map of results that MAY be smaller than the key list back into a list by mapping null
     * to missing elements.
     */
    @SuppressWarnings("unchecked")
    private CompletionStage<List<V>> invokeMapBatchLoader(List<K> keys, BatchLoaderEnvironment environment) {
        CompletionStage<Map<K, V>> loadResult;
        Set<K> setOfKeys = new LinkedHashSet<>(keys);
        if (batchLoadFunction instanceof MappedBatchLoaderWithContext) {
            loadResult = ((MappedBatchLoaderWithContext<K, V>) batchLoadFunction).load(setOfKeys, environment);
        } else {
            loadResult = ((MappedBatchLoader<K, V>) batchLoadFunction).load(setOfKeys);
        }
        CompletionStage<Map<K, V>> mapBatchLoad = nonNull(loadResult, "Your batch loader function MUST return a non null CompletionStage promise");
        return mapBatchLoad.thenApply(map -> {
            List<V> values = new ArrayList<>();
            for (K key : keys) {
                V value = map.get(key);
                values.add(value);
            }
            return values;
        });
    }

    private boolean isMapLoader() {
        return batchLoadFunction instanceof MappedBatchLoader || batchLoadFunction instanceof MappedBatchLoaderWithContext;
    }

    static class LoaderQueueEntry<K, V> {

        final K key;
        final V value;
        final Object callContext;

        LoaderQueueEntry(K key, V value, Object callContext) {
            this.key = key;
            this.value = value;
            this.callContext = callContext;
        }
    }
}
