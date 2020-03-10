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

import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of {@link CacheMap} that is based on a regular {@link java.util.LinkedHashMap}.
 *
 * @param <U> type parameter indicating the type of the cache keys
 * @param <V> type parameter indicating the type of the data that is cached
 *
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 */
@Internal
public class DefaultCacheMap<U, V> implements CacheMap<U, V> {

    private Map<U, V> cache;

    /**
     * Default constructor
     */
    public DefaultCacheMap() {
        cache = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(U key) {
        return cache.containsKey(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V get(U key) {
        return cache.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMap<U, V> set(U key, V value) {
        cache.put(key, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMap<U, V> delete(U key) {
        cache.remove(key);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMap<U, V> clear() {
        cache.clear();
        return this;
    }
}
