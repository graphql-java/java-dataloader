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

import org.dataloader.impl.DefaultCacheMap;

/**
 * Cache map interface for data loaders that use caching.
 * <p>
 * The default implementation used by the data loader is based on a {@link java.util.concurrent.ConcurrentHashMap}. Note that the
 * implementation could also have used a regular {@link java.util.Map} instead of this {@link CacheMap}, but
 * this aligns better to the reference data loader implementation provided by Facebook.
 * <p>
 * Also it doesn't require you to implement the full set of map overloads, just the required methods.
 *
 * @param <U> type parameter indicating the type of the cache keys
 * @param <V> type parameter indicating the type of the data that is cached
 *
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 * @author <a href="https://github.com/bbakerman/">Brad Baker</a>
 */
@PublicSpi
public interface CacheMap<U, V> {

    /**
     * Creates a new cache map, using the default implementation that is based on a {@link java.util.concurrent.ConcurrentHashMap}.
     *
     * @param <U> type parameter indicating the type of the cache keys
     * @param <V> type parameter indicating the type of the data that is cached
     *
     * @return the cache map
     */
    static <U, V> CacheMap<U, V> simpleMap() {
        return new DefaultCacheMap<>();
    }

    /**
     * Checks whether the specified key is contained in the cache map.
     *
     * @param key the key to check
     *
     * @return {@code true} if the cache contains the key, {@code false} otherwise
     */
    boolean containsKey(U key);

    /**
     * Gets the specified key from the cache map.
     * <p>
     * The result is wrapped in a {@link Try} to support caching both values and errors.
     * <p>
     * May throw an exception if the key does not exists, depending on the cache map implementation that is used,
     * so be sure to check {@link CacheMap#containsKey(Object)} first.
     *
     * @param key the key to retrieve
     *
     * @return the cached value, error, or {@code null} if not found (depends on cache implementation)
     */
    Try<V> get(U key);

    /**
     * Creates a new cache map entry with the specified key and value, or updates the value if the key already exists.
     *
     * @param key   the key to cache
     * @param value the value to cache
     *
     * @return the cache map for fluent coding
     */
    CacheMap<U, V> set(U key, V value);

    /**
     * Creates a new cache entry with the specified key and error, or updates the error if the key already exists.
     *
     * @param key   the key to cache
     * @param error the error to cache
     *
     * @return the cache map for fluent coding
     */
    CacheMap<U, V> set(U key, Throwable error);

    /**
     * Deletes the entry with the specified key from the cache map, if it exists.
     *
     * @param key the key to delete
     *
     * @return the cache map for fluent coding
     */
    CacheMap<U, V> delete(U key);

    /**
     * Clears all entries of the cache map
     *
     * @return the cache map for fluent coding
     */
    CacheMap<U, V> clear();
}
