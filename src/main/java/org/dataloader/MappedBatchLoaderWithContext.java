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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * This form of {@link MappedBatchLoader} is given a {@link org.dataloader.BatchLoaderEnvironment} object
 * that encapsulates the calling context.  A typical use case is passing in security credentials or database details
 * for example.
 * <p>
 * See {@link MappedBatchLoader} for more details on the design invariants that you must implement in order to
 * use this interface.
 */
public interface MappedBatchLoaderWithContext<K, V> {
    /**
     * Called to batch load the provided keys and return a promise to a map of values.
     *
     * @param keys        the set of keys to load
     * @param environment the calling environment
     *
     * @return a promise to a map of values for those keys
     */
    @SuppressWarnings("unused")
    CompletionStage<Map<K, V>> load(Set<K> keys, BatchLoaderEnvironment environment);
}
