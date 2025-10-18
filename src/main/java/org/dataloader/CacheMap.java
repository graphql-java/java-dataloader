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

import org.dataloader.annotations.PublicSpi;
import org.dataloader.impl.DefaultCacheMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * CacheMap is used by data loaders that use caching promises to values aka {@link CompletableFuture}&lt;V&gt;.  A better name for this
 * class might have been FutureCache but that is history now.
 * <p>
 * The default implementation used by the data loader is based on a {@link java.util.LinkedHashMap}.
 * <p>
 * This is really a cache of completed {@link CompletableFuture}&lt;V&gt; values in memory.  It is used, when caching is enabled, to
 * give back the same future to any code that may call it.  If you need a cache of the underlying values that is possible external to the JVM
 * then you will want to use {{@link ValueCache}} which is designed for external cache access.
 *
 * @param <K> type parameter indicating the type of the cache keys
 * @param <V> type parameter indicating the type of the data that is cached
 *
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 * @author <a href="https://github.com/bbakerman/">Brad Baker</a>
 */
@PublicSpi
@NullMarked
public interface CacheMap<K, V> {

    /**
     * Creates a new cache map, using the default implementation that is based on a {@link java.util.LinkedHashMap}.
     *
     * @param <K> type parameter indicating the type of the cache keys
     * @param <V> type parameter indicating the type of the data that is cached
     *
     * @return the cache map
     */
    static <K, V> CacheMap<K, V> simpleMap() {
        return new DefaultCacheMap<>();
    }

    /**
     * Checks whether the specified key is contained in the cache map.
     *
     * @param key the key to check
     *
     * @return {@code true} if the cache contains the key, {@code false} otherwise
     */
    boolean containsKey(K key);

    /**
     * Gets the specified key from the cache map.
     * <p>
     * May throw an exception if the key does not exist, depending on the cache map implementation that is used.
     *
     * @param key the key to retrieve
     *
     * @return the cached value, or {@code null} if not found (depends on cache implementation)
     */
    @Nullable  CompletableFuture<V> get(K key);

    /**
     * Gets a collection of CompletableFutures from the cache map.
     * @return the collection of cached values
     */
    Collection<CompletableFuture<V>> getAll();

    /**
     * Creates a new cache map entry with the specified key and value, or updates the value if the key already exists.
     *
     * @param key   the key to cache
     * @param value the value to cache
     *
     * @return the cache map for fluent coding
     */
    CompletableFuture<V> setIfAbsent(K key, CompletableFuture<V> value);

    /**
     * Deletes the entry with the specified key from the cache map, if it exists.
     *
     * @param key the key to delete
     *
     * @return the cache map for fluent coding
     */
    CacheMap<K, V> delete(K key);

    /**
     * Clears all entries of the cache map
     *
     * @return the cache map for fluent coding
     */
    CacheMap<K, V> clear();
}
