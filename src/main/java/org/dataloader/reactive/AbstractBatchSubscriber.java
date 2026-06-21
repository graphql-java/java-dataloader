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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
    final ReactiveSupport.HelperIntegration<K> helperIntegration;
    final Lock lock = new ReentrantLock();

    List<K> clearCacheKeys = new ArrayList<>();
    List<V> completedValues = new ArrayList<>();
    boolean onErrorCalled = false;
    boolean onCompleteCalled = false;

    // the upstream subscription and how much demand we currently have outstanding with it.
    // guarded by lock (see requestMore / requestMoreIfNeeded).
    Subscription subscription;
    long pendingDemand = 0;

    AbstractBatchSubscriber(
            CompletableFuture<List<V>> valuesFuture,
            List<K> keys,
            List<Object> callContexts,
            List<CompletableFuture<V>> queuedFutures,
            ReactiveSupport.HelperIntegration<K> helperIntegration
    ) {
        this.valuesFuture = valuesFuture;
        this.keys = keys;
        this.callContexts = callContexts;
        this.queuedFutures = queuedFutures;
        this.helperIntegration = helperIntegration;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        lock.lock();
        try {
            this.subscription = subscription;
            requestMore();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onNext(T v) {
        assertState(!onErrorCalled, () -> "onError has already been called; onNext may not be invoked.");
        assertState(!onCompleteCalled, () -> "onComplete has already been called; onNext may not be invoked.");
    }

    /**
     * Requests the next window of demand (sized to the key count) from the upstream subscription.
     * <p>
     * Must be called while holding {@link #lock}.
     */
    private void requestMore() {
        long n = keys.size();
        if (n <= 0) {
            return;
        }
        pendingDemand += n;
        subscription.request(n);
    }

    /**
     * Called by the concrete subscribers once they have processed a value in {@code onNext}.
     * <p>
     * A reactive publisher only emits while it has outstanding demand. We originally only ever
     * requested {@code keys.size()} once, so a publisher that emitted values not matching our keys
     * (or simply emitted them lazily as more demand arrived) could leave us blocked forever waiting
     * for a value that needed another request to be delivered. So we:
     * <ol>
     *     <li>re-request another window whenever the outstanding demand drains to zero, until the
     *     publisher completes or errors, and</li>
     *     <li>once every key has a result there is nothing left to wait for, so we cancel the upstream
     *     subscription and complete ourselves rather than blocking on a publisher that may never call
     *     {@code onComplete}.</li>
     * </ol>
     * <p>
     * Must be called while holding {@link #lock}.
     */
    void requestMoreIfNeeded() {
        if (onCompleteCalled || onErrorCalled) {
            return;
        }
        if (allResultsReceived()) {
            subscription.cancel();
            onComplete();
            return;
        }
        if (--pendingDemand <= 0) {
            requestMore();
        }
    }

    /**
     * @return true once a result has been received for every key, so that we can complete early
     * without waiting for the upstream publisher to finish
     */
    abstract boolean allResultsReceived();

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
