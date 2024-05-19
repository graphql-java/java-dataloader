package org.dataloader;

import org.reactivestreams.Subscriber;

import java.util.List;
import java.util.Map;

/**
 * A function that is invoked for batch loading a stream of data values indicated by the provided list of keys.
 * <p>
 * The function will call the provided {@link Subscriber} to process the key/value pairs it has retrieved to allow
 * the future returned by {@link DataLoader#load(Object)} to complete as soon as the individual value is available
 * (rather than when all values have been retrieved).
 *
 * @param <K> type parameter indicating the type of keys to use for data load requests.
 * @param <V> type parameter indicating the type of values returned
 */
public interface MappedBatchPublisher<K, V> {
    void load(List<K> keys, Subscriber<Map.Entry<K, V>> subscriber);
}
