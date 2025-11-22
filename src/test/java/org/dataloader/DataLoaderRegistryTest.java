package org.dataloader;

import org.dataloader.errors.StrictModeRegistryException;
import org.dataloader.stats.SimpleStatisticsCollector;
import org.dataloader.stats.Statistics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.asList;
import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DataLoaderRegistryTest {
    final BatchLoader<Object, Object> identityBatchLoader = CompletableFuture::completedFuture;

    @Test
    public void registration_works() {
        DataLoader<Object, Object> dlA = newDataLoader("a", identityBatchLoader);
        DataLoader<Object, Object> dlB = newDataLoader("b", identityBatchLoader);
        DataLoader<Object, Object> dlC = newDataLoader("c", identityBatchLoader);
        DataLoader<Object, Object> dlUnnamed = newDataLoader(identityBatchLoader);

        DataLoaderRegistry registry = new DataLoaderRegistry();

        registry.register("a", dlA).register("b", dlB).register("c", dlC);

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB, dlC)));

        // the same dl twice is one add


        registry = new DataLoaderRegistry();

        registry.register("a", dlA).register("b", dlB).register("c", dlC).register("b", dlB);

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB, dlC)));


        // and unregister (fluently)
        DataLoaderRegistry dlR = registry.unregister("c");
        assertThat(dlR, equalTo(registry));

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB)));

        // look up by name works
        DataLoader<String, String> readDL = registry.getDataLoader("a");
        assertThat(readDL, sameInstance(dlA));

        assertThat(registry.getKeys(), hasItems("a", "b"));


        // named registry
        registry = new DataLoaderRegistry();
        registry.register(dlA);
        assertThat(registry.getDataLoaders(), equalTo(List.of(dlA)));

        try {
            registry.register(dlUnnamed);
            Assertions.fail("Should have thrown an exception");
        } catch (NullPointerException ignored) {
        }
    }

    @Test
    public void registries_can_be_combined() {

        DataLoader<Object, Object> dlA = newDataLoader("a", identityBatchLoader);
        DataLoader<Object, Object> dlB = newDataLoader("b", identityBatchLoader);
        DataLoader<Object, Object> dlC = newDataLoader("c", identityBatchLoader);
        DataLoader<Object, Object> dlD = newDataLoader("d", identityBatchLoader);

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
                DataLoaderOptions.newOptions().setStatisticsCollector(SimpleStatisticsCollector::new).build()
        );
        DataLoader<Object, Object> dlB = newDataLoader(identityBatchLoader,
                DataLoaderOptions.newOptions().setStatisticsCollector(SimpleStatisticsCollector::new).build()
        );
        DataLoader<Object, Object> dlC = newDataLoader(identityBatchLoader,
                DataLoaderOptions.newOptions().setStatisticsCollector(SimpleStatisticsCollector::new).build()
        );

        registry.register("a", dlA).register("b", dlB).register("c", dlC);

        dlA = registry.getDataLoader("a");
        dlB = registry.getDataLoader("b");
        dlC = registry.getDataLoader("b");

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

        DataLoader<Object, Object> dlA = newDataLoader("a", identityBatchLoader);
        DataLoader<Object, Object> registered = registry.computeIfAbsent("a", (key) -> dlA);

        assertThat(registered, equalTo(dlA));
        assertThat(registry.getKeys(), hasItems("a"));
        assertThat(registry.getDataLoaders(), hasItems(dlA));
    }

    @Test
    public void computeIfAbsent_returns_an_existing_data_loader_if_there_was_a_value_at_key() {

        DataLoaderRegistry registry = new DataLoaderRegistry();

        DataLoader<Object, Object> dlA = newDataLoader("a", identityBatchLoader);
        registry.computeIfAbsent("a", (key) -> dlA);

        // register again at same key
        DataLoader<Object, Object> dlA2 = newDataLoader("a", identityBatchLoader);
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

        dlA = registry.registerAndGet("a", dlA);
        dlB = registry.registerAndGet("b", dlB);

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

    @Test
    public void strictMode_works() {

        DataLoader<Object, Object> dlA = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlB = newDataLoader(identityBatchLoader);

        assertThrows(StrictModeRegistryException.class, () -> {
            DataLoaderRegistry.newRegistry()
                .strictMode(true)
                .register("a", dlA)
                .register("a", dlB)
                .build();
        });
        assertThrows(StrictModeRegistryException.class, () -> {
            DataLoaderRegistry.newRegistry()
                .strictMode(true)
                .register("a", dlA)
                .registerAll(DataLoaderRegistry.newRegistry()
                    .register("a", dlB)
                    .build())
                .build();
        });

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
            .strictMode(true)
            .build();
        registry.register("a", dlA);

        assertThrows(StrictModeRegistryException.class, () -> {
            registry.register("a", dlB);
        });
        assertThrows(StrictModeRegistryException.class, () -> {
            registry.register(newDataLoader("a", identityBatchLoader));
        });
        assertThrows(StrictModeRegistryException.class, () -> {
            registry.registerAndGet("a", dlB);
        });
        assertThrows(StrictModeRegistryException.class, () -> {
            registry.combine(DataLoaderRegistry.newRegistry()
                .register("a", dlB)
                .build());
        });
    }
}
