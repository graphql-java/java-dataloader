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

package io.engagingspaces.vertx.dataloader;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Data loader is a utility class that allows batch loading of data that is identified by a set of unique keys. For
 * each key that is loaded a separate {@link Future} is returned, that completes as the batch function completes.
 * Besides individual futures a {@link CompositeFuture} of the batch is available as well.
 * <p>
 * With batching enabled the execution will start after calling {@link DataLoader#dispatch()}, causing the queue of
 * loaded keys to be sent to the batch function, clears the queue, and returns the {@link CompositeFuture}.
 * <p>
 * As batch functions are executed the resulting futures are cached using a cache implementation of choice, so they
 * will only execute once. Individual cache keys can be cleared, so they will be re-fetched when referred to again.
 * It is also possible to clear the cache entirely, and prime it with values before they are used.
 * <p>
 * Both caching and batching can be disabled. Configuration of the data loader is done by providing a
 * {@link DataLoaderOptions} instance on creation.
 *
 * @param <K> type parameter indicating the type of the data load keys
 * @param <V> type parameter indicating the type of the data that is returned
 *
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 */
public class DataLoader<K, V> {

    private final BatchLoader<K> batchLoadFunction;
    private final DataLoaderOptions loaderOptions;
    private final CacheMap<Object, Future<V>> futureCache;
    private final LinkedHashMap<K, Future<V>> loaderQueue;

    /**
     * Creates a new data loader with the provided batch load function, and default options.
     *
     * @param batchLoadFunction the batch load function to use
     */
    public DataLoader(BatchLoader<K> batchLoadFunction) {
        this(batchLoadFunction, null);
    }

    /**
     * Creates a new data loader with the provided batch load function and options.
     *
     * @param batchLoadFunction the batch load function to use
     * @param options           the batch load options
     */
    @SuppressWarnings("unchecked")
    public DataLoader(BatchLoader<K> batchLoadFunction, DataLoaderOptions options) {
        Objects.requireNonNull(batchLoadFunction, "Batch load function cannot be null");
        this.batchLoadFunction = batchLoadFunction;
        this.loaderOptions = options == null ? new DataLoaderOptions() : options;
        this.futureCache = loaderOptions.cacheMap().isPresent() ? (CacheMap<Object, Future<V>>) loaderOptions.cacheMap().get() : CacheMap.simpleMap();
        this.loaderQueue = new LinkedHashMap<>();
    }

    /**
     * Requests to load the data with the specified key asynchronously, and returns a future of the resulting value.
     * <p>
     * If batching is enabled (the default), you'll have to call {@link DataLoader#dispatch()} at a later stage to
     * start batch execution. If you forget this call the future will never be completed (unless already completed,
     * and returned from cache).
     *
     * @param key the key to load
     * @return the future of the value
     */
    public Future<V> load(K key) {
        Objects.requireNonNull(key, "Key cannot be null");
        Object cacheKey = getCacheKey(key);
        if (loaderOptions.cachingEnabled() && futureCache.containsKey(cacheKey)) {
            return futureCache.get(cacheKey);
        }

        Future<V> future = Future.future();
        if (loaderOptions.batchingEnabled()) {
            loaderQueue.put(key, future);
        } else {
            CompositeFuture compositeFuture = batchLoadFunction.load(Collections.singleton(key));
            if (compositeFuture.succeeded()) {
                future.complete(compositeFuture.result().resultAt(0));
            } else {
                future.fail(compositeFuture.cause());
            }
        }
        if (loaderOptions.cachingEnabled()) {
            futureCache.set(cacheKey, future);
        }
        return future;
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
     * @return the composite future of the list of values
     */
    public CompositeFuture loadMany(List<K> keys) {
        return CompositeFuture.join(keys.stream().map(this::load).collect(Collectors.toList()));
    }

    /**
     * Dispatches the queued load requests to the batch execution function and returns a composite future of the result.
     * <p>
     * If batching is disabled, or there are no queued requests, then a succeeded composite future is returned.
     *
     * @return the composite future of the queued load requests
     */
    public CompositeFuture dispatch() {
        if (!loaderOptions.batchingEnabled() || loaderQueue.size() == 0) {
            return CompositeFuture.join(Collections.emptyList());
        }
        CompositeFuture batch = batchLoadFunction.load(loaderQueue.keySet());
        batch.setHandler(rh -> {
            AtomicInteger index = new AtomicInteger(0);
            loaderQueue.forEach((key, future) -> {
                if (batch.succeeded(index.get())) {
                    future.complete(batch.resultAt(index.get()));
                } else {
                    future.fail(batch.cause(index.get()));
                }
                index.incrementAndGet();
            });
            loaderQueue.clear();
        });
        return batch;
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
        futureCache.delete(cacheKey);
        return this;
    }

    /**
     * Clears the entire cache map of the loader.
     *
     * @return the data loader for fluent coding
     */
    public DataLoader<K, V> clearAll() {
        futureCache.clear();
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
        if (!futureCache.containsKey(cacheKey)) {
            futureCache.set(cacheKey, Future.succeededFuture(value));
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
            futureCache.set(cacheKey, Future.failedFuture(error));
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
    @SuppressWarnings("unchecked")
    public Object getCacheKey(K key) {
        return loaderOptions.cacheKeyFunction().isPresent() ?
                loaderOptions.cacheKeyFunction().get().getKey(key) : key;
    }
}
