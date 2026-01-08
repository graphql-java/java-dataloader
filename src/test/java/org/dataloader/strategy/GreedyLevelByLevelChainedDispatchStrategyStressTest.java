package org.dataloader.strategy;

import org.dataloader.BatchLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class GreedyLevelByLevelChainedDispatchStrategyStressTest {

    private int iterationCount;
    private List<List<String>> dispatchOrder;
    private List<List<String>> queueOrder;
    private List<List<String>> completionOrder;
    private DataLoaderRegistry registry;
    private CountDownLatch bLatch;
    private CountDownLatch gLatch;
    private CountDownLatch aStarted;
    private CountDownLatch gCompleted;
    private CountDownLatch iCompleted;

    /*
    Simulating tree with async conditions

    A
      B (async) completed before G
        E
        F
      C
        G (async) completes last
        H
      D
        I
        J
     */
    @BeforeEach
    public void setup() {
        dispatchOrder = new ArrayList<>();
        queueOrder = new ArrayList<>();
        completionOrder = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            dispatchOrder.add(new ArrayList<>());
            queueOrder.add(new ArrayList<>());
            completionOrder.add(new ArrayList<>());
        }
        addAtIteration(queueOrder, "A");
        bLatch = new CountDownLatch(1);
        gLatch = new CountDownLatch(1);
        aStarted = new CountDownLatch(1);
        gCompleted = new CountDownLatch(1);
        iCompleted = new CountDownLatch(1);
        iterationCount = 1;
        GreedyLevelByLevelChainedDispatchStrategy greedyLevelByLevelChainedDispatchStrategy =
                new GreedyLevelByLevelChainedDispatchStrategy.Builder(Executors.newSingleThreadScheduledExecutor())
                        .setFallbackTimeout(Duration.ofMillis(300)).build();
        greedyLevelByLevelChainedDispatchStrategy.onIteration(() -> iterationCount += 1);
        registry = DataLoaderRegistry.newRegistry()
                .dispatchStrategy(greedyLevelByLevelChainedDispatchStrategy)
                .build();


        // Loaders named after diagram above
        BatchLoader<Integer, Integer> eLoader = keys -> {
            addAtIteration(dispatchOrder, "E");
            return CompletableFuture.completedFuture(keys);
        };
        BatchLoader<Integer, Integer> fLoader = keys -> {
            addAtIteration(dispatchOrder, "F");
            return CompletableFuture.completedFuture(keys);
        };
        BatchLoader<Integer, Integer> hLoader = keys -> {
            addAtIteration(dispatchOrder, "H");
            return CompletableFuture.completedFuture(keys);
        };
        BatchLoader<Integer, Integer> iLoader = keys -> {
            addAtIteration(dispatchOrder, "I");
            return CompletableFuture.completedFuture(keys);
        };
        BatchLoader<Integer, Integer> jLoader = keys -> {
            addAtIteration(dispatchOrder, "J");
            return CompletableFuture.completedFuture(keys);
        };

        BatchLoader<Integer, Integer> gLoader = keys -> {
            addAtIteration(dispatchOrder, "G");
            CompletableFuture<List<Integer>> gFuture = new CompletableFuture<>();
            CompletableFuture.runAsync(() -> {
                try {
                    gLatch.await();
                    gFuture.complete(keys);
                } catch (InterruptedException e) {
                    // do nothing
                }
            });
            return gFuture;
        };

        BatchLoader<Integer, Integer> bLoader = keys -> {
            addAtIteration(dispatchOrder, "B");
            CompletableFuture<List<Integer>> bFuture = new CompletableFuture<>();
            CompletableFuture.runAsync(() -> {
                try {
                    bLatch.await();
                    CompletableFuture<Integer> eResult = registry.<Integer, Integer>getDataLoader("eLoader").load(keys.get(0))
                            .whenComplete((result, error) -> addAtIteration(completionOrder, "E"));
                    addAtIteration(queueOrder, "E");
                    CompletableFuture<Integer> fResult = registry.<Integer, Integer>getDataLoader("fLoader").load(keys.get(0))
                            .whenComplete((result, error) -> addAtIteration(completionOrder, "F"));
                    addAtIteration(queueOrder, "F");
                    eResult.thenCombine(fResult, (eNum, fNum) -> List.of(eNum + fNum))
                            .thenAccept(bFuture::complete);
                } catch (InterruptedException e) {
                    // do nothing
                }
            });
            return bFuture;
        };

        BatchLoader<Integer, Integer> cLoader = keys -> {
            addAtIteration(dispatchOrder, "C");
            CompletableFuture<Integer> gResult = registry.<Integer, Integer>getDataLoader("gLoader").load(keys.get(0))
                    .whenComplete((result, error) -> {
                        addAtIteration(completionOrder, "G");
                        gCompleted.countDown();
                    });
            addAtIteration(queueOrder, "G");
            CompletableFuture<Integer> hResult = registry.<Integer, Integer>getDataLoader("hLoader").load(keys.get(0))
                    .whenComplete((result, error) -> addAtIteration(completionOrder, "H"));
            addAtIteration(queueOrder, "H");

            return gResult.thenCombine(hResult, (gNum, hNum) -> List.of(gNum + hNum));
        };

        BatchLoader<Integer, Integer> dLoader = keys -> {
            addAtIteration(dispatchOrder, "D");
            CompletableFuture<Integer> iResult = registry.<Integer, Integer>getDataLoader("iLoader").load(keys.get(0))
                    .whenComplete((result, error) -> {
                        addAtIteration(completionOrder, "I");
                        iCompleted.countDown();
                    });
            addAtIteration(queueOrder, "I");
            CompletableFuture<Integer> jResult = registry.<Integer, Integer>getDataLoader("jLoader").load(keys.get(0))
                    .whenComplete((result, error) -> addAtIteration(completionOrder, "J"));
            addAtIteration(queueOrder, "J");

            return iResult.thenCombine(jResult, (iNum, jNum) -> List.of(iNum + jNum));
        };

        BatchLoader<Integer, Integer> aLoader = keys -> {
            aStarted.countDown();
            addAtIteration(dispatchOrder, "A");
            CompletableFuture<Integer> bResult = registry.<Integer, Integer>getDataLoader("bLoader").load(keys.get(0))
                    .whenComplete((result, error) -> addAtIteration(completionOrder, "B"));
            addAtIteration(queueOrder, "B");
            CompletableFuture<Integer> cResult = registry.<Integer, Integer>getDataLoader("cLoader").load(keys.get(0))
                    .whenComplete((result, error) -> addAtIteration(completionOrder, "C"));
            addAtIteration(queueOrder, "C");
            CompletableFuture<Integer> dResult = registry.<Integer, Integer>getDataLoader("dLoader").load(keys.get(0))
                    .whenComplete((result, error) -> addAtIteration(completionOrder, "D"));
            addAtIteration(queueOrder, "D");

            return CompletableFuture.allOf(bResult, cResult, dResult).thenApply(unused -> {
               int bNum = bResult.join();
               int cNum = cResult.join();
               int dNum = dResult.join();

               return List.of(bNum + cNum + dNum);
            }).whenComplete((result, error) -> addAtIteration(completionOrder, "A"));
        };

        registry.register("aLoader", DataLoaderFactory.newDataLoader(aLoader));
        registry.register("bLoader", DataLoaderFactory.newDataLoader(bLoader));
        registry.register("cLoader", DataLoaderFactory.newDataLoader(cLoader));
        registry.register("dLoader", DataLoaderFactory.newDataLoader(dLoader));
        registry.register("eLoader", DataLoaderFactory.newDataLoader(eLoader));
        registry.register("fLoader", DataLoaderFactory.newDataLoader(fLoader));
        registry.register("gLoader", DataLoaderFactory.newDataLoader(gLoader));
        registry.register("hLoader", DataLoaderFactory.newDataLoader(hLoader));
        registry.register("iLoader", DataLoaderFactory.newDataLoader(iLoader));
        registry.register("jLoader", DataLoaderFactory.newDataLoader(jLoader));
    }


    /*
      Explanation of assertions.

      G and B are async
      G unlocked once leaf nodes have started

      B unlocked once G completes

      Dispatch order
      Iteration 1: - Due to dataloader order in the registry C is dispatched and H is dispatched greedily
      A, B, C, D H
      Iteration 2:
      G, I, J - E and F are blocked by B as they are async chained
      Iteration 3:
      E, F - B has unlocked and allowed dispatching of E and F

      Queue Order
      Iteration 1: -
      A
      Iteration 2:
      B, C, D, G, H, I, J - All but E and F queued as we get as much work as possible
      Iteration 3
      E, F - B unlocks E and F once async call completes

      Completion Order
      H, J, I, D, G, C, E, F, B, A

      Walk the tree up from roots greedily as calls finish.
      D finishes first as no blocks
      C finishes second as G is async
      B finishes last as well as E and F leafs as they are blocked by async B finishing
     */
    @Test
    void verifyExecutionOrder() throws Exception {
        CompletableFuture<Integer> result = CompletableFuture.supplyAsync(() -> registry.<Integer, Integer>getDataLoader("aLoader").load(1).join(),
                Executors.newSingleThreadExecutor());

        aStarted.await();

        // do not release g until leaf level started
        iCompleted.await();

        // g call finished
        gLatch.countDown();

        // do not release b until leafs completed
        gCompleted.await();

        // b call finished
        bLatch.countDown();

        int resultNum = result.join();

        // 6 leaf nodes added together
        assertThat(resultNum, equalTo(6));

        // clean up padded lists
        dispatchOrder = dispatchOrder.stream().filter(list -> !list.isEmpty()).collect(Collectors.toList());
        queueOrder = queueOrder.stream().filter(list -> !list.isEmpty()).collect(Collectors.toList());
        List<String> flatCompletionOrder = completionOrder.stream().flatMap(List::stream).collect(Collectors.toList());

        // Due to DataLoaders queueing other dataloaders during dispatch more work is done than level by level
        assertThat(dispatchOrder, equalTo(List.of(
                List.of("A", "C", "B", "H", "D"),
                List.of("G", "J", "I"),
                List.of("E", "F")
        )));
        // Greedily queues all known work capable
        assertThat(queueOrder, equalTo(List.of(
                List.of("A"),
                List.of("B", "C", "D", "G", "H", "I", "J"),
                List.of("E", "F")
        )));

        assertThat(completionOrder, equalTo(List.of(
                "H",
                "J",
                "I",
                "D",
                "G",
                "C",
                "E",
                "F",
                "B",
                "A"
        )));
    }

    private void addAtIteration(List<List<String>> aList, String toAdd) {
        aList.get(iterationCount).add(toAdd);
    }
}
