package org.dataloader;

import java.util.List;

/**
 * An {@link ObserverBatchLoader} with a {@link BatchLoaderEnvironment} provided as an extra parameter to {@link #load}.
 */
public interface ObserverBatchLoaderWithContext<K, V> {
    void load(List<K> keys, BatchObserver<V> observer, BatchLoaderEnvironment environment);
}
