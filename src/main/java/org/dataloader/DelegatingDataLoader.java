package org.dataloader;

import org.dataloader.annotations.PublicApi;
import org.dataloader.stats.Statistics;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * This delegating {@link DataLoader} makes it easier to create wrappers of {@link DataLoader}s in case you want to change how
 * values are returned for example
 *
 * @param <K> type parameter indicating the type of the data load keys
 * @param <V> type parameter indicating the type of the data that is returned
 */
@PublicApi
public class DelegatingDataLoader<K, V> extends DataLoader<K, V> {

    protected final DataLoader<K, V> delegate;

    /**
     * This can be called to unwrap a given {@link DataLoader} such that if it's a {@link DelegatingDataLoader} the underlying
     * {@link DataLoader} is returned otherwise it's just passed in data loader
     *
     * @param dataLoader the dataLoader to unwrap
     * @param <K>        type parameter indicating the type of the data load keys
     * @param <V>        type parameter indicating the type of the data that is returned
     * @return the delegate dataLoader OR just this current one if it's not wrapped
     */
    public static <K, V> DataLoader<K, V> unwrap(DataLoader<K, V> dataLoader) {
        if (dataLoader instanceof DelegatingDataLoader) {
            return ((DelegatingDataLoader<K, V>) dataLoader).getDelegate();
        }
        return dataLoader;
    }

    public DelegatingDataLoader(DataLoader<K, V> delegate) {
        super(delegate.getBatchLoadFunction(), delegate.getOptions());
        this.delegate = delegate;
    }

    public DataLoader<K, V> getDelegate() {
        return delegate;
    }

    /**
     * The {@link DataLoader#load(Object)} and {@link DataLoader#loadMany(List)} type methods all call back
     * to the {@link DataLoader#load(Object, Object)} and hence we don't override them.
     *
     * @param key        the key to load
     * @param keyContext a context object that is specific to this key
     * @return the future of the value
     */
    @Override
    public CompletableFuture<V> load(K key, Object keyContext) {
        return delegate.load(key, keyContext);
    }


    @Override
    public DataLoader<K, V> transform(Consumer<DataLoaderFactory.Builder<K, V>> builderConsumer) {
        return delegate.transform(builderConsumer);
    }

    @Override
    public Instant getLastDispatchTime() {
        return delegate.getLastDispatchTime();
    }

    @Override
    public Duration getTimeSinceDispatch() {
        return delegate.getTimeSinceDispatch();
    }

    @Override
    public Optional<CompletableFuture<V>> getIfPresent(K key) {
        return delegate.getIfPresent(key);
    }

    @Override
    public Optional<CompletableFuture<V>> getIfCompleted(K key) {
        return delegate.getIfCompleted(key);
    }

    @Override
    public CompletableFuture<List<V>> dispatch() {
        return delegate.dispatch();
    }

    @Override
    public DispatchResult<V> dispatchWithCounts() {
        return delegate.dispatchWithCounts();
    }

    @Override
    public List<V> dispatchAndJoin() {
        return delegate.dispatchAndJoin();
    }

    @Override
    public int dispatchDepth() {
        return delegate.dispatchDepth();
    }

    @Override
    public Object getCacheKey(K key) {
        return delegate.getCacheKey(key);
    }

    @Override
    public Statistics getStatistics() {
        return delegate.getStatistics();
    }

    @Override
    public CacheMap<Object, V> getCacheMap() {
        return delegate.getCacheMap();
    }

    @Override
    public ValueCache<K, V> getValueCache() {
        return delegate.getValueCache();
    }

    @Override
    public DataLoader<K, V> clear(K key) {
        delegate.clear(key);
        return this;
    }

    @Override
    public DataLoader<K, V> clear(K key, BiConsumer<Void, Throwable> handler) {
        delegate.clear(key, handler);
        return this;
    }

    @Override
    public DataLoader<K, V> clearAll() {
        delegate.clearAll();
        return this;
    }

    @Override
    public DataLoader<K, V> clearAll(BiConsumer<Void, Throwable> handler) {
        delegate.clearAll(handler);
        return this;
    }

    @Override
    public DataLoader<K, V> prime(K key, V value) {
        delegate.prime(key, value);
        return this;
    }

    @Override
    public DataLoader<K, V> prime(K key, Exception error) {
        delegate.prime(key, error);
        return this;
    }

    @Override
    public DataLoader<K, V> prime(K key, CompletableFuture<V> value) {
        delegate.prime(key, value);
        return this;
    }
}
