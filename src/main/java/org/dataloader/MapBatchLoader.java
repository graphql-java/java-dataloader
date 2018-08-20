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
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * A function that is invoked for batch loading a map of of data values indicated by the provided list of keys. The
 * function returns a promise of a map of results of individual load requests.
 *
 * There are a few constraints that must be upheld:
 * <ul>
 * <li>The keys MUST be able to be first class keys in a Java map.  Get your equals() and hashCode() methods in order</li>
 * <li>The caller of the {@link org.dataloader.DataLoader} that uses this batch loader function MUSt be able to cope with
 * null values coming back as results
 * </li>
 * <li>The function MUST be resilient to the same key being presented twice.</li>
 * </ul>
 *
 * This form is useful when you don't have a 1:1 mapping of keys to values or when null is an acceptable value for a missing value.
 *
 * For example, let's assume you want to load users from a database, you could probably use a query that looks like this:
 *
 * <pre>
 *    SELECT * FROM User WHERE id IN (keys)
 * </pre>
 *
 * Given say 10 user id keys you might only get 7 results back.  This can be more naturally represented in a map
 * than in an order list of values when returning values from the batch loader function.
 *
 * When the map is processed by the {@link org.dataloader.DataLoader} code, any keys that are missing in the map
 * will be replaced with null values.  The semantics that the number of {@link org.dataloader.DataLoader#load(Object)} requests
 * are matched with values is kept.
 *
 * This means that if 10 keys are asked for then {@link DataLoader#dispatch()} will return a promise 10 value results and each
 * of the {@link org.dataloader.DataLoader#load(Object)} will complete with a value, null or an exception.
 *
 * When caching is disabled, its possible for the same key to be presented in the list of keys more than once.  You map
 * batch loader function needs to be resilient to this situation.
 *
 * @param <K> type parameter indicating the type of keys to use for data load requests.
 * @param <V> type parameter indicating the type of values returned
 *
 * @author <a href="https://github.com/bbakerman/">Brad Baker</a>
 */
public interface MapBatchLoader<K, V> {

    /**
     * Called to batch load the provided keys and return a promise to a map of values.  It can be given an environment object to
     * that maybe be useful during the call.  A typical use case is passing in security credentials or database connection details say.
     *
     * @param keys        the collection of keys to load
     * @param environment an environment object that can help with the call
     *
     * @return a promise to a map of values for those keys
     */
    @SuppressWarnings("unused")
    CompletionStage<Map<K, V>> load(List<K> keys, BatchLoaderEnvironment environment);
}
