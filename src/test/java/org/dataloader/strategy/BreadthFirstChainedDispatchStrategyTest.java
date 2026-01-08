package org.dataloader.strategy;

import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class BreadthFirstChainedDispatchStrategyTest {

    private ScheduledExecutorService scheduledExecutorService;

    @BeforeEach
    public void setUp() {
        this.scheduledExecutorService = Executors.newScheduledThreadPool(2);
    }

    @AfterEach
    public void cleanUp() {
        this.scheduledExecutorService.shutdownNow();
    }

    @Test
    void singleDepthLoadSucceeds() throws Exception {
        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .register(SimpleLoader.class.getSimpleName(), DataLoaderFactory.newDataLoader(new SimpleLoader()))
                .dispatchStrategy(new BreadthFirstChainedDispatchStrategy.Builder(scheduledExecutorService).build())
                .build();
        DataLoader<Integer, Integer> loader = registry.getDataLoader(SimpleLoader.class.getSimpleName());
        CompletableFuture<Integer> result = loader.load(1);
        assertThat(result.isDone(), equalTo(true));
        assertThat(result.get(), equalTo(1));
    }

    @Test
    void singleDepthLoadSucceedsMultipleTimes() throws Exception {
        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .register(SimpleLoader.class.getSimpleName(), DataLoaderFactory.newDataLoader(new SimpleLoader()))
                .dispatchStrategy(new BreadthFirstChainedDispatchStrategy.Builder(scheduledExecutorService).build())
                .build();
        DataLoader<Integer, Integer> loader = registry.getDataLoader(SimpleLoader.class.getSimpleName());
        CompletableFuture<Integer> result = loader.load(1);
        assertThat(result.isDone(), equalTo(true));
        assertThat(result.get(), equalTo(1));

        // state reset, kick off another load
        CompletableFuture<Integer> result2 = loader.load(1);
        assertThat(result2.isDone(), equalTo(true));
        assertThat(result2.get(), equalTo(1));
    }

    @Test
    void chainedLoaderSucceeds() throws Exception {
        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .dispatchStrategy(new BreadthFirstChainedDispatchStrategy.Builder(scheduledExecutorService).build())
                .build();
        registry.register(SimpleLoader.class.getSimpleName(), DataLoaderFactory.newDataLoader(new SimpleLoader()));
        registry.register(ChainedLoader.class.getSimpleName(), DataLoaderFactory.newDataLoader(new ChainedLoader(registry)));
        DataLoader<Integer, Integer> loader = registry.getDataLoader(ChainedLoader.class.getSimpleName());
        CompletableFuture<Integer> result = loader.load(1);
        assertThat(result.isDone(), equalTo(true));
        assertThat(result.get(), equalTo(1));
    }

    @Test
    void chainedAsyncLoaderSucceeds() {
        CountDownLatch latch = new CountDownLatch(1);
        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .dispatchStrategy(new BreadthFirstChainedDispatchStrategy.Builder(scheduledExecutorService).build())
                .build();
        registry.register(SimpleLoader.class.getSimpleName(), DataLoaderFactory.newDataLoader(new SimpleLoader()));
        registry.register(ChainedAsyncLoader.class.getSimpleName(), DataLoaderFactory.newDataLoader(new ChainedAsyncLoader(registry, latch)));
        DataLoader<Integer, Integer> loader = registry.getDataLoader(ChainedAsyncLoader.class.getSimpleName());
        CompletableFuture<Integer> result = loader.load(1);
        // future not done, fallback triggered
        assertThat(result.isDone(), equalTo(false));
        // allow loader to continue, simulating async behavior
        latch.countDown();

        // blocking wait for fallback dispatch to trigger
        Integer resultInteger = result.join();

        assertThat(resultInteger, equalTo(1));
    }

    @Test
    void dispatchGoesByLevel() throws Exception {
        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .dispatchStrategy(new BreadthFirstChainedDispatchStrategy.Builder(scheduledExecutorService).build())
                .build();
        List<List<Integer>> leafLevelSeenKeys = new ArrayList<>();
        BatchLoader<Integer, Integer> leaf = keys -> {
            leafLevelSeenKeys.add(keys);
            return CompletableFuture.completedFuture(keys);
        };

        List<List<Integer>> secondLevelSeenKeys = new ArrayList<>();
        BatchLoader<Integer, Integer> secondLevel = keys -> {
            secondLevelSeenKeys.add(keys);
            List<CompletableFuture<Integer>> futures = keys.stream().map(key -> registry.<Integer, Integer>getDataLoader("leaf").load(key)).collect(Collectors.toList());
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(unused -> {
                List<Integer> results = new ArrayList<>();
                for (CompletableFuture<Integer> future: futures) {
                    results.add(future.join());
                }
                return results;
            });
        };

        // Call trigger 2 loads on root to secondLevel
        BatchLoader<Integer, Integer> root = keys -> {
            DataLoader<Integer, Integer> second = registry.getDataLoader("secondLevel");
            List<CompletableFuture<Integer>> firstCall =  keys.stream().map(second::load).collect(Collectors.toList());

            // used to verify batching
            List<CompletableFuture<Integer>> secondCall = keys.stream().map(second::load).collect(Collectors.toList());

            // used to verify multiple keys
            List<CompletableFuture<Integer>> thirdCall = keys.stream().map(key -> second.load(key + 1)).collect(Collectors.toList());
            CompletableFuture<Void> firstFinished = CompletableFuture.allOf(firstCall.toArray(new CompletableFuture[0]));
            CompletableFuture<Void> secondFinished = CompletableFuture.allOf(secondCall.toArray(new CompletableFuture[0]));
            CompletableFuture<Void> thirdFinished = CompletableFuture.allOf(thirdCall.toArray(new CompletableFuture[0]));
            CompletableFuture<Void> allFinished = CompletableFuture.allOf(firstFinished, secondFinished, thirdFinished);
            return allFinished.thenApply(unused -> {
                List<Integer> result = new ArrayList<>();
                for (int i = 0; i < firstCall.size(); i++) {
                    int firstResult = firstCall.get(i).join();
                    int secondResult = secondCall.get(i).join();
                    int thirdResult = thirdCall.get(i).join();
                    result.add(firstResult + secondResult + thirdResult);
                }
                return result;
            });
        };

        registry.register("root", DataLoaderFactory.newDataLoader(root));
        registry.register("secondLevel", DataLoaderFactory.newDataLoader(secondLevel));
        registry.register("leaf", DataLoaderFactory.newDataLoader(leaf));

        CompletableFuture<Integer> result = registry.<Integer, Integer>getDataLoader("root").load(1);

        assertThat(result.isDone(), equalTo(true));
        // 1 + 1 + 2 (first, second, third call)
        assertThat(result.get(), equalTo(4));

        // verify levels only called once even though multiple loads called with different arguments (level by level)
        assertThat(secondLevelSeenKeys.size(), equalTo(1));
        assertThat(leafLevelSeenKeys.size(), equalTo(1));

        // verify keys sent to levels are proper
        assertThat(secondLevelSeenKeys.get(0), equalTo(List.of(1, 2)));
        assertThat(leafLevelSeenKeys.get(0), equalTo(List.of(1, 2)));
    }

    private static final class ChainedAsyncLoader implements BatchLoader<Integer, Integer> {
        private final DataLoaderRegistry dataLoaderRegistry;
        private final CountDownLatch latch;
        public ChainedAsyncLoader(DataLoaderRegistry dataLoaderRegistry, CountDownLatch latch) {
            this.dataLoaderRegistry = dataLoaderRegistry;
            this.latch = latch;
        }

        @Override
        public CompletionStage<List<Integer>> load(List<Integer> keys) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                List<CompletableFuture<Integer>> futures = keys.stream().map(key ->
                        dataLoaderRegistry.<Integer, Integer>getDataLoader(SimpleLoader.class.getSimpleName()).load(key)
                ).collect(Collectors.toList());
                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(unused -> {
                    List<Integer> result = new ArrayList<>();
                    for (CompletableFuture<Integer> future: futures) {
                        result.add(future.join());
                    }
                    return result;
                }).join();
            });
        }
    }

    private static final class ChainedLoader implements BatchLoader<Integer, Integer> {

        private final DataLoaderRegistry dataLoaderRegistry;
        public ChainedLoader(DataLoaderRegistry dataLoaderRegistry) {
            this.dataLoaderRegistry = dataLoaderRegistry;
        }

        @Override
        public CompletionStage<List<Integer>> load(List<Integer> keys) {
            List<CompletableFuture<Integer>> futures = keys.stream().map(key ->
                    dataLoaderRegistry.<Integer, Integer>getDataLoader(SimpleLoader.class.getSimpleName()).load(key)
            ).collect(Collectors.toList());
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(unused -> {
                List<Integer> result = new ArrayList<>();
                for (CompletableFuture<Integer> future: futures) {
                    result.add(future.join());
                }
                return result;
            });
        }
    }

    private static final class SimpleLoader implements BatchLoader<Integer, Integer> {

        @Override
        public CompletionStage<List<Integer>> load(List<Integer> keys) {
            return CompletableFuture.completedFuture(keys);
        }
    }
}
