package org.dataloader.scheduler;

import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.awaitility.Awaitility.await;
import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.dataloader.DataLoaderFactory.newMappedDataLoader;
import static org.dataloader.fixtures.TestKit.keysAsMapOfValues;
import static org.dataloader.fixtures.TestKit.keysAsMapOfValuesWithContext;
import static org.dataloader.fixtures.TestKit.keysAsValues;
import static org.dataloader.fixtures.TestKit.keysAsValuesWithContext;
import static org.dataloader.fixtures.TestKit.snooze;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class BatchLoaderSchedulerTest {

    BatchLoaderScheduler immediateScheduling = new BatchLoaderScheduler() {

        @Override
        public <K, V> CompletionStage<List<V>> scheduleBatchLoader(ScheduledBatchLoaderCall<V> scheduledCall, List<K> keys, BatchLoaderEnvironment environment) {
            return scheduledCall.invoke();
        }

        @Override
        public <K, V> CompletionStage<Map<K, V>> scheduleMappedBatchLoader(ScheduledMappedBatchLoaderCall<K, V> scheduledCall, List<K> keys, BatchLoaderEnvironment environment) {
            return scheduledCall.invoke();
        }

        @Override
        public <K> void scheduleBatchPublisher(ScheduledBatchPublisherCall scheduledCall, List<K> keys, BatchLoaderEnvironment environment) {
            scheduledCall.invoke();
        }
    };

    private BatchLoaderScheduler delayedScheduling(int ms) {
        return new BatchLoaderScheduler() {

            @Override
            public <K, V> CompletionStage<List<V>> scheduleBatchLoader(ScheduledBatchLoaderCall<V> scheduledCall, List<K> keys, BatchLoaderEnvironment environment) {
                return CompletableFuture.supplyAsync(() -> {
                    snooze(ms);
                    return scheduledCall.invoke();
                }).thenCompose(Function.identity());
            }

            @Override
            public <K, V> CompletionStage<Map<K, V>> scheduleMappedBatchLoader(ScheduledMappedBatchLoaderCall<K, V> scheduledCall, List<K> keys, BatchLoaderEnvironment environment) {
                return CompletableFuture.supplyAsync(() -> {
                    snooze(ms);
                    return scheduledCall.invoke();
                }).thenCompose(Function.identity());
            }

            @Override
            public <K> void scheduleBatchPublisher(ScheduledBatchPublisherCall scheduledCall, List<K> keys, BatchLoaderEnvironment environment) {
                snooze(ms);
                scheduledCall.invoke();
            }
        };
    }

    private static void commonSetupAndSimpleAsserts(DataLoader<Integer, Integer> identityLoader) {
        CompletableFuture<Integer> future1 = identityLoader.load(1);
        CompletableFuture<Integer> future2 = identityLoader.load(2);

        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.join(), equalTo(1));
        assertThat(future2.join(), equalTo(2));
    }

    @Test
    public void can_allow_a_simple_scheduler() {
        DataLoaderOptions options = DataLoaderOptions.newOptions().withBatchLoaderScheduler(immediateScheduling);

        DataLoader<Integer, Integer> identityLoader = newDataLoader(keysAsValues(), options);

        commonSetupAndSimpleAsserts(identityLoader);
    }

    @Test
    public void can_allow_a_simple_scheduler_with_context() {
        DataLoaderOptions options = DataLoaderOptions.newOptions().withBatchLoaderScheduler(immediateScheduling);

        DataLoader<Integer, Integer> identityLoader = newDataLoader(keysAsValuesWithContext(), options);

        commonSetupAndSimpleAsserts(identityLoader);
    }

    @Test
    public void can_allow_a_simple_scheduler_with_mapped_batch_load() {
        DataLoaderOptions options = DataLoaderOptions.newOptions().withBatchLoaderScheduler(immediateScheduling);

        DataLoader<Integer, Integer> identityLoader = newMappedDataLoader(keysAsMapOfValues(), options);

        commonSetupAndSimpleAsserts(identityLoader);
    }

    @Test
    public void can_allow_a_simple_scheduler_with_mapped_batch_load_with_context() {
        DataLoaderOptions options = DataLoaderOptions.newOptions().withBatchLoaderScheduler(immediateScheduling);

        DataLoader<Integer, Integer> identityLoader = newMappedDataLoader(keysAsMapOfValuesWithContext(), options);

        commonSetupAndSimpleAsserts(identityLoader);
    }

    @Test
    public void can_allow_an_async_scheduler() {
        DataLoaderOptions options = DataLoaderOptions.newOptions().withBatchLoaderScheduler(delayedScheduling(50));

        DataLoader<Integer, Integer> identityLoader = newDataLoader(keysAsValues(), options);

        commonSetupAndSimpleAsserts(identityLoader);
    }


    @Test
    public void can_allow_a_funky_scheduler() {
        AtomicBoolean releaseTheHounds = new AtomicBoolean();
        BatchLoaderScheduler funkyScheduler = new BatchLoaderScheduler() {
            @Override
            public <K, V> CompletionStage<List<V>> scheduleBatchLoader(ScheduledBatchLoaderCall<V> scheduledCall, List<K> keys, BatchLoaderEnvironment environment) {
                return CompletableFuture.supplyAsync(() -> {
                    while (!releaseTheHounds.get()) {
                        snooze(10);
                    }
                    return scheduledCall.invoke();
                }).thenCompose(Function.identity());
            }

            @Override
            public <K, V> CompletionStage<Map<K, V>> scheduleMappedBatchLoader(ScheduledMappedBatchLoaderCall<K, V> scheduledCall, List<K> keys, BatchLoaderEnvironment environment) {
                return CompletableFuture.supplyAsync(() -> {
                    while (!releaseTheHounds.get()) {
                        snooze(10);
                    }
                    return scheduledCall.invoke();
                }).thenCompose(Function.identity());
            }

            @Override
            public <K> void scheduleBatchPublisher(ScheduledBatchPublisherCall scheduledCall, List<K> keys, BatchLoaderEnvironment environment) {
                CompletableFuture.supplyAsync(() -> {
                    snooze(10);
                    scheduledCall.invoke();
                    return null;
                });
            }
        };
        DataLoaderOptions options = DataLoaderOptions.newOptions().withBatchLoaderScheduler(funkyScheduler);

        DataLoader<Integer, Integer> identityLoader = newDataLoader(keysAsValues(), options);

        CompletableFuture<Integer> future1 = identityLoader.load(1);
        CompletableFuture<Integer> future2 = identityLoader.load(2);

        identityLoader.dispatch();

        // we can spin around for a while - nothing will happen until we release the hounds
        for (int i = 0; i < 5; i++) {
            assertThat(future1.isDone(), equalTo(false));
            assertThat(future2.isDone(), equalTo(false));
            snooze(50);
        }

        releaseTheHounds.set(true);

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.join(), equalTo(1));
        assertThat(future2.join(), equalTo(2));
    }


}
