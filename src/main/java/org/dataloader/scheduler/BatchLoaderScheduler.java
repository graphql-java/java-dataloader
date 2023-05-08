package org.dataloader.scheduler;

import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.MappedBatchLoader;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * By default, when  {@link DataLoader#dispatch()} is called the {@link BatchLoader} / {@link MappedBatchLoader} function will be invoked
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
     * This is called to schedule a {@link BatchLoader} call.
     *
     * @param scheduledCall the callback that needs to be invoked to allow the {@link BatchLoader} to proceed.
     * @param keys          this is the list of keys that will be passed to the {@link BatchLoader}.
     *                      This is provided only for informative reasons and you cant change the keys that are used
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
     *                      This is provided only for informative reasons and you cant change the keys that are used
     * @param environment   this is the {@link BatchLoaderEnvironment} in place,
     *                      which can be null if it's a simple {@link MappedBatchLoader} call
     * @param <K>           the key type
     * @param <V>           the value type
     *
     * @return a promise to the values that come from the {@link BatchLoader}
     */
    <K, V> CompletionStage<Map<K, V>> scheduleMappedBatchLoader(ScheduledMappedBatchLoaderCall<K, V> scheduledCall, List<K> keys, BatchLoaderEnvironment environment);
}
