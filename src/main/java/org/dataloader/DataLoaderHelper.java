package org.dataloader;

import org.dataloader.annotations.GuardedBy;
import org.dataloader.annotations.Internal;
import org.dataloader.impl.CompletableFutureKit;
import org.dataloader.scheduler.BatchLoaderScheduler;
import org.dataloader.stats.StatisticsCollector;
import org.dataloader.stats.context.IncrementBatchLoadCountByStatisticsContext;
import org.dataloader.stats.context.IncrementBatchLoadExceptionCountStatisticsContext;
import org.dataloader.stats.context.IncrementCacheHitCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadErrorCountStatisticsContext;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
 * This helps break up the large DataLoader class functionality, and it contains the logic to dispatch the
 * promises on behalf of its peer dataloader.
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
                try {
                    CompletableFuture<V> cacheValue = futureCache.get(cacheKey);
                    if (cacheValue != null) {
                        stats.incrementCacheHitCount(new IncrementCacheHitCountStatisticsContext<>(key));
                        return Optional.of(cacheValue);
                    }
                } catch (Exception ignored) {
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

    Object getCacheKey(K key) {
        return loaderOptions.cacheKeyFunction().isPresent() ?
                loaderOptions.cacheKeyFunction().get().getKey(key) : key;
    }

    Object getCacheKeyWithContext(K key, Object context) {
        return loaderOptions.cacheKeyFunction().isPresent() ?
                loaderOptions.cacheKeyFunction().get().getKeyWithContext(key, context) : key;
    }

    DispatchResult<V> dispatch() {
        boolean batchingEnabled = loaderOptions.batchingEnabled();
        final List<K> keys;
        final List<Object> callContexts;
        final List<CompletableFuture<V>> queuedFutures;
        synchronized (dataLoader) {
            int queueSize = loaderQueue.size();
            if (queueSize == 0) {
                lastDispatchTime.set(now());
                return emptyDispatchResult();
            }

            // we copy the pre-loaded set of futures ready for dispatch
            keys = new ArrayList<>(queueSize);
            callContexts = new ArrayList<>(queueSize);
            queuedFutures = new ArrayList<>(queueSize);

            loaderQueue.forEach(entry -> {
                keys.add(entry.getKey());
                queuedFutures.add(entry.getValue());
                callContexts.add(entry.getCallContext());
            });
            loaderQueue.clear();
            lastDispatchTime.set(now());
        }
        if (!batchingEnabled) {
            return emptyDispatchResult();
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
        int len = keys.size();
        int batchCount = (int) Math.ceil(len / (double) maxBatchSize);
        List<CompletableFuture<List<V>>> allBatches = new ArrayList<>(batchCount);
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
        CompletableFuture<List<V>> batchLoad = invokeLoader(keys, callContexts, queuedFutures, loaderOptions.cachingEnabled());
        return batchLoad
                .thenApply(values -> {
                    assertResultSize(keys, values);
                    if (isPublisher() || isMappedPublisher()) {
                        // We have already completed the queued futures by the time the overall batchLoad future has completed.
                        return values;
                    }

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
        // by default, we don't clear the cached view of this entry to avoid
        // frequently loading the same error.  This works for short-lived request caches
        // but might work against long-lived caches. Hence, we have an option that allows
        // it to be cleared
        if (!loaderOptions.cachingExceptionsEnabled()) {
            keys.forEach(dataLoader::clear);
        }
    }

    @GuardedBy("dataLoader")
    private CompletableFuture<V> loadFromCache(K key, Object loadContext, boolean batchingEnabled) {
        final Object cacheKey = loadContext == null ? getCacheKey(key) : getCacheKeyWithContext(key, loadContext);

        try {
            CompletableFuture<V> cacheValue = futureCache.get(cacheKey);
            if (cacheValue != null) {
                // We already have a promise for this key, no need to check value cache or queue up load
                stats.incrementCacheHitCount(new IncrementCacheHitCountStatisticsContext<>(key, loadContext));
                return cacheValue;
            }
        } catch (Exception ignored) {
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
        List<CompletableFuture<V>> queuedFutures = singletonList(new CompletableFuture<>());
        return invokeLoader(keys, keyContexts, queuedFutures, cachingEnabled)
                .thenApply(list -> list.get(0))
                .toCompletableFuture();
    }

    CompletableFuture<List<V>> invokeLoader(List<K> keys, List<Object> keyContexts, List<CompletableFuture<V>> queuedFutures, boolean cachingEnabled) {
        if (!cachingEnabled) {
            return invokeLoader(keys, keyContexts, queuedFutures);
        }
        CompletableFuture<List<Try<V>>> cacheCallCF = getFromValueCache(keys);
        return cacheCallCF.thenCompose(cachedValues -> {

            // the following is NOT a Map because keys in data loader can repeat (by design)
            // and hence "a","b","c","b" is a valid set of keys
            List<Try<V>> valuesInKeyOrder = new ArrayList<>();
            List<Integer> missedKeyIndexes = new ArrayList<>();
            List<K> missedKeys = new ArrayList<>();
            List<Object> missedKeyContexts = new ArrayList<>();
            List<CompletableFuture<V>> missedQueuedFutures = new ArrayList<>();

            // if they return a ValueCachingNotSupported exception then we insert this special marker value, and it
            // means it's a total miss, we need to get all these keys via the batch loader
            if (cachedValues == NOT_SUPPORTED_LIST) {
                for (int i = 0; i < keys.size(); i++) {
                    valuesInKeyOrder.add(ALWAYS_FAILED);
                    missedKeyIndexes.add(i);
                    missedKeys.add(keys.get(i));
                    missedKeyContexts.add(keyContexts.get(i));
                    missedQueuedFutures.add(queuedFutures.get(i));
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
                // we missed some keys from cache, so send them to the batch loader
                // and then fill in their values
                //
                CompletableFuture<List<V>> batchLoad = invokeLoader(missedKeys, missedKeyContexts, missedQueuedFutures);
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

    CompletableFuture<List<V>> invokeLoader(List<K> keys, List<Object> keyContexts, List<CompletableFuture<V>> queuedFutures) {
        CompletableFuture<List<V>> batchLoad;
        try {
            Object context = loaderOptions.getBatchLoaderContextProvider().getContext();
            BatchLoaderEnvironment environment = BatchLoaderEnvironment.newBatchLoaderEnvironment()
                    .context(context).keyContexts(keys, keyContexts).build();
            if (isMapLoader()) {
                batchLoad = invokeMapBatchLoader(keys, environment);
            } else if (isPublisher()) {
                batchLoad = invokeBatchPublisher(keys, keyContexts, queuedFutures, environment);
            } else if (isMappedPublisher()) {
                batchLoad = invokeMappedBatchPublisher(keys, keyContexts, queuedFutures, environment);
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
        BatchLoaderScheduler batchLoaderScheduler = loaderOptions.getBatchLoaderScheduler();
        if (batchLoadFunction instanceof BatchLoaderWithContext) {
            BatchLoaderWithContext<K, V> loadFunction = (BatchLoaderWithContext<K, V>) batchLoadFunction;
            if (batchLoaderScheduler != null) {
                BatchLoaderScheduler.ScheduledBatchLoaderCall<V> loadCall = () -> loadFunction.load(keys, environment);
                loadResult = batchLoaderScheduler.scheduleBatchLoader(loadCall, keys, environment);
            } else {
                loadResult = loadFunction.load(keys, environment);
            }
        } else {
            BatchLoader<K, V> loadFunction = (BatchLoader<K, V>) batchLoadFunction;
            if (batchLoaderScheduler != null) {
                BatchLoaderScheduler.ScheduledBatchLoaderCall<V> loadCall = () -> loadFunction.load(keys);
                loadResult = batchLoaderScheduler.scheduleBatchLoader(loadCall, keys, null);
            } else {
                loadResult = loadFunction.load(keys);
            }
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
        BatchLoaderScheduler batchLoaderScheduler = loaderOptions.getBatchLoaderScheduler();
        if (batchLoadFunction instanceof MappedBatchLoaderWithContext) {
            MappedBatchLoaderWithContext<K, V> loadFunction = (MappedBatchLoaderWithContext<K, V>) batchLoadFunction;
            if (batchLoaderScheduler != null) {
                BatchLoaderScheduler.ScheduledMappedBatchLoaderCall<K, V> loadCall = () -> loadFunction.load(setOfKeys, environment);
                loadResult = batchLoaderScheduler.scheduleMappedBatchLoader(loadCall, keys, environment);
            } else {
                loadResult = loadFunction.load(setOfKeys, environment);
            }
        } else {
            MappedBatchLoader<K, V> loadFunction = (MappedBatchLoader<K, V>) batchLoadFunction;
            if (batchLoaderScheduler != null) {
                BatchLoaderScheduler.ScheduledMappedBatchLoaderCall<K, V> loadCall = () -> loadFunction.load(setOfKeys);
                loadResult = batchLoaderScheduler.scheduleMappedBatchLoader(loadCall, keys, null);
            } else {
                loadResult = loadFunction.load(setOfKeys);
            }
        }
        CompletableFuture<Map<K, V>> mapBatchLoad = nonNull(loadResult, () -> "Your batch loader function MUST return a non null CompletionStage").toCompletableFuture();
        return mapBatchLoad.thenApply(map -> {
            List<V> values = new ArrayList<>(keys.size());
            for (K key : keys) {
                V value = map.get(key);
                values.add(value);
            }
            return values;
        });
    }

    private CompletableFuture<List<V>> invokeBatchPublisher(List<K> keys, List<Object> keyContexts, List<CompletableFuture<V>> queuedFutures, BatchLoaderEnvironment environment) {
        CompletableFuture<List<V>> loadResult = new CompletableFuture<>();
        Subscriber<V> subscriber = new DataLoaderSubscriber(loadResult, keys, keyContexts, queuedFutures);

        BatchLoaderScheduler batchLoaderScheduler = loaderOptions.getBatchLoaderScheduler();
        if (batchLoadFunction instanceof BatchPublisherWithContext) {
            BatchPublisherWithContext<K, V> loadFunction = (BatchPublisherWithContext<K, V>) batchLoadFunction;
            if (batchLoaderScheduler != null) {
                BatchLoaderScheduler.ScheduledBatchPublisherCall loadCall = () -> loadFunction.load(keys, subscriber, environment);
                batchLoaderScheduler.scheduleBatchPublisher(loadCall, keys, environment);
            } else {
                loadFunction.load(keys, subscriber, environment);
            }
        } else {
            BatchPublisher<K, V> loadFunction = (BatchPublisher<K, V>) batchLoadFunction;
            if (batchLoaderScheduler != null) {
                BatchLoaderScheduler.ScheduledBatchPublisherCall loadCall = () -> loadFunction.load(keys, subscriber);
                batchLoaderScheduler.scheduleBatchPublisher(loadCall, keys, null);
            } else {
                loadFunction.load(keys, subscriber);
            }
        }
        return loadResult;
    }

    private CompletableFuture<List<V>> invokeMappedBatchPublisher(List<K> keys, List<Object> keyContexts, List<CompletableFuture<V>> queuedFutures, BatchLoaderEnvironment environment) {
        CompletableFuture<List<V>> loadResult = new CompletableFuture<>();
        Subscriber<Map.Entry<K, V>> subscriber = new DataLoaderMapEntrySubscriber(loadResult, keys, keyContexts, queuedFutures);

        BatchLoaderScheduler batchLoaderScheduler = loaderOptions.getBatchLoaderScheduler();
        if (batchLoadFunction instanceof MappedBatchPublisherWithContext) {
            MappedBatchPublisherWithContext<K, V> loadFunction = (MappedBatchPublisherWithContext<K, V>) batchLoadFunction;
            if (batchLoaderScheduler != null) {
                BatchLoaderScheduler.ScheduledBatchPublisherCall loadCall = () -> loadFunction.load(keys, subscriber, environment);
                batchLoaderScheduler.scheduleBatchPublisher(loadCall, keys, environment);
            } else {
                loadFunction.load(keys, subscriber, environment);
            }
        } else {
            MappedBatchPublisher<K, V> loadFunction = (MappedBatchPublisher<K, V>) batchLoadFunction;
            if (batchLoaderScheduler != null) {
                BatchLoaderScheduler.ScheduledBatchPublisherCall loadCall = () -> loadFunction.load(keys, subscriber);
                batchLoaderScheduler.scheduleBatchPublisher(loadCall, keys, null);
            } else {
                loadFunction.load(keys, subscriber);
            }
        }
        return loadResult;
    }

    private boolean isMapLoader() {
        return batchLoadFunction instanceof MappedBatchLoader || batchLoadFunction instanceof MappedBatchLoaderWithContext;
    }

    private boolean isPublisher() {
        return batchLoadFunction instanceof BatchPublisher;
    }

    private boolean isMappedPublisher() {
        return batchLoadFunction instanceof MappedBatchPublisher;
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

    private static final DispatchResult<?> EMPTY_DISPATCH_RESULT = new DispatchResult<>(completedFuture(emptyList()), 0);

    @SuppressWarnings("unchecked") // Casting to any type is safe since the underlying list is empty
    private static <T> DispatchResult<T> emptyDispatchResult() {
        return (DispatchResult<T>) EMPTY_DISPATCH_RESULT;
    }

    private abstract class DataLoaderSubscriberBase<T> implements Subscriber<T> {

        final CompletableFuture<List<V>> valuesFuture;
        final List<K> keys;
        final List<Object> callContexts;
        final List<CompletableFuture<V>> queuedFutures;

        List<K> clearCacheKeys = new ArrayList<>();
        List<V> completedValues = new ArrayList<>();
        boolean onErrorCalled = false;
        boolean onCompleteCalled = false;

        DataLoaderSubscriberBase(
                CompletableFuture<List<V>> valuesFuture,
                List<K> keys,
                List<Object> callContexts,
                List<CompletableFuture<V>> queuedFutures
        ) {
            this.valuesFuture = valuesFuture;
            this.keys = keys;
            this.callContexts = callContexts;
            this.queuedFutures = queuedFutures;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(keys.size());
        }

        @Override
        public void onNext(T v) {
            assertState(!onErrorCalled, () -> "onError has already been called; onNext may not be invoked.");
            assertState(!onCompleteCalled, () -> "onComplete has already been called; onNext may not be invoked.");
        }

        @Override
        public void onComplete() {
            assertState(!onErrorCalled, () -> "onError has already been called; onComplete may not be invoked.");
            onCompleteCalled = true;
        }

        @Override
        public void onError(Throwable throwable) {
            assertState(!onCompleteCalled, () -> "onComplete has already been called; onError may not be invoked.");
            onErrorCalled = true;

            stats.incrementBatchLoadExceptionCount(new IncrementBatchLoadExceptionCountStatisticsContext<>(keys, callContexts));
        }

        /*
         * A value has arrived - how do we complete the future that's associated with it in a common way
         */
        void onNextValue(K key, V value, Object callContext, CompletableFuture<V> future) {
            if (value instanceof Try) {
                // we allow the batch loader to return a Try so we can better represent a computation
                // that might have worked or not.
                //noinspection unchecked
                Try<V> tryValue = (Try<V>) value;
                if (tryValue.isSuccess()) {
                    future.complete(tryValue.get());
                } else {
                    stats.incrementLoadErrorCount(new IncrementLoadErrorCountStatisticsContext<>(key, callContext));
                    future.completeExceptionally(tryValue.getThrowable());
                    clearCacheKeys.add(key);
                }
            } else {
                future.complete(value);
            }
        }

        Throwable unwrapThrowable(Throwable ex) {
            if (ex instanceof CompletionException) {
                ex = ex.getCause();
            }
            return ex;
        }
    }

    private class DataLoaderSubscriber extends DataLoaderSubscriberBase<V> {

        private int idx = 0;

        private DataLoaderSubscriber(
                CompletableFuture<List<V>> valuesFuture,
                List<K> keys,
                List<Object> callContexts,
                List<CompletableFuture<V>> queuedFutures
        ) {
            super(valuesFuture, keys, callContexts, queuedFutures);
        }

        // onNext may be called by multiple threads - for the time being, we pass 'synchronized' to guarantee
        // correctness (at the cost of speed).
        @Override
        public synchronized void onNext(V value) {
            super.onNext(value);

            K key = keys.get(idx);
            Object callContext = callContexts.get(idx);
            CompletableFuture<V> future = queuedFutures.get(idx);
            onNextValue(key, value, callContext, future);

            completedValues.add(value);
            idx++;
        }


        @Override
        public synchronized void onComplete() {
            super.onComplete();
            assertResultSize(keys, completedValues);

            possiblyClearCacheEntriesOnExceptions(clearCacheKeys);
            valuesFuture.complete(completedValues);
        }

        @Override
        public synchronized void onError(Throwable ex) {
            super.onError(ex);
            ex = unwrapThrowable(ex);
            // Set the remaining keys to the exception.
            for (int i = idx; i < queuedFutures.size(); i++) {
                K key = keys.get(i);
                CompletableFuture<V> future = queuedFutures.get(i);
                future.completeExceptionally(ex);
                // clear any cached view of this key because they all failed
                dataLoader.clear(key);
            }
            valuesFuture.completeExceptionally(ex);
        }

    }

    private class DataLoaderMapEntrySubscriber extends DataLoaderSubscriberBase<Map.Entry<K, V>> {

        private final Map<K, Object> callContextByKey;
        private final Map<K, CompletableFuture<V>> queuedFutureByKey;
        private final Map<K, V> completedValuesByKey = new HashMap<>();


        private DataLoaderMapEntrySubscriber(
                CompletableFuture<List<V>> valuesFuture,
                List<K> keys,
                List<Object> callContexts,
                List<CompletableFuture<V>> queuedFutures
        ) {
            super(valuesFuture, keys, callContexts, queuedFutures);
            this.callContextByKey = new HashMap<>();
            this.queuedFutureByKey = new HashMap<>();
            for (int idx = 0; idx < queuedFutures.size(); idx++) {
                K key = keys.get(idx);
                Object callContext = callContexts.get(idx);
                CompletableFuture<V> queuedFuture = queuedFutures.get(idx);
                callContextByKey.put(key, callContext);
                queuedFutureByKey.put(key, queuedFuture);
            }
        }


        @Override
        public synchronized void onNext(Map.Entry<K, V> entry) {
            super.onNext(entry);
            K key = entry.getKey();
            V value = entry.getValue();

            Object callContext = callContextByKey.get(key);
            CompletableFuture<V> future = queuedFutureByKey.get(key);

            onNextValue(key, value, callContext, future);

            completedValuesByKey.put(key, value);
        }

        @Override
        public synchronized void onComplete() {
            super.onComplete();

            possiblyClearCacheEntriesOnExceptions(clearCacheKeys);
            List<V> values = new ArrayList<>(keys.size());
            for (K key : keys) {
                V value = completedValuesByKey.get(key);
                values.add(value);
            }
            valuesFuture.complete(values);
        }

        @Override
        public synchronized void onError(Throwable ex) {
            super.onError(ex);
            ex = unwrapThrowable(ex);
            // Complete the futures for the remaining keys with the exception.
            for (int idx = 0; idx < queuedFutures.size(); idx++) {
                K key = keys.get(idx);
                CompletableFuture<V> future = queuedFutureByKey.get(key);
                if (!completedValuesByKey.containsKey(key)) {
                    future.completeExceptionally(ex);
                    // clear any cached view of this key because they all failed
                    dataLoader.clear(key);
                }
            }
            valuesFuture.completeExceptionally(ex);
        }

    }
}
