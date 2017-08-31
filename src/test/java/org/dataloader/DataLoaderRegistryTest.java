package org.dataloader;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class DataLoaderRegistryTest {
    final BatchLoader<Object, Object> identityBatchLoader = CompletableFuture::completedFuture;

    @Test
    public void registration_works() throws Exception {
        DataLoader<Object, Object> dlA = new DataLoader<>(identityBatchLoader);
        DataLoader<Object, Object> dlB = new DataLoader<>(identityBatchLoader);
        DataLoader<Object, Object> dlC = new DataLoader<>(identityBatchLoader);

        DataLoaderRegistry registry = new DataLoaderRegistry();

        registry.register(dlA).register(dlB).register(dlC);

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB, dlC)));

        // the same dl twice is one add


        registry = new DataLoaderRegistry();

        registry.register(dlA).register(dlB).register(dlC).register(dlA).register(dlB);

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB, dlC)));


        // and unregister
        registry.unregister(dlC);

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB)));
    }
}