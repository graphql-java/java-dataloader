package org.dataloader;

import org.dataloader.impl.CompletableFutureKit;
import org.dataloader.stats.StatisticsCollector;

import java.util.ArrayList;
import java.util.Collection;
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

/**
 * This helps break up the large DataLoader class functionality and it contains the logic to dispatch the
 * promises on behalf of its peer dataloader
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
@Internal
class DataLoaderHelper<K, V> {


    static class LoaderQueueEntry<K, V> {

        final K key;
        final V value;
        final Object callContext;

        public LoaderQueueEntry(K key, V value, Object callContext) {
            this.key = key;
            this.value = value;
            this.callContext = callContext;
        }

        K getKey() {
            return key;
        }

        V getValue() {
            return value;
        }

        Object getCallContext() {
            return callContext;
        }
    }

    private final DataLoader<K, V> dataLoader;
    private final Object batchLoadFunction;
    private final DataLoaderOptions loaderOptions;
    private final CacheMap<Object, CompletableFuture<V>> futureCache;
    private final List<LoaderQueueEntry<K, CompletableFuture<V>>> loaderQueue;
    private final StatisticsCollector stats;

    DataLoaderHelper(DataLoader<K, V> dataLoader, Object batchLoadFunction, DataLoaderOptions loaderOptions, CacheMap<Object, CompletableFuture<V>> futureCache, StatisticsCollector stats) {
        this.dataLoader = dataLoader;
        this.batchLoadFunction = batchLoadFunction;
        this.loaderOptions = loaderOptions;
        this.futureCache = futureCache;
        this.loaderQueue = new ArrayList<>();
        this.stats = stats;
    }

    Optional<CompletableFuture<V>> getIfPresent(K key) {
        synchronized (dataLoader) {
            Object cacheKey = getCacheKey(nonNull(key));
            boolean cachingEnabled = loaderOptions.cachingEnabled();
            if (cachingEnabled) {
                if (futureCache.containsKey(cacheKey)) {
                    stats.incrementCacheHitCount();
                    return Optional.of(futureCache.get(cacheKey));
                }
            }
        }
        return Optional.empty();
    }

    Optional<CompletableFuture<V>> getIfCompleted(K key) {
        synchronized (dataLoader) {
            Optional<CompletableFuture<V>> cachedPromise = getIfPresent(key);
            if (cachedPromise.isPresent()) {
                CompletableFuture<V> promise = cachedPromise.get();
                if (promise.isDone()) {
                    return cachedPromise;
                }
            }
        }
        return Optional.empty();
    }


    CompletableFuture<V> load(K key, Object loadContext) {
        synchronized (dataLoader) {
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
                loaderQueue.add(new LoaderQueueEntry<>(key, future, loadContext));
            } else {
                stats.incrementBatchLoadCountBy(1);
                // immediate execution of batch function
                future = invokeLoaderImmediately(key, loadContext);
            }
            if (cachingEnabled) {
                futureCache.set(cacheKey, future);
            }
            return future;
        }
    }

    @SuppressWarnings("unchecked")
    Object getCacheKey(K key) {
        return loaderOptions.cacheKeyFunction().isPresent() ?
                loaderOptions.cacheKeyFunction().get().getKey(key) : key;
    }

    DispatchResult<V> dispatch() {
        boolean batchingEnabled = loaderOptions.batchingEnabled();
        //
        // we copy the pre-loaded set of futures ready for dispatch
        final List<K> keys = new ArrayList<>();
        final List<Object> callContexts = new ArrayList<>();
        final List<CompletableFuture<V>> queuedFutures = new ArrayList<>();
        synchronized (dataLoader) {
            loaderQueue.forEach(entry -> {
                keys.add(entry.getKey());
                queuedFutures.add(entry.getValue());
                callContexts.add(entry.getCallContext());
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
                        Object value = values.get(idx);
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
                            V val = (V) value;
                            future.complete(val);
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
                        dataLoader.clear(key);
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
            keys.forEach(dataLoader::clear);
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

    int dispatchDepth() {
        synchronized (dataLoader) {
            return loaderQueue.size();
        }
    }
}
