package org.dataloader.reactive;

import org.dataloader.impl.DataLoaderAssertionException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * This class can be used to subscribe to a {@link org.reactivestreams.Publisher} and then
 * have the values it receives complete the data loader keys.  The keys and values must be
 * in index order.
 * <p>
 * This is a reactive version of {@link org.dataloader.BatchLoader}
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
class BatchSubscriberImpl<K, V> extends AbstractBatchSubscriber<K, V, V> {

    private int idx = 0;

    BatchSubscriberImpl(
            CompletableFuture<List<V>> valuesFuture,
            List<K> keys,
            List<Object> callContexts,
            List<CompletableFuture<V>> queuedFutures,
            ReactiveSupport.HelperIntegration<K> helperIntegration
    ) {
        super(valuesFuture, keys, callContexts, queuedFutures, helperIntegration);
    }

    // onNext may be called by multiple threads - for the time being, we use a ReentrantLock to guarantee
    // correctness (at the cost of speed).
    @Override
    public void onNext(V value) {
        try {
            lock.lock();

            super.onNext(value);

            if (idx >= keys.size()) {
                // hang on they have given us more values than we asked for in keys
                // we cant handle this
                return;
            }
            K key = keys.get(idx);
            Object callContext = callContexts.get(idx);
            CompletableFuture<V> future = queuedFutures.get(idx);
            onNextValue(key, value, callContext, List.of(future));

            completedValues.add(value);
            idx++;
        } finally {
            lock.unlock();
        }
    }


    @Override
    public void onComplete() {
        try {
            lock.lock();
            super.onComplete();
            if (keys.size() != completedValues.size()) {
                // we have more or less values than promised
                // we will go through all the outstanding promises and mark those that
                // have not finished as failed
                for (CompletableFuture<V> queuedFuture : queuedFutures) {
                    if (!queuedFuture.isDone()) {
                        queuedFuture.completeExceptionally(new DataLoaderAssertionException("The size of the promised values MUST be the same size as the key list"));
                    }
                }
            }
            possiblyClearCacheEntriesOnExceptions();
            valuesFuture.complete(completedValues);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onError(Throwable ex) {
        try {
            lock.lock();
            super.onError(ex);
            ex = unwrapThrowable(ex);
            // Set the remaining keys to the exception.
            for (int i = idx; i < queuedFutures.size(); i++) {
                K key = keys.get(i);
                CompletableFuture<V> future = queuedFutures.get(i);
                if (!future.isDone()) {
                    future.completeExceptionally(ex);
                    // clear any cached view of this key because it failed
                    helperIntegration.clearCacheView(key);
                }
            }
            valuesFuture.completeExceptionally(ex);
        } finally {
            lock.unlock();
        }
    }
}
