package org.dataloader;

import org.reactivestreams.Subscriber;

import java.util.List;
import java.util.Map;

/**
 * A {@link MappedPublisherBatchLoader} with a {@link BatchLoaderEnvironment} provided as an extra parameter to {@link #load}.
 */
public interface MappedPublisherBatchLoaderWithContext<K, V> {
    void load(List<K> keys, Subscriber<Map.Entry<K, V>> subscriber, BatchLoaderEnvironment environment);
}
