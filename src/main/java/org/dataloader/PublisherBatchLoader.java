package org.dataloader;

import org.reactivestreams.Subscriber;

import java.util.List;

/**
 * A function that is invoked for batch loading a stream of data values indicated by the provided list of keys.
 * <p>
 * The function will call the provided {@link Subscriber} to process the values it has retrieved to allow
 * the future returned by {@link DataLoader#load(Object)} to complete as soon as the individual value is available
 * (rather than when all values have been retrieved).
 * <p>
 * It is required that values be returned in the same order as the keys provided.
 *
 * @param <K> type parameter indicating the type of keys to use for data load requests.
 * @param <V> type parameter indicating the type of values returned
 */
public interface PublisherBatchLoader<K, V> {
    void load(List<K> keys, Subscriber<V> subscriber);
}
