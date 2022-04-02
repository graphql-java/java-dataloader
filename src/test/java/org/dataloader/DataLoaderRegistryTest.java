package org.dataloader;

import org.dataloader.stats.SimpleStatisticsCollector;
import org.dataloader.stats.Statistics;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

public class DataLoaderRegistryTest {
    final BatchLoader<Object, Object> identityBatchLoader = CompletableFuture::completedFuture;
    final BatchLoader<Integer, Integer> incrementalBatchLoader =
        v -> CompletableFuture.supplyAsync(() -> v.stream().map(i -> ++i).collect(Collectors.toList()));

    @Test
    public void registration_works() {
        DataLoader<Object, Object> dlA = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlB = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlC = newDataLoader(identityBatchLoader);

        DataLoaderRegistry registry = new DataLoaderRegistry();

        registry.register("a", dlA).register("b", dlB).register("c", dlC);

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB, dlC)));

        // the same dl twice is one add


        registry = new DataLoaderRegistry();

        registry.register("a", dlA).register("b", dlB).register("c", dlC).register("b", dlB);

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB, dlC)));


        // and unregister (fluently)
        DataLoaderRegistry dlR = registry.unregister("c");
        assertThat(dlR,equalTo(registry));

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB)));

        // look up by name works
        DataLoader<String, String> readDL = registry.getDataLoader("a");
        assertThat(readDL, sameInstance(dlA));

        assertThat(registry.getKeys(), hasItems("a", "b"));
    }

    @Test
    public void registries_can_be_combined() {

        DataLoader<Object, Object> dlA = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlB = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlC = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlD = newDataLoader(identityBatchLoader);

        DataLoaderRegistry registry1 = new DataLoaderRegistry();

        registry1.register("a", dlA).register("b", dlB);

        DataLoaderRegistry registry2 = new DataLoaderRegistry();

        registry2.register("c", dlC).register("d", dlD);

        DataLoaderRegistry combinedRegistry = registry1.combine(registry2);

        assertThat(combinedRegistry.getKeys(), hasItems("a", "b", "c", "d"));
        assertThat(combinedRegistry.getDataLoaders(), hasItems(dlA, dlB, dlC, dlD));
    }

    @Test
    public void stats_can_be_collected() {

        DataLoaderRegistry registry = new DataLoaderRegistry();

        DataLoader<Object, Object> dlA = newDataLoader(identityBatchLoader,
                DataLoaderOptions.newOptions().setStatisticsCollector(SimpleStatisticsCollector::new)
        );
        DataLoader<Object, Object> dlB = newDataLoader(identityBatchLoader,
                DataLoaderOptions.newOptions().setStatisticsCollector(SimpleStatisticsCollector::new)
        );
        DataLoader<Object, Object> dlC = newDataLoader(identityBatchLoader,
                DataLoaderOptions.newOptions().setStatisticsCollector(SimpleStatisticsCollector::new)
        );

        registry.register("a", dlA).register("b", dlB).register("c", dlC);

        dlA.load("X");
        dlB.load("Y");
        dlC.load("Z");

        registry.dispatchAll();

        dlA.load("X");
        dlB.load("Y");
        dlC.load("Z");

        registry.dispatchAll();

        Statistics statistics = registry.getStatistics();

        assertThat(statistics.getLoadCount(), equalTo(6L));
        assertThat(statistics.getBatchLoadCount(), equalTo(3L));
        assertThat(statistics.getCacheHitCount(), equalTo(3L));
        assertThat(statistics.getLoadErrorCount(), equalTo(0L));
        assertThat(statistics.getBatchLoadExceptionCount(), equalTo(0L));
    }

    @Test
    public void computeIfAbsent_creates_a_data_loader_if_there_was_no_value_at_key() {

        DataLoaderRegistry registry = new DataLoaderRegistry();

        DataLoader<Object, Object> dlA = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> registered = registry.computeIfAbsent("a", (key) -> dlA);

        assertThat(registered, equalTo(dlA));
        assertThat(registry.getKeys(), hasItems("a"));
        assertThat(registry.getDataLoaders(), hasItems(dlA));
    }

    @Test
    public void computeIfAbsent_returns_an_existing_data_loader_if_there_was_a_value_at_key() {

        DataLoaderRegistry registry = new DataLoaderRegistry();

        DataLoader<Object, Object> dlA = newDataLoader(identityBatchLoader);
        registry.computeIfAbsent("a", (key) -> dlA);

        // register again at same key
        DataLoader<Object, Object> dlA2 = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> registered = registry.computeIfAbsent("a", (key) -> dlA2);

        assertThat(registered, equalTo(dlA));
        assertThat(registry.getKeys(), hasItems("a"));
        assertThat(registry.getDataLoaders(), hasItems(dlA));
    }

    @Test
    public void dispatch_counts_are_maintained() {

        DataLoaderRegistry registry = new DataLoaderRegistry();

        DataLoader<Object, Object> dlA = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlB = newDataLoader(identityBatchLoader);

        registry.register("a", dlA);
        registry.register("b", dlB);

        dlA.load("av1");
        dlA.load("av2");
        dlB.load("bv1");
        dlB.load("bv2");

        int dispatchDepth = registry.dispatchDepth();
        assertThat(dispatchDepth, equalTo(4));

        int dispatchedCount = registry.dispatchAllWithCount();
        dispatchDepth = registry.dispatchDepth();
        assertThat(dispatchedCount, equalTo(4));
        assertThat(dispatchDepth, equalTo(0));
    }

    @Test
    public void composed_dispatch_counts_are_maintained() {

        DataLoaderRegistry registry = new DataLoaderRegistry();

        DataLoader<Integer, Integer> dlA = newDataLoader(incrementalBatchLoader);
        DataLoader<Integer, Integer> dlB = newDataLoader(incrementalBatchLoader);
        DataLoader<Integer, Integer> dlC = newDataLoader(incrementalBatchLoader);

        registry.register("a", dlA).register("b", dlB).register("c", dlC);
        
        CompletableFuture<Integer> test1 = dlA.load(100)
            .thenCompose(dlB::load)
            .thenCompose(dlC::load);
        CompletableFuture<Integer> test2 = dlC.load(200)
            .thenCompose(dlB::load)
            .thenCompose(dlA::load);

        assertThat("Initially dispatching only top level load calls", registry.dispatchDepth(), equalTo(2));

        CompletableFuture<Integer> dispatchedKeys1 = registry.dispatch();

        assertThat("Total count of dispatched keys in first iteration", dispatchedKeys1.join(), equalTo(6));
        assertThat("Zero dispatch depth after first iteration done", registry.dispatchDepth(), equalTo(0));

        CompletableFuture<Integer> test3 = dlA.load(100)
            .thenCompose(dlB::load)
            .thenCompose(dlC::load);
        CompletableFuture<Integer> test4 = dlC.load(200)
            .thenCompose(dlB::load)
            .thenCompose(dlA::load);

        assertThat("Not dispatching the same keys twice", registry.dispatchDepth(), equalTo(0));

        CompletableFuture<Integer> dispatchedKeys2 = registry.dispatch();

        assertThat("Zero dispatched keys in second iteration", dispatchedKeys2.join(), equalTo(0));
        assertThat("Zero dispatch depth after second iteration done", registry.dispatchDepth(), equalTo(0));

        assertThat(test1.join(), equalTo(103));
        assertThat(test2.join(), equalTo(203));
        assertThat(test3.join(), equalTo(103));
        assertThat(test4.join(), equalTo(203));
    }

    @Test
    public void builder_works() {
        DataLoader<Object, Object> dlA = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlB = newDataLoader(identityBatchLoader);

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .register("a", dlA)
                .register("b", dlB)
                .build();

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB)));
        assertThat(registry.getDataLoader("a"), equalTo(dlA));


        DataLoader<Object, Object> dlC = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlD = newDataLoader(identityBatchLoader);

        DataLoaderRegistry registry2 = DataLoaderRegistry.newRegistry()
                .register("c", dlC)
                .register("d", dlD)
                .build();


        registry = DataLoaderRegistry.newRegistry()
                .register("a", dlA)
                .register("b", dlB)
                .registerAll(registry2)
                .build();

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB, dlC, dlD)));
        assertThat(registry.getDataLoader("a"), equalTo(dlA));
        assertThat(registry.getDataLoader("c"), equalTo(dlC));

    }
}
