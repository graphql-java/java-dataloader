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

import java.util.List;

/**
 * A function that is invoked for batch loading a list of data values indicated by the provided list of keys. The
 * function returns a {@link PromisedValues} to aggregate results of individual load requests.
 *
 * There are a few constraints that must be upheld:
 * <ul>
 * <li>The list of values must be the same size as the list of keys.</li>
 * <li>Each index in the list of values must correspond to the same index in the list of keys.</li>
 * </ul>
 *
 * For example, if your batch function was provided the list of keys: [ 2, 9, 6, 1 ], and loading from a back-end service returned the values:
 *
 * <pre>
 *  [
 *      { id: 9, name: 'Chicago' },
 *      { id: 1, name: 'New York' },
 *      { id: 2, name: 'San Francisco' },
 *  ]
 * </pre>
 *
 * The back-end service returned results in a different order than we requested, likely because it was more efficient for it to do so. Also, it omitted a result for key 6, which we can interpret as no value
 * existing for that key.
 *
 * To uphold the constraints of the batch function, it must return an List of values the same length as the List of keys, and re-order them to ensure each index aligns with the original keys [ 2, 9, 6, 1 ]:
 *
 * <pre>
 *  [
 *      { id: 2, name: 'San Francisco' },
 *      { id: 9, name: 'Chicago' },
 *      null,
 *      { id: 1, name: 'New York' }
 * ]
 * </pre>
 *
 * @param <K> type parameter indicating the type of keys to use for data load requests.
 * @param <V> type parameter indicating the type of values returned
 *
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 */
@FunctionalInterface
public interface BatchLoader<K, V> {

    /**
     * Called to batch load the provided keys and return a {@link PromisedValues} which is a promise to a list of values
     *
     * @param keys the collection of keys to load
     *
     * @return a promise of the values for those keys
     */
    PromisedValues<V> load(List<K> keys);
}
