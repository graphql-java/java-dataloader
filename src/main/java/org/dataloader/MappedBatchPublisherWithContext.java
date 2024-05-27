package org.dataloader;

import org.reactivestreams.Subscriber;

import java.util.List;
import java.util.Map;

/**
 * This form of {@link MappedBatchPublisher} is given a {@link org.dataloader.BatchLoaderEnvironment} object
 * that encapsulates the calling context.  A typical use case is passing in security credentials or database details
 * for example.
 * <p>
 * See {@link MappedBatchPublisher} for more details on the design invariants that you must implement in order to
 * use this interface.
 */
public interface MappedBatchPublisherWithContext<K, V> {

    /**
     * Called to batch the provided keys into a stream of map entries of keys and values.
     * <p>
     * The idiomatic approach would be to create a reactive {@link org.reactivestreams.Publisher} that provides
     * the values given the keys and then subscribe to it with the provided {@link Subscriber}.
     * <p>
     * This is given an environment object to that maybe be useful during the call.  A typical use case
     * is passing in security credentials or database details for example.
     *
     * @param keys        the collection of keys to load
     * @param subscriber  as values arrive you must call the subscriber for each value
     * @param environment an environment object that can help with the call
     */
    void load(List<K> keys, Subscriber<Map.Entry<K, V>> subscriber, BatchLoaderEnvironment environment);
}
