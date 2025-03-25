package org.dataloader;

import org.dataloader.annotations.PublicSpi;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * This form of {@link org.dataloader.BatchLoader} is given a {@link org.dataloader.BatchLoaderEnvironment} object
 * that encapsulates the calling context.  A typical use case is passing in security credentials or database connection details
 * say.
 *
 * See {@link org.dataloader.BatchLoader} for more details on the design invariants that you must implement in order to
 * use this interface.
 */
@PublicSpi
@NullMarked
public interface BatchLoaderWithContext<K, V> {
    /**
     * Called to batch load the provided keys and return a promise to a list of values.  This default
     * version can be given an environment object to that maybe be useful during the call.  A typical use case
     * is passing in security credentials or database details for example.
     *
     * @param keys        the collection of keys to load
     * @param environment an environment object that can help with the call
     *
     * @return a promise of the values for those keys
     */
    CompletionStage<List<V>> load(List<K> keys, BatchLoaderEnvironment environment);
}
