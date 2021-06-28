package org.dataloader.impl;


import org.dataloader.CachedValueStore;
import org.dataloader.annotations.Internal;

import java.util.concurrent.CompletableFuture;

/**
 * Default implementation of {@link CachedValueStore} that does nothing.
 * <p>
 * We don't want to store values in memory twice, so when using the default store we just
 * say we never have the key and complete the other methods by doing nothing.
 *
 * @param <K> the type of cache keys
 * @param <V> the type of cache values
 *
 * @author <a href="https://github.com/craig-day">Craig Day</a>
 */
@Internal
public class DefaultCachedValueStore<K, V> implements CachedValueStore<K, V> {

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Boolean> containsKey(K key) {
        return CompletableFuture.completedFuture(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<V> get(K key) {
        return CompletableFutureKit.failedFuture(new UnsupportedOperationException());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<V> set(K key, V value) {
        return CompletableFuture.completedFuture(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Void> delete(K key) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Void> clear() {
        return CompletableFuture.completedFuture(null);
    }
}