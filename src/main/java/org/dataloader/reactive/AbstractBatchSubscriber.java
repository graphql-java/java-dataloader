package org.dataloader.reactive;

import org.dataloader.Try;
import org.dataloader.stats.context.IncrementBatchLoadExceptionCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadErrorCountStatisticsContext;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.dataloader.impl.Assertions.assertState;

/**
 * The base class for our reactive subscriber support
 *
 * @param <T> for two
 */
abstract class AbstractBatchSubscriber<K, V, T> implements Subscriber<T> {

    final CompletableFuture<List<V>> valuesFuture;
    final List<K> keys;
    final List<Object> callContexts;
    final List<CompletableFuture<V>> queuedFutures;
    final HelperIntegration<K> helperIntegration;

    List<K> clearCacheKeys = new ArrayList<>();
    List<V> completedValues = new ArrayList<>();
    boolean onErrorCalled = false;
    boolean onCompleteCalled = false;

    AbstractBatchSubscriber(
            CompletableFuture<List<V>> valuesFuture,
            List<K> keys,
            List<Object> callContexts,
            List<CompletableFuture<V>> queuedFutures,
            HelperIntegration<K> helperIntegration
    ) {
        this.valuesFuture = valuesFuture;
        this.keys = keys;
        this.callContexts = callContexts;
        this.queuedFutures = queuedFutures;
        this.helperIntegration = helperIntegration;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        subscription.request(keys.size());
    }

    @Override
    public void onNext(T v) {
        assertState(!onErrorCalled, () -> "onError has already been called; onNext may not be invoked.");
        assertState(!onCompleteCalled, () -> "onComplete has already been called; onNext may not be invoked.");
    }

    @Override
    public void onComplete() {
        assertState(!onErrorCalled, () -> "onError has already been called; onComplete may not be invoked.");
        onCompleteCalled = true;
    }

    @Override
    public void onError(Throwable throwable) {
        assertState(!onCompleteCalled, () -> "onComplete has already been called; onError may not be invoked.");
        onErrorCalled = true;

        helperIntegration.getStats().incrementBatchLoadExceptionCount(new IncrementBatchLoadExceptionCountStatisticsContext<>(keys, callContexts));
    }

    /*
     * A value has arrived - how do we complete the future that's associated with it in a common way
     */
    void onNextValue(K key, V value, Object callContext, List<CompletableFuture<V>> futures) {
        if (value instanceof Try) {
            // we allow the batch loader to return a Try so we can better represent a computation
            // that might have worked or not.
            //noinspection unchecked
            Try<V> tryValue = (Try<V>) value;
            if (tryValue.isSuccess()) {
                futures.forEach(f -> f.complete(tryValue.get()));
            } else {
                helperIntegration.getStats().incrementLoadErrorCount(new IncrementLoadErrorCountStatisticsContext<>(key, callContext));
                futures.forEach(f -> f.completeExceptionally(tryValue.getThrowable()));
                clearCacheKeys.add(key);
            }
        } else {
            futures.forEach(f -> f.complete(value));
        }
    }

    Throwable unwrapThrowable(Throwable ex) {
        if (ex instanceof CompletionException) {
            ex = ex.getCause();
        }
        return ex;
    }

    void possiblyClearCacheEntriesOnExceptions() {
        helperIntegration.clearCacheEntriesOnExceptions(clearCacheKeys);
    }
}
