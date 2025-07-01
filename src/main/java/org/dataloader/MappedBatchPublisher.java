package org.dataloader;

import org.dataloader.annotations.PublicSpi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;

import java.util.Map;
import java.util.Set;

/**
 * A function that is invoked for batch loading a stream of data values indicated by the provided list of keys.
 * <p>
 * The function <b>must</b> call the provided {@link Subscriber} to process the key/value pairs it has retrieved to allow
 * the future returned by {@link DataLoader#load(Object)} to complete as soon as the individual value is available
 * (rather than when all values have been retrieved).
 *
 * @param <K> type parameter indicating the type of keys to use for data load requests.
 * @param <V> type parameter indicating the type of values returned
 * @see MappedBatchLoader for the non-reactive version
 */
@PublicSpi
@NullMarked
public interface MappedBatchPublisher<K, V extends @Nullable Object> {
    /**
     * Called to batch the provided keys into a stream of map entries of keys and values.
     * <p>
     * The idiomatic approach would be to create a reactive {@link org.reactivestreams.Publisher} that provides
     * the values given the keys and then subscribe to it with the provided {@link Subscriber}.
     *
     * @param keys       the collection of keys to load
     * @param subscriber as values arrive you must call the subscriber for each value
     */
    void load(Set<K> keys, Subscriber<Map.Entry<K, V>> subscriber);
}
