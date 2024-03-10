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

import org.dataloader.annotations.PublicApi;
import org.dataloader.annotations.VisibleForTesting;
import org.dataloader.impl.CompletableFutureKit;
import org.dataloader.stats.Statistics;
import org.dataloader.stats.StatisticsCollector;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

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
@PublicApi
public class DataLoader<K, V> {

    private final DataLoaderHelper<K, V> helper;
    private final StatisticsCollector stats;
    private final CacheMap<Object, V> futureCache;
    private final ValueCache<K, V> valueCache;

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
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
     *
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoader<K, V> batchLoadFunction, DataLoaderOptions options) {
        return DataLoaderFactory.mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If it's important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
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
     * @see DataLoaderFactory#newDataLoaderWithTry(BatchLoader)
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(BatchLoader<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return DataLoaderFactory.mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoaderWithContext<K, V> batchLoadFunction) {
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
     *
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public static <K, V> DataLoader<K, V> newDataLoader(BatchLoaderWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return DataLoaderFactory.mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If it's important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(BatchLoaderWithContext<K, Try<V>> batchLoadFunction) {
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
     * @see DataLoaderFactory#newDataLoaderWithTry(BatchLoader)
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public static <K, V> DataLoader<K, V> newDataLoaderWithTry(BatchLoaderWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return DataLoaderFactory.mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public static <K, V> DataLoader<K, V> newMappedDataLoader(MappedBatchLoader<K, V> batchLoadFunction) {
        return newMappedDataLoader(batchLoadFunction, null);
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
     *
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public static <K, V> DataLoader<K, V> newMappedDataLoader(MappedBatchLoader<K, V> batchLoadFunction, DataLoaderOptions options) {
        return DataLoaderFactory.mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If it's important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     * <p>
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(MappedBatchLoader<K, Try<V>> batchLoadFunction) {
        return newMappedDataLoaderWithTry(batchLoadFunction, null);
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
     * @see DataLoaderFactory#newDataLoaderWithTry(BatchLoader)
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(MappedBatchLoader<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return DataLoaderFactory.mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified mapped batch loader function and default options
     * (batching, caching and unlimited batch size).
     *
     * @param batchLoadFunction the batch load function to use
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public static <K, V> DataLoader<K, V> newMappedDataLoader(MappedBatchLoaderWithContext<K, V> batchLoadFunction) {
        return newMappedDataLoader(batchLoadFunction, null);
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
     *
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public static <K, V> DataLoader<K, V> newMappedDataLoader(MappedBatchLoaderWithContext<K, V> batchLoadFunction, DataLoaderOptions options) {
        return DataLoaderFactory.mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates new DataLoader with the specified batch loader function and default options
     * (batching, caching and unlimited batch size) where the batch loader function returns a list of
     * {@link org.dataloader.Try} objects.
     * <p>
     * If it's important you to know the exact status of each item in a batch call and whether it threw exceptions then
     * you can use this form to create the data loader.
     * <p>
     * Using Try objects allows you to capture a value returned or an exception that might
     * have occurred trying to get a value. .
     *
     * @param batchLoadFunction the batch load function to use that uses {@link org.dataloader.Try} objects
     * @param <K>               the key type
     * @param <V>               the value type
     *
     * @return a new DataLoader
     *
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(MappedBatchLoaderWithContext<K, Try<V>> batchLoadFunction) {
        return newMappedDataLoaderWithTry(batchLoadFunction, null);
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
     * @see DataLoaderFactory#newDataLoaderWithTry(BatchLoader)
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public static <K, V> DataLoader<K, V> newMappedDataLoaderWithTry(MappedBatchLoaderWithContext<K, Try<V>> batchLoadFunction, DataLoaderOptions options) {
        return DataLoaderFactory.mkDataLoader(batchLoadFunction, options);
    }

    /**
     * Creates a new data loader with the provided batch load function, and default options.
     *
     * @param batchLoadFunction the batch load function to use
     *
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public DataLoader(BatchLoader<K, V> batchLoadFunction) {
        this((Object) batchLoadFunction, null);
    }

    /**
     * Creates a new data loader with the provided batch load function and options.
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the batch load options
     *
     * @deprecated use {@link DataLoaderFactory} instead
     */
    @Deprecated
    public DataLoader(BatchLoader<K, V> batchLoadFunction, DataLoaderOptions options) {
        this((Object) batchLoadFunction, options);
    }

    @VisibleForTesting
    DataLoader(Object batchLoadFunction, DataLoaderOptions options) {
        this(batchLoadFunction, options, Clock.systemUTC());
    }

    @VisibleForTesting
    DataLoader(Object batchLoadFunction, DataLoaderOptions options, Clock clock) {
        DataLoaderOptions loaderOptions = options == null ? new DataLoaderOptions() : options;
        this.futureCache = determineFutureCache(loaderOptions);
        this.valueCache = determineValueCache(loaderOptions);
        // order of keys matter in data loader
        this.stats = nonNull(loaderOptions.getStatisticsCollector());

        this.helper = new DataLoaderHelper<>(this, batchLoadFunction, loaderOptions, this.futureCache, this.valueCache, this.stats, clock);
    }


    @SuppressWarnings("unchecked")
    private CacheMap<Object, V> determineFutureCache(DataLoaderOptions loaderOptions) {
        return (CacheMap<Object, V>) loaderOptions.cacheMap().orElseGet(CacheMap::simpleMap);
    }

    @SuppressWarnings("unchecked")
    private ValueCache<K, V> determineValueCache(DataLoaderOptions loaderOptions) {
        return (ValueCache<K, V>) loaderOptions.valueCache().orElseGet(ValueCache::defaultValueCache);
    }

    /**
     * This returns the last instant the data loader was dispatched.  When the data loader is created this value is set to now.
     *
     * @return the instant since the last dispatch
     */
    public Instant getLastDispatchTime() {
        return helper.getLastDispatchTime();
    }

    /**
     * This returns the {@link Duration} since the data loader was dispatched.  When the data loader is created this is zero.
     *
     * @return the time duration since the last dispatch
     */
    public Duration getTimeSinceDispatch() {
        return Duration.between(helper.getLastDispatchTime(), helper.now());
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
        return load(key, null);
    }

    /**
     * This will return an optional promise to a value previously loaded via a {@link #load(Object)} call or empty if not call has been made for that key.
     * <p>
     * If you do get a present CompletableFuture it does not mean it has been dispatched and completed yet.  It just means
     * it's at least pending and in cache.
     * <p>
     * If caching is disabled there will never be a present Optional returned.
     * <p>
     * NOTE : This will NOT cause a data load to happen. You must call {@link #load(Object)} for that to happen.
     *
     * @param key the key to check
     *
     * @return an Optional to the future of the value
     */
    public Optional<CompletableFuture<V>> getIfPresent(K key) {
        return helper.getIfPresent(key);
    }

    /**
     * This will return an optional promise to a value previously loaded via a {@link #load(Object)} call that has in fact been completed or empty
     * if no call has been made for that key or the promise has not completed yet.
     * <p>
     * If you do get a present CompletableFuture it means it has been dispatched and completed.  Completed is defined as
     * {@link java.util.concurrent.CompletableFuture#isDone()} returning true.
     * <p>
     * If caching is disabled there will never be a present Optional returned.
     * <p>
     * NOTE : This will NOT cause a data load to happen.  You must call {@link #load(Object)} for that to happen.
     *
     * @param key the key to check
     *
     * @return an Optional to the future of the value
     */
    public Optional<CompletableFuture<V>> getIfCompleted(K key) {
        return helper.getIfCompleted(key);
    }


    /**
     * Requests to load the data with the specified key asynchronously, and returns a future of the resulting value.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     * <p>
     * The key context object may be useful in the batch loader interfaces such as {@link org.dataloader.BatchLoaderWithContext} or
     * {@link org.dataloader.MappedBatchLoaderWithContext} to help retrieve data.
     *
     * @param key        the key to load
     * @param keyContext a context object that is specific to this key
     *
     * @return the future of the value
     */
    public CompletableFuture<V> load(K key, Object keyContext) {
        return helper.load(key, keyContext);
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
        return loadMany(keys, Collections.emptyList());
    }

    /**
     * Requests to load the list of data provided by the specified keys asynchronously, and returns a composite future
     * of the resulting values.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     * <p>
     * The key context object may be useful in the batch loader interfaces such as {@link org.dataloader.BatchLoaderWithContext} or
     * {@link org.dataloader.MappedBatchLoaderWithContext} to help retrieve data.
     *
     * @param keys        the list of keys to load
     * @param keyContexts the list of key calling context objects
     *
     * @return the composite future of the list of values
     */
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

    /**
     * Dispatches the queued load requests to the batch execution function and returns a promise of the result.
     * <p>
     * If batching is disabled, or there are no queued requests, then a succeeded promise is returned.
     *
     * @return the promise of the queued load requests
     */
    public CompletableFuture<List<V>> dispatch() {
        return helper.dispatch().getPromisedResults();
    }

    /**
     * Dispatches the queued load requests to the batch execution function and returns both the promise of the result
     * and the number of entries that were dispatched.
     * <p>
     * If batching is disabled, or there are no queued requests, then a succeeded promise with no entries dispatched is
     * returned.
     *
     * @return the promise of the queued load requests and the number of keys dispatched.
     */
    public DispatchResult<V> dispatchWithCounts() {
        return helper.dispatch();
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
        List<V> joinedResults = dispatch().join();
        List<V> results = new ArrayList<>(joinedResults);
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
        return helper.dispatchDepth();
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
        return clear(key, (v, e) -> {
        });
    }

    /**
     * Clears the future with the specified key from the cache remote value store, if caching is enabled
     * and a remote store is set, so it will be re-fetched and stored on the next load request.
     *
     * @param key     the key to remove
     * @param handler a handler that will be called after the async remote clear completes
     *
     * @return the data loader for fluent coding
     */
    public DataLoader<K, V> clear(K key, BiConsumer<Void, Throwable> handler) {
        Object cacheKey = getCacheKey(key);
        synchronized (this) {
            futureCache.delete(cacheKey);
            valueCache.delete(key).whenComplete(handler);
        }
        return this;
    }

    /**
     * Clears the entire cache map of the loader.
     *
     * @return the data loader for fluent coding
     */
    public DataLoader<K, V> clearAll() {
        return clearAll((v, e) -> {
        });
    }

    /**
     * Clears the entire cache map of the loader, and of the cached value store.
     *
     * @param handler a handler that will be called after the async remote clear all completes
     *
     * @return the data loader for fluent coding
     */
    public DataLoader<K, V> clearAll(BiConsumer<Void, Throwable> handler) {
        synchronized (this) {
            futureCache.clear();
            valueCache.clear().whenComplete(handler);
        }
        return this;
    }

    /**
     * Primes the cache with the given key and value.  Note this will only prime the future cache
     * and not the value store.  Use {@link ValueCache#set(Object, Object)} if you want
     * to prime it with values before use
     *
     * @param key   the key
     * @param value the value
     *
     * @return the data loader for fluent coding
     */
    public DataLoader<K, V> prime(K key, V value) {
        return prime(key, CompletableFuture.completedFuture(value));
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
        return prime(key, CompletableFutureKit.failedFuture(error));
    }

    /**
     * Primes the cache with the given key and value.  Note this will only prime the future cache
     * and not the value store.  Use {@link ValueCache#set(Object, Object)} if you want
     * to prime it with values before use
     *
     * @param key   the key
     * @param value the value
     *
     * @return the data loader for fluent coding
     */
    public DataLoader<K, V> prime(K key, CompletableFuture<V> value) {
        Object cacheKey = getCacheKey(key);
        synchronized (this) {
            if (!futureCache.containsKey(cacheKey)) {
                futureCache.set(cacheKey, value);
            }
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
    public Object getCacheKey(K key) {
        return helper.getCacheKey(key);
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

    /**
     * Gets the cacheMap associated with this data loader passed in via {@link DataLoaderOptions#cacheMap()}
     * @return the cacheMap of this data loader
     */
    public CacheMap<Object, V> getCacheMap() {
        return futureCache;
    }


    /**
     * Gets the valueCache associated with this data loader passed in via {@link DataLoaderOptions#valueCache()}
     * @return the valueCache of this data loader
     */
    public ValueCache<K, V> getValueCache() {
        return valueCache;
    }

}
