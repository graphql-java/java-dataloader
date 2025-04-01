package org.dataloader;

import org.dataloader.annotations.PublicSpi;
import org.jspecify.annotations.NullMarked;
import org.reactivestreams.Subscriber;

import java.util.List;

/**
 * This form of {@link BatchPublisher} is given a {@link org.dataloader.BatchLoaderEnvironment} object
 * that encapsulates the calling context.  A typical use case is passing in security credentials or database details
 * for example.
 * <p>
 * See {@link BatchPublisher} for more details on the design invariants that you must implement in order to
 * use this interface.
 */
@NullMarked
@PublicSpi
public interface BatchPublisherWithContext<K, V> {
    /**
     * Called to batch the provided keys into a stream of values.  You <b>must</b> provide
     * the same number of values as there as keys, and they <b>must</b> be in the order of the keys.
     * <p>
     * The idiomatic approach would be to create a reactive {@link org.reactivestreams.Publisher} that provides
     * the values given the keys and then subscribe to it with the provided {@link Subscriber}.
     * <p>
     * <b>NOTE:</b> It is <b>required</b> that {@link Subscriber#onNext(Object)} is invoked on each value in the same order as
     * the provided keys and that you provide a value for every key provided.
     * <p>
     * This is given an environment object to that maybe be useful during the call.  A typical use case
     * is passing in security credentials or database details for example.
     *
     * @param keys        the collection of keys to load
     * @param subscriber  as values arrive you must call the subscriber for each value
     * @param environment an environment object that can help with the call
     */
    void load(List<K> keys, Subscriber<V> subscriber, BatchLoaderEnvironment environment);
}
