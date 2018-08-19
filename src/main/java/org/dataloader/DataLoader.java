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

package org.dataloader;

import org.dataloader.impl.CompletableFutureKit;
import org.dataloader.stats.Statistics;
import org.dataloader.stats.StatisticsCollector;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.dataloader.impl.Assertions.assertState;
import static org.dataloader.impl.Assertions.nonNull;

/**
 * Data loader is a utility class that allows batch loading of data that is identified by a set of unique keys. For
 * each key that is loaded a separate {@link CompletableFuture} is returned, that completes as the batch function completes.
 * <p>
 * With batching enabled the execution will start after calling {@link DataLoader#dispatch()}, causing the queue of
 * loaded keys to be sent to the batch function, clears the queue, and returns a promise to the values.
 * <p>
 * As {@link org.dataloader.BatchLoader} batch functions are executed the resulting futures are cached using a cache
 * implementation of choice, so they will only execute once. Individual cache keys can be cleared, so they will
 * be re-fetched when referred to again.
 * <p>
 * It is also possible to clear the cache entirely, and prime it with values before they are used.
 * <p>
 * Both caching and batching can be disabled. Configuration of the data loader is done by providing a
 * {@link DataLoaderOptions} instance on creation.
 * <p>
 * A call to the batch loader might result in individual exception failures for item with the returned list.  if
 * you want to capture these specific item failures then use {@link org.dataloader.Try} as a return value and
 * create the data loader with {@link #newDataLoaderWithTry(BatchLoader)} form.  The Try values will be interpreted
 * as either success values or cause the {@link #load(Object)} promise to complete exceptionally.
 *
 * @param <K> type parameter indicating the type of the data load keys
 * @param <V> type parameter indicating the type of the data that is returned
 *
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 * @author <a href="https://github.com/bbakerman/">Brad Baker</a>
 */
public class DataLoader<K, V> {

