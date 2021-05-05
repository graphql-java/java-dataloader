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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.dataloader.CacheMap;
import org.dataloader.Internal;
import org.dataloader.Try;

/**
 * Default implementation of {@link CacheMap} that is based on a {@link ConcurrentHashMap}.
 *
 * @param <U> type parameter indicating the type of the cache keys
 * @param <V> type parameter indicating the type of the data that is cached
 *
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 */
@Internal
public class DefaultCacheMap<U, V> implements CacheMap<U, V> {

    private final Map<U, V> cache;
    private final Map<U, Throwable> errorCache;

    /**
     * Default constructor
     */
    public DefaultCacheMap() {
        cache = new ConcurrentHashMap<>();
        errorCache = new ConcurrentHashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(U key) {
        return cache.containsKey(key) || errorCache.containsKey(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Try<V> get(U key) {
        final Throwable error = errorCache.get(key);
        final V value = cache.get(key);

        if (error != null) {
            return Try.failed(error);
        } else {
            return Try.succeeded(value);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMap<U, V> set(U key, V value) {
        cache.put(key, value);
        errorCache.remove(key);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMap<U, V> set(U key, Throwable error) {
        cache.remove(key);
        errorCache.put(key, error);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMap<U, V> setIfAbsent(U key, V value) {
        cache.putIfAbsent(key, value);
        errorCache.remove(key);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMap<U, V> setIfAbsent(U key, Throwable error) {
        cache.remove(key);
        errorCache.putIfAbsent(key, error);
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMap<U, V> delete(U key) {
        cache.remove(key);
        errorCache.remove(key);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMap<U, V> clear() {
        cache.clear();
        errorCache.clear();
        return this;
    }
}
