package org.dataloader;

import org.reactivestreams.Subscriber;

import java.util.List;

/**
 * An {@link PublisherBatchLoader} with a {@link BatchLoaderEnvironment} provided as an extra parameter to {@link #load}.
 */
public interface PublisherBatchLoaderWithContext<K, V> {
    void load(List<K> keys, Subscriber<V> subscriber, BatchLoaderEnvironment environment);
}
