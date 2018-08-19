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
import java.util.concurrent.CompletionStage;

/**
 * A function that is invoked for batch loading a list of data values indicated by the provided list of keys. The
 * function returns a promise of a list of results of individual load requests.
 *
 * There are a few constraints that must be upheld:
 * <ul>
 * <li>The list of values must be the same size as the list of keys.</li>
 * <li>Each index in the list of values must correspond to the same index in the list of keys.</li>
 * </ul>
 *
 * For example, if your batch function was provided the list of keys:
 *
 * <pre>
 *  [
 *      2, 9, 6, 1
 *  ]
 * </pre>
 *
 * and loading from a back-end service returned this list of  values:
 *
 * <pre>
 *  [
 *      { id: 9, name: 'Chicago' },
 *      { id: 1, name: 'New York' },
 *      { id: 2, name: 'San Francisco' },
 *  ]
 * </pre>
 *
 * then the batch loader function contract has been broken.
 *
 * The back-end service returned results in a different order than we requested, likely because it was more efficient for it to
 * do so. Also, it omitted a result for key 6, which we may interpret as no value existing for that key.
 *
 * To uphold the constraints of the batch function, it must return an List of values the same length as
 * the List of keys, and re-order them to ensure each index aligns with the original keys [ 2, 9, 6, 1 ]:
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
 * @author <a href="https://github.com/bbakerman/">Brad Baker</a>
 */
@FunctionalInterface
public interface BatchLoader<K, V> {

    /**
     * Called to batch load the provided keys and return a promise to a list of values.
     *
     * If you need calling context then implement the {@link #load(java.util.List, Object)} method
     * instead.
     *
     * @param keys the collection of keys to load
     *
     * @return a promise of the values for those keys
     */
    CompletionStage<List<V>> load(List<K> keys);

    /**
     * Called to batch load the provided keys and return a promise to a list of values.  This default
     * version can be given a context object to that maybe be useful during the call.  A typical use case
     * is passing in security credentials or database connection details say.
     *
     * This method is implemented as a default method in order to preserve the API for previous
     * callers.  It is always called first by the {@link org.dataloader.DataLoader} code and simply
     * delegates to the {@link #load(java.util.List)} method.
     *
     * @param keys    the collection of keys to load
     * @param context a context object that can help with the call
     *
     * @return a promise of the values for those keys
     */
    @SuppressWarnings("unused")
    default CompletionStage<List<V>> load(List<K> keys, Object context) {
        return load(keys);
    }
}
