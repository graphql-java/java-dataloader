package org.dataloader.scheduler;

import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.BatchPublisher;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.MappedBatchLoader;
import org.dataloader.MappedBatchPublisher;
import org.dataloader.impl.CompletableFutureKit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * By default, when  {@link DataLoader#dispatch()} is called, the {@link BatchLoader} / {@link MappedBatchLoader} function will be invoked
 * immediately.  However, you can provide your own {@link BatchLoaderScheduler} that allows this call to be done some time into
 * the future.  You will be passed a callback ({@link ScheduledBatchLoaderCall} / {@link ScheduledMappedBatchLoaderCall} and you are expected
 * to eventually call this callback method to make the batch loading happen.
 * <p>
 * Note: Because there is a {@link DataLoaderOptions#maxBatchSize()} it is possible for this scheduling to happen N times for a given {@link DataLoader#dispatch()}
 * call.  The total set of keys will be sliced into batches themselves and then the {@link BatchLoaderScheduler} will be called for
 * each batch of keys.  Do not assume that a single call to {@link DataLoader#dispatch()} results in a single call to {@link BatchLoaderScheduler}.
 */
public interface BatchLoaderScheduler {


    /**
     * This represents a callback that will invoke a {@link BatchLoader} function under the covers
     *
     * @param <V> the value type
     */
    interface ScheduledBatchLoaderCall<V> {

        CompletionStage<List<V>> invoke();
    }

    /**
     * This represents a callback that will invoke a {@link MappedBatchLoader} function under the covers
     *
     * @param <K> the key type
     * @param <V> the value type
     */
    interface ScheduledMappedBatchLoaderCall<K, V> {

        CompletionStage<Map<K, V>> invoke();
    }

    /**
     * This represents a callback that will invoke a {@link BatchPublisher} or {@link MappedBatchPublisher} function under the covers
     */
    interface ScheduledBatchPublisherCall {

        void invoke();
    }

    /**
     * This is called to schedule a {@link BatchLoader} call.
     *
     * @param scheduledCall the callback that needs to be invoked to allow the {@link BatchLoader} to proceed.
     * @param keys          this is the list of keys that will be passed to the {@link BatchLoader}.
     *                      This is provided only for informative reasons, and you can't change the keys that are used
     * @param environment   this is the {@link BatchLoaderEnvironment} in place,
     *                      which can be null if it's a simple {@link BatchLoader} call
     * @param <K>           the key type
     * @param <V>           the value type
     *
     * @return a promise to the values that come from the {@link BatchLoader}
     */
    <K, V> CompletionStage<List<V>> scheduleBatchLoader(ScheduledBatchLoaderCall<V> scheduledCall, List<K> keys, BatchLoaderEnvironment environment);

    /**
     * This is called to schedule a {@link MappedBatchLoader} call.
     *
     * @param scheduledCall the callback that needs to be invoked to allow the {@link MappedBatchLoader} to proceed.
     * @param keys          this is the list of keys that will be passed to the {@link MappedBatchLoader}.
     *                      This is provided only for informative reasons and, you can't change the keys that are used
     * @param environment   this is the {@link BatchLoaderEnvironment} in place,
     *                      which can be null if it's a simple {@link MappedBatchLoader} call
     * @param <K>           the key type
     * @param <V>           the value type
     *
     * @return a promise to the values that come from the {@link BatchLoader}
     */
    <K, V> CompletionStage<Map<K, V>> scheduleMappedBatchLoader(ScheduledMappedBatchLoaderCall<K, V> scheduledCall, List<K> keys, BatchLoaderEnvironment environment);

    /**
     * This is called to schedule a {@link BatchPublisher} call.
     *
     * @param scheduledCall the callback that needs to be invoked to allow the {@link BatchPublisher} to proceed.
     * @param keys          this is the list of keys that will be passed to the {@link BatchPublisher}.
     *                      This is provided only for informative reasons and, you can't change the keys that are used
     * @param environment   this is the {@link BatchLoaderEnvironment} in place,
     *                      which can be null if it's a simple {@link BatchPublisher} call
     * @param <K>           the key type
     */
    <K> void scheduleBatchPublisher(ScheduledBatchPublisherCall scheduledCall, List<K> keys, BatchLoaderEnvironment environment);

    /**
     * This is called to schedule the "completion" of the {@link java.util.concurrent.CompletableFuture}s in the {@link org.dataloader.DataLoader} that map
     * to values that have come back from the batch loader call.
     * <p>
     * You might want to schedule the completion of values on another thread if there following chained work such as :
     * <pre>
     * {@code
     *   var cfStage1 = dataLoader.load(key);
     *   var cfStage2 = cfStage1.thenApply(v -> doSomethingSlow(v));
     *   //...
     *   var dispatchCF = dataLoader.dispatch();
     * }
     * </pre>
     * <p>
     * In the above example the `.doSomethingSlow(v)` call will happen inside the completion
     * of the dataloader code path when it tries to complete `cfStage1` since {@link java.util.concurrent.CompletableFuture}
     * by design runs chained dependent methods eagerly.
     * <p>
     * Perhaps you want this tp happen more asynchronously so that the `.dispatch()` returns more
     * quickly and is not bound to the `.doSomethingSlow(v)` call.
     * <p>
     * By default, the dispatch completion is done on the current thread in a synchronous manner, which will include
     * any extra {@link java.util.concurrent.CompletableFuture} dependent chained methods.
     *
     * @param completeValuesRunnable this is the runnable that the {@link DataLoader} engine code needs to be run
     * @param keys          this is the list of keys that will be passed to the {@link BatchPublisher}.
     *                      This is provided only for informative reasons and, you can't change the keys that are used
     * @param environment   this is the {@link BatchLoaderEnvironment} in place,
     *
     * @return a {@link CompletionStage} representing this work is being scheduled
     */
    default <K>  CompletionStage<?> scheduleCompletion(Runnable completeValuesRunnable, List<K> keys, BatchLoaderEnvironment environment) {
        return CompletableFutureKit.run(completeValuesRunnable);
    }

}
