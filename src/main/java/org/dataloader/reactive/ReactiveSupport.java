package org.dataloader.reactive;

import org.dataloader.stats.StatisticsCollector;
import org.reactivestreams.Subscriber;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ReactiveSupport {

    public static <K, V> Subscriber<V> batchSubscriber(
            CompletableFuture<List<V>> valuesFuture,
            List<K> keys,
            List<Object> callContexts,
            List<CompletableFuture<V>> queuedFutures,
            ReactiveSupport.HelperIntegration<K> helperIntegration
    ) {
        return new BatchSubscriberImpl<>(valuesFuture, keys, callContexts, queuedFutures, helperIntegration);
    }

    public static <K, V> Subscriber<Map.Entry<K, V>> mappedBatchSubscriber(
            CompletableFuture<List<V>> valuesFuture,
            List<K> keys,
            List<Object> callContexts,
            List<CompletableFuture<V>> queuedFutures,
            ReactiveSupport.HelperIntegration<K> helperIntegration
    ) {
        return new MappedBatchSubscriberImpl<>(valuesFuture, keys, callContexts, queuedFutures, helperIntegration);
    }

    /**
     * Just some callbacks to the data loader code to do common tasks
     *
     * @param <K> for keys
     */
    public interface HelperIntegration<K> {

        StatisticsCollector getStats();

        void clearCacheView(K key);

        void clearCacheEntriesOnExceptions(List<K> keys);
    }
}
