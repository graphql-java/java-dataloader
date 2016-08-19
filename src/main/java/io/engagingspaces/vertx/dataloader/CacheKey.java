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

/**
 * Function that is invoked on input keys of type {@code K} to derive keys that are required by the {@link CacheMap}
 * implementation.
 *
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 */
@FunctionalInterface
public interface CacheKey {

    /**
     * Returns the cache key that is created from the provided input key.
     *
     * @param input the input key
     * @param <K>   type parameter indicating the type of the input key
     * @param <U>   type parameter indicating the type of the cache key that is returned
     * @return the cache key
     */
    <K, U> U getKey(K input);
}
