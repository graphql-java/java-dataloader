package org.dataloader.impl;


import org.dataloader.Try;
import org.dataloader.ValueCache;
import org.dataloader.annotations.Internal;

import java.util.List;
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

    /**
     * a no op value cache instance
     */
    public static final NoOpValueCache<?, ?> NOOP = new NoOpValueCache<>();

    // avoid object allocation by using a final field
    private final ValueCachingNotSupported NOT_SUPPORTED = new ValueCachingNotSupported();
    private final CompletableFuture<V> NOT_SUPPORTED_CF = CompletableFutureKit.failedFuture(NOT_SUPPORTED);
    private final CompletableFuture<Void> NOT_SUPPORTED_VOID_CF = CompletableFuture.completedFuture(null);

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<V> get(K key) {
        return NOT_SUPPORTED_CF;
    }

    @Override
    public CompletableFuture<List<Try<V>>> getValues(List<K> keys) throws ValueCachingNotSupported {
        throw NOT_SUPPORTED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<V> set(K key, V value) {
        return NOT_SUPPORTED_CF;
    }

    @Override
    public CompletableFuture<List<V>> setValues(List<K> keys, List<V> values) throws ValueCachingNotSupported {
        throw NOT_SUPPORTED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Void> delete(K key) {
        return NOT_SUPPORTED_VOID_CF;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Void> clear() {
        return NOT_SUPPORTED_VOID_CF;
    }
}