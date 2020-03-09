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

import org.dataloader.CacheMap;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DispatchResult;
import org.dataloader.Internal;
import org.dataloader.stats.Statistics;
import org.dataloader.stats.StatisticsCollector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.dataloader.impl.Assertions.nonNull;

@Internal
public class DataLoaderImpl<K, V> implements DataLoader<K, V> {

    private final DataLoaderHelper<K, V> helper;
    private final DataLoaderOptions loaderOptions;
    private final CacheMap<Object, CompletableFuture<V>> futureCache;
    private final StatisticsCollector stats;


    @Internal
    public DataLoaderImpl(Object batchLoadFunction, DataLoaderOptions options) {
        this.loaderOptions = options == null ? new DataLoaderOptions() : options;
        this.futureCache = determineCacheMap(loaderOptions);
        // order of keys matter in data loader
        this.stats = nonNull(this.loaderOptions.getStatisticsCollector());

        this.helper = new DataLoaderHelper<>(this, batchLoadFunction, this.loaderOptions, this.futureCache, this.stats);
    }

    @SuppressWarnings("unchecked")
    private CacheMap<Object, CompletableFuture<V>> determineCacheMap(DataLoaderOptions loaderOptions) {
        return loaderOptions.cacheMap().isPresent() ? (CacheMap<Object, CompletableFuture<V>>) loaderOptions.cacheMap().get() : CacheMap.simpleMap();
    }

    /**
     * Requests to load the data with the specified key asynchronously, and returns a future of the resulting value.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link org.dataloader.DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     *
     * @param key the key to load
     * @return the future of the value
     */
    @Override
    public CompletableFuture<V> load(K key) {
        return load(key, null);
    }

    /**
     * This will return an optional promise to a value previously loaded via a {@link #load(Object)} call or empty if not call has been made for that key.
     * <p>
     * If you do get a present CompletableFuture it does not mean it has been dispatched and completed yet.  It just means
     * its at least pending and in cache.
     * <p>
     * If caching is disabled there will never be a present Optional returned.
     * <p>
     * NOTE : This will NOT cause a data load to happen.  You must called {@link #load(Object)} for that to happen.
     *
     * @param key the key to check
     * @return an Optional to the future of the value
     */
    @Override
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
     * NOTE : This will NOT cause a data load to happen.  You must called {@link #load(Object)} for that to happen.
     *
     * @param key the key to check
     * @return an Optional to the future of the value
     */
    @Override
    public Optional<CompletableFuture<V>> getIfCompleted(K key) {
        return helper.getIfCompleted(key);
    }


    /**
     * Requests to load the data with the specified key asynchronously, and returns a future of the resulting value.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link org.dataloader.DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     * <p>
     * The key context object may be useful in the batch loader interfaces such as {@link org.dataloader.BatchLoaderWithContext} or
     * {@link org.dataloader.MappedBatchLoaderWithContext} to help retrieve data.
     *
     * @param key        the key to load
     * @param keyContext a context object that is specific to this key
     * @return the future of the value
     */
    @Override
    public CompletableFuture<V> load(K key, Object keyContext) {
        return helper.load(key, keyContext);
    }

    /**
     * Requests to load the list of data provided by the specified keys asynchronously, and returns a composite future
     * of the resulting values.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link org.dataloader.DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     *
     * @param keys the list of keys to load
     * @return the composite future of the list of values
     */
    @Override
    public CompletableFuture<List<V>> loadMany(List<K> keys) {
        return loadMany(keys, Collections.emptyList());
    }

    /**
     * Requests to load the list of data provided by the specified keys asynchronously, and returns a composite future
     * of the resulting values.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link org.dataloader.DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     * <p>
     * The key context object may be useful in the batch loader interfaces such as {@link org.dataloader.BatchLoaderWithContext} or
     * {@link org.dataloader.MappedBatchLoaderWithContext} to help retrieve data.
     *
     * @param keys        the list of keys to load
     * @param keyContexts the list of key calling context objects
     * @return the composite future of the list of values
     */
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

    /**
     * Dispatches the queued load requests to the batch execution function and returns a promise of the result.
     * <p>
     * If batching is disabled, or there are no queued requests, then a succeeded promise is returned.
     *
     * @return the promise of the queued load requests
     */
    @Override
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
    @Override
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


    /**
     * @return the depth of the batched key loads that need to be dispatched
     */
    @Override
    public int dispatchDepth() {
        return helper.dispatchDepth();
    }


    /**
     * Clears the future with the specified key from the cache, if caching is enabled, so it will be re-fetched
     * on the next load request.
     *
     * @param key the key to remove
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
    @Override
    public Statistics getStatistics() {
        return stats.getStatistics();
    }

}
