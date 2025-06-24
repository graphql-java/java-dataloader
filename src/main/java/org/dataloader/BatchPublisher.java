package org.dataloader;

import org.dataloader.annotations.PublicSpi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;

import java.util.List;

/**
 * A function that is invoked for batch loading a stream of data values indicated by the provided list of keys.
 * <p>
 * The function <b>must</b> call the provided {@link Subscriber} to process the values it has retrieved to allow
 * the future returned by {@link DataLoader#load(Object)} to complete as soon as the individual value is available
 * (rather than when all values have been retrieved).
 * <p>
 * <b>NOTE:</b> It is <b>required</b> that {@link Subscriber#onNext(Object)} is invoked on each value in the same order as
 * the provided keys and that you provide a value for every key provided.
 *
 * @param <K> type parameter indicating the type of keys to use for data load requests.
 * @param <V> type parameter indicating the type of values returned
 * @see BatchLoader for the non-reactive version
 */
@NullMarked
@PublicSpi
public interface BatchPublisher<K, V extends @Nullable Object> {
    /**
     * Called to batch the provided keys into a stream of values.  You <b>must</b> provide
     * the same number of values as there as keys, and they <b>must</b> be in the order of the keys.
     * <p>
     * The idiomatic approach would be to create a reactive {@link org.reactivestreams.Publisher} that provides
     * the values given the keys and then subscribe to it with the provided {@link Subscriber}.
     * <p>
     * <b>NOTE:</b> It is <b>required</b> that {@link Subscriber#onNext(Object)} is invoked on each value in the same order as
     * the provided keys and that you provide a value for every key provided.
     *
     * @param keys       the collection of keys to load
     * @param subscriber as values arrive you must call the subscriber for each value
     */
    void load(List<K> keys, Subscriber<V> subscriber);
}
