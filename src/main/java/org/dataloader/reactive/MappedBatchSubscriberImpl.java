package org.dataloader.reactive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * This class can be used to subscribe to a {@link org.reactivestreams.Publisher} and then
 * have the values it receives complete the data loader keys in a map lookup fashion.
 * <p>
 * This is a reactive version of {@link org.dataloader.MappedBatchLoader}
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
class MappedBatchSubscriberImpl<K, V> extends AbstractBatchSubscriber<K, V, Map.Entry<K, V>> {

    private final Map<K, Object> callContextByKey;
    private final Map<K, List<CompletableFuture<V>>> queuedFuturesByKey;
    private final Map<K, V> completedValuesByKey = new HashMap<>();


    MappedBatchSubscriberImpl(
            CompletableFuture<List<V>> valuesFuture,
            List<K> keys,
            List<Object> callContexts,
            List<CompletableFuture<V>> queuedFutures,
            ReactiveSupport.HelperIntegration<K> helperIntegration
    ) {
        super(valuesFuture, keys, callContexts, queuedFutures, helperIntegration);
        this.callContextByKey = new HashMap<>();
        this.queuedFuturesByKey = new HashMap<>();
        for (int idx = 0; idx < queuedFutures.size(); idx++) {
            K key = keys.get(idx);
            Object callContext = callContexts.get(idx);
            CompletableFuture<V> queuedFuture = queuedFutures.get(idx);
            callContextByKey.put(key, callContext);
            queuedFuturesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(queuedFuture);
        }
    }


    @Override
    public void onNext(Map.Entry<K, V> entry) {
        try {
            lock.lock();
            super.onNext(entry);
            K key = entry.getKey();
            V value = entry.getValue();

            Object callContext = callContextByKey.get(key);
            List<CompletableFuture<V>> futures = queuedFuturesByKey.getOrDefault(key, List.of());

            onNextValue(key, value, callContext, futures);

            // did we have an actual key for this value - ignore it if they send us one outside the key set
            if (!futures.isEmpty()) {
                completedValuesByKey.put(key, value);
            }
        } finally {
            lock.unlock();
        }

    }

    @Override
    public void onComplete() {
        try {
            lock.lock();
            super.onComplete();

            possiblyClearCacheEntriesOnExceptions();
            List<V> values = new ArrayList<>(keys.size());
            for (K key : keys) {
                V value = completedValuesByKey.get(key);
                values.add(value);

                List<CompletableFuture<V>> futures = queuedFuturesByKey.getOrDefault(key, List.of());
                for (CompletableFuture<V> future : futures) {
                    if (!future.isDone()) {
                        // we have a future that never came back for that key
                        // but the publisher is done sending in data - it must be null
                        // e.g. for key X when found no value
                        future.complete(null);
                    }
                }
            }
            valuesFuture.complete(values);
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
            // Complete the futures for the remaining keys with the exception.
            for (int idx = 0; idx < queuedFutures.size(); idx++) {
                K key = keys.get(idx);
                List<CompletableFuture<V>> futures = queuedFuturesByKey.get(key);
                if (!completedValuesByKey.containsKey(key)) {
                    for (CompletableFuture<V> future : futures) {
                        future.completeExceptionally(ex);
                    }
                    // clear any cached view of this key because they all failed
                    helperIntegration.clearCacheView(key);
                }
            }
            valuesFuture.completeExceptionally(ex);
        } finally {
            lock.unlock();
        }
    }
}
