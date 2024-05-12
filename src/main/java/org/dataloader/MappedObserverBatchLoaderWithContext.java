package org.dataloader;

import java.util.List;

/**
 * A {@link MappedObserverBatchLoader} with a {@link BatchLoaderEnvironment} provided as an extra parameter to {@link #load}.
 */
public interface MappedObserverBatchLoaderWithContext<K, V> {
    void load(List<K> keys, MappedBatchObserver<K, V> observer, BatchLoaderEnvironment environment);
}
