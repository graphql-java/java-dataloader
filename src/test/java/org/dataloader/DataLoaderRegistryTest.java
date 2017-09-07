package org.dataloader;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

public class DataLoaderRegistryTest {
    final BatchLoader<Object, Object> identityBatchLoader = CompletableFuture::completedFuture;

    @Test
    public void registration_works() throws Exception {
        DataLoader<Object, Object> dlA = new DataLoader<>(identityBatchLoader);
        DataLoader<Object, Object> dlB = new DataLoader<>(identityBatchLoader);
        DataLoader<Object, Object> dlC = new DataLoader<>(identityBatchLoader);

        DataLoaderRegistry registry = new DataLoaderRegistry();

        registry.register("a", dlA).register("b", dlB).register("c", dlC);

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB, dlC)));

        // the same dl twice is one add


        registry = new DataLoaderRegistry();

        registry.register("a", dlA).register("b", dlB).register("c", dlC).register("b", dlB);

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB, dlC)));


        // and unregister
        registry.unregister("c");

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB)));

        // look up by name works
        DataLoader<String, String> readDL = registry.getDataLoader("a");
        assertThat(readDL, sameInstance(dlA));

        assertThat(registry.getKeys(), hasItems("a", "b"));
    }

    @Test
    public void registries_can_be_combined() throws Exception {

        DataLoader<Object, Object> dlA = new DataLoader<>(identityBatchLoader);
        DataLoader<Object, Object> dlB = new DataLoader<>(identityBatchLoader);
        DataLoader<Object, Object> dlC = new DataLoader<>(identityBatchLoader);
        DataLoader<Object, Object> dlD = new DataLoader<>(identityBatchLoader);

        DataLoaderRegistry registry1 = new DataLoaderRegistry();

        registry1.register("a", dlA).register("b", dlB);

        DataLoaderRegistry registry2 = new DataLoaderRegistry();

        registry2.register("c", dlC).register("d", dlD);

        DataLoaderRegistry combinedRegistry = registry1.combine(registry2);

        assertThat(combinedRegistry.getKeys(), hasItems("a", "b", "c", "d"));
        assertThat(combinedRegistry.getDataLoaders(), hasItems(dlA, dlB, dlC, dlD));
    }
}