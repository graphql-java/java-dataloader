package org.dataloader.impl;


import org.dataloader.ValueCache;
import org.dataloader.annotations.Internal;

import java.util.concurrent.CompletableFuture;

/**
 * Implementation of {@link ValueCache} that does nothing.
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
public class NoOpValueCache<K, V> implements ValueCache<K, V> {

    public static final NoOpValueCache<?, ?> NOOP = new NoOpValueCache<>();

    private static final Exception NOOP_GET_FAILURE = new UnsupportedOperationException();

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<V> get(K key) {
        return CompletableFutureKit.failedFuture(NOOP_GET_FAILURE);
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