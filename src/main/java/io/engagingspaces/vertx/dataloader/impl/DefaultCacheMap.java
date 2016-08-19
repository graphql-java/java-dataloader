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

package io.engagingspaces.vertx.dataloader.impl;

import io.engagingspaces.vertx.dataloader.CacheMap;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @param <K>
 * @param <V>
 *
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 */
public class DefaultCacheMap<K, V> implements CacheMap<K, V> {

    private Map<K, V> cache;

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
    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V get(K key) {
        return cache.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMap set(K key, V value) {
        cache.put(key, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMap delete(K key) {
        cache.remove(key);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CacheMap clear() {
        cache.clear();
        return this;
    }
}