    private final BatchLoader<K, V> batchLoadFunction;
    private final DataLoaderOptions loaderOptions;
    private final CacheMap<Object, CompletableFuture<V>> futureCache;
    private final List<SimpleImmutableEntry<K, CompletableFuture<V>>> loaderQueue;
    private final StatisticsCollector stats;

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoader<K, V> batchLoadFunction) {
        return newDataLoader(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function with the provided options
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoader<K, V> batchLoadFunction, DataLoaderOptions options) {
        return new DataLoader<>(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * This allows you to capture both the value that might be returned and also whether exception that might have occurred getting that individual value.  If its important you to
     * know gther exact status of each item in a batch call and whether it threw exceptions when fetched then
     * you can use this form to create the data loader.
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     */
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(BatchLoader<K, Try<V>> batchLoadFunction) {
        return newDataLoaderWithTry(batchLoadFunction, null);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and with the provided options
     * where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param options           the options to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @see #newDataLoaderWithTry(BatchLoader)
     */
    @SuppressWarnings("unchecked")
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(BatchLoader<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return new DataLoader<>((BatchLoader<K, V>) batchLoadFunction, options);
    }


    /**
     * Creates a new data loader with the provided batch load function, and default options.
     *
     * @param batchLoadFunction the batch load function to use
     */
    public DataLoader(BatchLoader<K, V> batchLoadFunction) {
        this(batchLoadFunction, null);
    }

    /**
     * Creates a new data loader with the provided batch load function and options.
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the batch load options
     */
    public DataLoader(BatchLoader<K, V> batchLoadFunction, DataLoaderOptions options) {
        this.batchLoadFunction = nonNull(batchLoadFunction);
        this.loaderOptions = options == null ? new DataLoaderOptions() : options;
        this.futureCache = determineCacheMap(loaderOptions);
        // order of keys matter in data loader
        this.loaderQueue = new ArrayList<>();
        this.stats = nonNull(this.loaderOptions.getStatisticsCollector());
    }

    @SuppressWarnings("unchecked")
    private CacheMap<Object, CompletableFuture<V>> determineCacheMap(DataLoaderOptions loaderOptions) {
        return loaderOptions.cacheMap().isPresent() ? (CacheMap<Object, CompletableFuture<V>>) loaderOptions.cacheMap().get() : CacheMap.simpleMap();
    }

    /**
     * Requests to load the data with the specified key asynchronously, and returns a future of the resulting value.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     *
     * @param key the key to load
     *
     * @return the future of the value
     */
    public CompletableFuture<V> load(K key) {
        synchronized (this) {
            Object cacheKey = getCacheKey(nonNull(key));
            stats.incrementLoadCount();

            boolean batchingEnabled = loaderOptions.batchingEnabled();
            boolean cachingEnabled = loaderOptions.cachingEnabled();

            if (cachingEnabled) {
                if (futureCache.containsKey(cacheKey)) {
                    stats.incrementCacheHitCount();
                    return futureCache.get(cacheKey);
                }
            }

            CompletableFuture<V> future = new CompletableFuture<>();
            if (batchingEnabled) {
                loaderQueue.add(new SimpleImmutableEntry<>(key, future));
            } else {
                stats.incrementBatchLoadCountBy(1);
                // immediate execution of batch function
                Object context = loaderOptions.getBatchContextProvider().get();
                CompletableFuture<List<V>> batchedLoad = batchLoadFunction
                        .load(singletonList(key), context)
                        .toCompletableFuture();
                future = batchedLoad
                        .thenApply(list -> list.get(0));
            }
            if (cachingEnabled) {
                futureCache.set(cacheKey, future);
            }
            return future;
        }
    }

    /**
     * Requests to load the list of data provided by the specified keys asynchronously, and returns a composite future
     * of the resulting values.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     *
     * @param keys the list of keys to load
     *
     * @return the composite future of the list of values
     */
    public CompletableFuture<List<V>> loadMany(List<K> keys) {
        synchronized (this) {
            List<CompletableFuture<V>> collect = keys.stream()
                    .map(this::load)
                    .collect(Collectors.toList());

            return CompletableFutureKit.allOf(collect);
        }
    }

    /**
     * Dispatches the queued load requests to the batch execution function and returns a promise of the result.
     * <p>
     * If batching is disabled, or there are no queued requests, then a succeeded promise is returned.
     *
     * @return the promise of the queued load requests
     */
    public CompletableFuture<List<V>> dispatch() {
        boolean batchingEnabled = loaderOptions.batchingEnabled();
        //
        // we copy the pre-loaded set of futures ready for dispatch
        final List<K> keys = new ArrayList<>();
        final List<CompletableFuture<V>> queuedFutures = new ArrayList<>();
        synchronized (this) {
            loaderQueue.forEach(entry -> {
                keys.add(entry.getKey());
                queuedFutures.add(entry.getValue());
            });
            loaderQueue.clear();
        }
        if (!batchingEnabled || keys.size() == 0) {
            return CompletableFuture.completedFuture(emptyList());
        }
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
        if (maxBatchSize > 0 && maxBatchSize < keys.size()) {
            return sliceIntoBatchesOfBatches(keys, queuedFutures, maxBatchSize);
        } else {
            return dispatchQueueBatch(keys, queuedFutures);
        }
    }

    private CompletableFuture<List<V>> sliceIntoBatchesOfBatches(List<K> keys, List<CompletableFuture<V>> queuedFutures, int maxBatchSize) {
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

            allBatches.add(dispatchQueueBatch(subKeys, subFutures));
        }
        //
        // now reassemble all the futures into one that is the complete set of results
        return CompletableFuture.allOf(allBatches.toArray(new CompletableFuture[allBatches.size()]))
                .thenApply(v -> allBatches.stream()
                        .map(CompletableFuture::join)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<List<V>> dispatchQueueBatch(List<K> keys, List<CompletableFuture<V>> queuedFutures) {
        stats.incrementBatchLoadCountBy(keys.size());
        CompletionStage<List<V>> batchLoad;
        try {
            Object context = loaderOptions.getBatchContextProvider().get();
            batchLoad = nonNull(batchLoadFunction.load(keys, context), "Your batch loader function MUST return a non null CompletionStage promise");
        } catch (Exception e) {
            batchLoad = CompletableFutureKit.failedFuture(e);
        }
        return batchLoad
                .toCompletableFuture()
                .thenApply(values -> {
                    assertState(keys.size() == values.size(), "The size of the promised values MUST be the same size as the key list");

                    for (int idx = 0; idx < queuedFutures.size(); idx++) {
                        Object value = values.get(idx);
                        CompletableFuture<V> future = queuedFutures.get(idx);
                        if (value instanceof Throwable) {
                            stats.incrementLoadErrorCount();
                            future.completeExceptionally((Throwable) value);
                            // we don't clear the cached view of this entry to avoid
                            // frequently loading the same error
                        } else if (value instanceof Try) {
                            // we allow the batch loader to return a Try so we can better represent a computation
                            // that might have worked or not.
                            Try<V> tryValue = (Try<V>) value;
                            if (tryValue.isSuccess()) {
                                future.complete(tryValue.get());
                            } else {
                                stats.incrementLoadErrorCount();
                                future.completeExceptionally(tryValue.getThrowable());
                            }
                        } else {
                            V val = (V) value;
                            future.complete(val);
                        }
                    }
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

    /**
     * Normally {@link #dispatch()} is an asynchronous operation but this version will 'join' on the
     * results if dispatch and wait for them to complete.  If the {@link CompletableFuture} callbacks make more
     * calls to this data loader then the {@link #dispatchDepth()} will be &gt; 0 and this method will loop
     * around and wait for any other extra batch loads to occur.
     *
     * @return the list of all results when the {@link #dispatchDepth()} reached 0
     */
    public List<V> dispatchAndJoin() {
        List<V> results = new ArrayList<>();

        List<V> joinedResults = dispatch().join();
        results.addAll(joinedResults);
        while (this.dispatchDepth() > 0) {
            joinedResults = dispatch().join();
            results.addAll(joinedResults);
        }
        return results;
    }


    /**
     * @return the depth of the batched key loads that need to be dispatched
     */
    public int dispatchDepth() {
        synchronized (this) {
            return loaderQueue.size();
        }
    }


    /**
     * Clears the future with the specified key from the cache, if caching is enabled, so it will be re-fetched
     * on the next load request.
     *
     * @param key the key to remove
     *
     * @return the data loader for fluent coding
     */
    public DataLoader<K, V> clear(K key) {
        Object cacheKey = getCacheKey(key);
        synchronized (this) {
            futureCache.delete(cacheKey);
        }
        return this;
    }

    /**
     * Clears the entire cache map of the loader.
     *
     * @return the data loader for fluent coding
     */
    public DataLoader<K, V> clearAll() {
        synchronized (this) {
            futureCache.clear();
        }
        return this;
    }

    /**
     * Primes the cache with the given key and value.
     *
     * @param key   the key
     * @param value the value
     *
     * @return the data loader for fluent coding
     */
    public DataLoader<K, V> prime(K key, V value) {
        Object cacheKey = getCacheKey(key);
        synchronized (this) {
            if (!futureCache.containsKey(cacheKey)) {
                futureCache.set(cacheKey, CompletableFuture.completedFuture(value));
            }
        }
        return this;
    }

    /**
     * Primes the cache with the given key and error.
     *
     * @param key   the key
     * @param error the exception to prime instead of a value
     *
     * @return the data loader for fluent coding
     */
    public DataLoader<K, V> prime(K key, Exception error) {
        Object cacheKey = getCacheKey(key);
        if (!futureCache.containsKey(cacheKey)) {
            futureCache.set(cacheKey, CompletableFutureKit.failedFuture(error));
        }
        return this;
    }

    /**
     * Gets the object that is used in the internal cache map as key, by applying the cache key function to
     * the provided key.
     * <p>
     * If no cache key function is present in {@link DataLoaderOptions}, then the returned value equals the input key.
     *
     * @param key the input key
     *
     * @return the cache key after the input is transformed with the cache key function
     */
    @SuppressWarnings("unchecked")
    public Object getCacheKey(K key) {
        return loaderOptions.cacheKeyFunction().isPresent() ?
                loaderOptions.cacheKeyFunction().get().getKey(key) : key;
    }

    /**
     * Gets the statistics associated with this data loader.  These will have been gather via
     * the {@link org.dataloader.stats.StatisticsCollector} passed in via {@link DataLoaderOptions#getStatisticsCollector()}
     *
     * @return statistics for this data loader
     */
    public Statistics getStatistics() {
        return stats.getStatistics();
    }

}
