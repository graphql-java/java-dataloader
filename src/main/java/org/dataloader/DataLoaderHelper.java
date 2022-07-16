package org.dataloader;

import org.dataloader.annotations.GuardedBy;
import org.dataloader.annotations.Internal;
import org.dataloader.impl.CompletableFutureKit;
import org.dataloader.stats.StatisticsCollector;
import org.dataloader.stats.context.IncrementBatchLoadCountByStatisticsContext;
import org.dataloader.stats.context.IncrementBatchLoadExceptionCountStatisticsContext;
import org.dataloader.stats.context.IncrementCacheHitCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadErrorCountStatisticsContext;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
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
    private final CacheMap<Object, V> futureCache;
    private final ValueCache<K, V> valueCache;
    private final List<LoaderQueueEntry<K, CompletableFuture<V>>> loaderQueue;
    private final StatisticsCollector stats;
    private final Clock clock;
    private final AtomicReference<Instant> lastDispatchTime;

    DataLoaderHelper(DataLoader<K, V> dataLoader,
                     Object batchLoadFunction,
                     DataLoaderOptions loaderOptions,
                     CacheMap<Object, V> futureCache,
                     ValueCache<K, V> valueCache,
                     StatisticsCollector stats,
                     Clock clock) {
        this.dataLoader = dataLoader;
        this.batchLoadFunction = batchLoadFunction;
        this.loaderOptions = loaderOptions;
        this.futureCache = futureCache;
        this.valueCache = valueCache;
        this.loaderQueue = new ArrayList<>();
        this.stats = stats;
        this.clock = clock;
        this.lastDispatchTime = new AtomicReference<>();
        this.lastDispatchTime.set(now());
    }

    Instant now() {
        return clock.instant();
    }

    public Instant getLastDispatchTime() {
        return lastDispatchTime.get();
    }

    Optional<CompletableFuture<V>> getIfPresent(K key) {
        synchronized (dataLoader) {
            boolean cachingEnabled = loaderOptions.cachingEnabled();
            if (cachingEnabled) {
                Object cacheKey = getCacheKey(nonNull(key));
                if (futureCache.containsKey(cacheKey)) {
                    stats.incrementCacheHitCount(new IncrementCacheHitCountStatisticsContext<>(key));
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
            boolean batchingEnabled = loaderOptions.batchingEnabled();
            boolean cachingEnabled = loaderOptions.cachingEnabled();

            stats.incrementLoadCount(new IncrementLoadCountStatisticsContext<>(key, loadContext));

            if (cachingEnabled) {
                return loadFromCache(key, loadContext, batchingEnabled);
            } else {
                return queueOrInvokeLoader(key, loadContext, batchingEnabled, false);
            }
        }
    }

    @SuppressWarnings("unchecked")
    Object getCacheKey(K key) {
        return loaderOptions.cacheKeyFunction().isPresent() ?
                loaderOptions.cacheKeyFunction().get().getKey(key) : key;
    }

    @SuppressWarnings("unchecked")
    Object getCacheKeyWithContext(K key, Object context) {
        return loaderOptions.cacheKeyFunction().isPresent() ?
                loaderOptions.cacheKeyFunction().get().getKeyWithContext(key, context) : key;
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
            lastDispatchTime.set(now());
        }
        if (!batchingEnabled || keys.isEmpty()) {
            return new DispatchResult<>(completedFuture(emptyList()), 0);
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
        return allOf(allBatches.toArray(new CompletableFuture[0]))
                .thenApply(v -> allBatches.stream()
                        .map(CompletableFuture::join)
                        .flatMap(Collection::stream)
                        .collect(toList()));
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<List<V>> dispatchQueueBatch(List<K> keys, List<Object> callContexts, List<CompletableFuture<V>> queuedFutures) {
        stats.incrementBatchLoadCountBy(keys.size(), new IncrementBatchLoadCountByStatisticsContext<>(keys, callContexts));
        CompletableFuture<List<V>> batchLoad = invokeLoader(keys, callContexts, loaderOptions.cachingEnabled());
        return batchLoad
                .thenApply(values -> {
                    assertResultSize(keys, values);

                    List<K> clearCacheKeys = new ArrayList<>();
                    for (int idx = 0; idx < queuedFutures.size(); idx++) {
                        K key = keys.get(idx);
                        V value = values.get(idx);
                        Object callContext = callContexts.get(idx);
                        CompletableFuture<V> future = queuedFutures.get(idx);
                        if (value instanceof Throwable) {
                            stats.incrementLoadErrorCount(new IncrementLoadErrorCountStatisticsContext<>(key, callContext));
                            future.completeExceptionally((Throwable) value);
                            clearCacheKeys.add(keys.get(idx));
                        } else if (value instanceof Try) {
                            // we allow the batch loader to return a Try so we can better represent a computation
                            // that might have worked or not.
                            Try<V> tryValue = (Try<V>) value;
                            if (tryValue.isSuccess()) {
                                future.complete(tryValue.get());
                            } else {
                                stats.incrementLoadErrorCount(new IncrementLoadErrorCountStatisticsContext<>(key, callContext));
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
                    stats.incrementBatchLoadExceptionCount(new IncrementBatchLoadExceptionCountStatisticsContext<>(keys, callContexts));
                    if (ex instanceof CompletionException) {
                        ex = ex.getCause();
                    }
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
        assertState(keys.size() == values.size(), () -> "The size of the promised values MUST be the same size as the key list");
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

    @GuardedBy("dataLoader")
    private CompletableFuture<V> loadFromCache(K key, Object loadContext, boolean batchingEnabled) {
        final Object cacheKey = loadContext == null ? getCacheKey(key) : getCacheKeyWithContext(key, loadContext);

        if (futureCache.containsKey(cacheKey)) {
            // We already have a promise for this key, no need to check value cache or queue up load
            stats.incrementCacheHitCount(new IncrementCacheHitCountStatisticsContext<>(key, loadContext));
            return futureCache.get(cacheKey);
        }

        CompletableFuture<V> loadCallFuture = queueOrInvokeLoader(key, loadContext, batchingEnabled, true);
        futureCache.set(cacheKey, loadCallFuture);
        return loadCallFuture;
    }

    @GuardedBy("dataLoader")
    private CompletableFuture<V> queueOrInvokeLoader(K key, Object loadContext, boolean batchingEnabled, boolean cachingEnabled) {
        if (batchingEnabled) {
            CompletableFuture<V> loadCallFuture = new CompletableFuture<>();
            loaderQueue.add(new LoaderQueueEntry<>(key, loadCallFuture, loadContext));
            return loadCallFuture;
        } else {
            stats.incrementBatchLoadCountBy(1, new IncrementBatchLoadCountByStatisticsContext<>(key, loadContext));
            // immediate execution of batch function
            return invokeLoaderImmediately(key, loadContext, cachingEnabled);
        }
    }

    CompletableFuture<V> invokeLoaderImmediately(K key, Object keyContext, boolean cachingEnabled) {
        List<K> keys = singletonList(key);
        List<Object> keyContexts = singletonList(keyContext);
        return invokeLoader(keys, keyContexts, cachingEnabled)
                .thenApply(list -> list.get(0))
                .toCompletableFuture();
    }

    CompletableFuture<List<V>> invokeLoader(List<K> keys, List<Object> keyContexts, boolean cachingEnabled) {
        if (!cachingEnabled) {
            return invokeLoader(keys, keyContexts);
        }
        CompletableFuture<List<Try<V>>> cacheCallCF = getFromValueCache(keys);
        return cacheCallCF.thenCompose(cachedValues -> {

            // the following is NOT a Map because keys in data loader can repeat (by design)
            // and hence "a","b","c","b" is a valid set of keys
            List<Try<V>> valuesInKeyOrder = new ArrayList<>();
            List<Integer> missedKeyIndexes = new ArrayList<>();
            List<K> missedKeys = new ArrayList<>();
            List<Object> missedKeyContexts = new ArrayList<>();

            // if they return a ValueCachingNotSupported exception then we insert this special marker value, and it
            // means it's a total miss, we need to get all these keys via the batch loader
            if (cachedValues == NOT_SUPPORTED_LIST) {
                for (int i = 0; i < keys.size(); i++) {
                    valuesInKeyOrder.add(ALWAYS_FAILED);
                    missedKeyIndexes.add(i);
                    missedKeys.add(keys.get(i));
                    missedKeyContexts.add(keyContexts.get(i));
                }
            } else {
                assertState(keys.size() == cachedValues.size(), () -> "The size of the cached values MUST be the same size as the key list");
                for (int i = 0; i < keys.size(); i++) {
                    Try<V> cacheGet = cachedValues.get(i);
                    valuesInKeyOrder.add(cacheGet);
                    if (cacheGet.isFailure()) {
                        missedKeyIndexes.add(i);
                        missedKeys.add(keys.get(i));
                        missedKeyContexts.add(keyContexts.get(i));
                    }
                }
            }
            if (missedKeys.isEmpty()) {
                //
                // everything was cached
                //
                List<V> assembledValues = valuesInKeyOrder.stream().map(Try::get).collect(toList());
                return completedFuture(assembledValues);
            } else {
                //
                // we missed some of the keys from cache, so send them to the batch loader
                // and then fill in their values
                //
                CompletableFuture<List<V>> batchLoad = invokeLoader(missedKeys, missedKeyContexts);
                return batchLoad.thenCompose(missedValues -> {
                    assertResultSize(missedKeys, missedValues);

                    for (int i = 0; i < missedValues.size(); i++) {
                        V v = missedValues.get(i);
                        Integer listIndex = missedKeyIndexes.get(i);
                        valuesInKeyOrder.set(listIndex, Try.succeeded(v));
                    }
                    List<V> assembledValues = valuesInKeyOrder.stream().map(Try::get).collect(toList());
                    //
                    // fire off a call to the ValueCache to allow it to set values into the
                    // cache now that we have them
                    return setToValueCache(assembledValues, missedKeys, missedValues);
                });
            }
        });
    }


    CompletableFuture<List<V>> invokeLoader(List<K> keys, List<Object> keyContexts) {
        CompletableFuture<List<V>> batchLoad;
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
    private CompletableFuture<List<V>> invokeListBatchLoader(List<K> keys, BatchLoaderEnvironment environment) {
        CompletionStage<List<V>> loadResult;
        if (batchLoadFunction instanceof BatchLoaderWithContext) {
            loadResult = ((BatchLoaderWithContext<K, V>) batchLoadFunction).load(keys, environment);
        } else {
            loadResult = ((BatchLoader<K, V>) batchLoadFunction).load(keys);
        }
        return nonNull(loadResult, () -> "Your batch loader function MUST return a non null CompletionStage").toCompletableFuture();
    }


    /*
     * Turns a map of results that MAY be smaller than the key list back into a list by mapping null
     * to missing elements.
     */
    @SuppressWarnings("unchecked")
    private CompletableFuture<List<V>> invokeMapBatchLoader(List<K> keys, BatchLoaderEnvironment environment) {
        CompletionStage<Map<K, V>> loadResult;
        Set<K> setOfKeys = new LinkedHashSet<>(keys);
        if (batchLoadFunction instanceof MappedBatchLoaderWithContext) {
            loadResult = ((MappedBatchLoaderWithContext<K, V>) batchLoadFunction).load(setOfKeys, environment);
        } else {
            loadResult = ((MappedBatchLoader<K, V>) batchLoadFunction).load(setOfKeys);
        }
        CompletableFuture<Map<K, V>> mapBatchLoad = nonNull(loadResult, () -> "Your batch loader function MUST return a non null CompletionStage").toCompletableFuture();
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

    private final List<Try<V>> NOT_SUPPORTED_LIST = emptyList();
    private final CompletableFuture<List<Try<V>>> NOT_SUPPORTED = CompletableFuture.completedFuture(NOT_SUPPORTED_LIST);
    private final Try<V> ALWAYS_FAILED = Try.alwaysFailed();

    private CompletableFuture<List<Try<V>>> getFromValueCache(List<K> keys) {
        try {
            return nonNull(valueCache.getValues(keys), () -> "Your ValueCache.getValues function MUST return a non null CompletableFuture");
        } catch (ValueCache.ValueCachingNotSupported ignored) {
            // use of a final field prevents CF object allocation for this special purpose
            return NOT_SUPPORTED;
        } catch (RuntimeException e) {
            return CompletableFutureKit.failedFuture(e);
        }
    }

    private CompletableFuture<List<V>> setToValueCache(List<V> assembledValues, List<K> missedKeys, List<V> missedValues) {
        try {
            boolean completeValueAfterCacheSet = loaderOptions.getValueCacheOptions().isCompleteValueAfterCacheSet();
            if (completeValueAfterCacheSet) {
                return nonNull(valueCache
                        .setValues(missedKeys, missedValues), () -> "Your ValueCache.setValues function MUST return a non null CompletableFuture")
                        // we don't trust the set cache to give us the values back - we have them - lets use them
                        // if the cache set fails - then they won't be in cache and maybe next time they will
                        .handle((ignored, setExIgnored) -> assembledValues);
            } else {
                // no one is waiting for the set to happen here so if its truly async
                // it will happen eventually but no result will be dependent on it
                valueCache.setValues(missedKeys, missedValues);
            }
        } catch (ValueCache.ValueCachingNotSupported ignored) {
            // ok no set caching is fine if they say so
        } catch (RuntimeException ignored) {
            // if we can't set values back into the cache - so be it - this must be a faulty
            // ValueCache implementation
        }
        return CompletableFuture.completedFuture(assembledValues);
    }
}
