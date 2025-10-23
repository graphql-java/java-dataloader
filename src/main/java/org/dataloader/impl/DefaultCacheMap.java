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
import org.dataloader.annotations.Internal;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link CacheMap} that is based on a regular {@link java.util.HashMap}.
 *
 * @param <K> type parameter indicating the type of the cache keys
 * @param <V> type parameter indicating the type of the data that is cached
 *
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 */
@Internal
public class DefaultCacheMap<K, V> implements CacheMap<K, V> {

    private final ConcurrentHashMap<K, CompletableFuture<V>> cache;

    /**
     * Default constructor
     */
    public DefaultCacheMap() {
        cache = new ConcurrentHashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<V> get(K key) {
        return cache.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<CompletableFuture<V>> getAll() {
        return cache.values();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<V> putIfAbsentAtomically(K key, CompletableFuture<V> value) {
        return cache.putIfAbsent(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMap<K, V> delete(K key) {
        cache.remove(key);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMap<K, V> clear() {
        cache.clear();
        return this;
    }

    @Override
    public int size() {
        return cache.size();
    }
}
