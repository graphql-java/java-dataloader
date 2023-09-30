package org.dataloader;

import org.dataloader.registries.DispatchPredicate;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.asList;
import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.dataloader.fixtures.TestKit.asSet;
import static org.dataloader.registries.DispatchPredicate.DISPATCH_NEVER;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class DataLoaderRegistryPredicateTest {
    final BatchLoader<Object, Object> identityBatchLoader = CompletableFuture::completedFuture;

    static class CountingDispatchPredicate implements DispatchPredicate {
        int count = 0;
        int max = 0;

        public CountingDispatchPredicate(int max) {
            this.max = max;
        }

        @Override
        public boolean test(String dataLoaderKey, DataLoader<?, ?> dataLoader) {
            boolean shouldFire = count >= max;
            count++;
            return shouldFire;
        }
    }

    @Test
    public void predicate_registration_works() {
        DataLoader<Object, Object> dlA = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlB = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlC = newDataLoader(identityBatchLoader);

        DispatchPredicate predicateA = new CountingDispatchPredicate(1);
        DispatchPredicate predicateB = new CountingDispatchPredicate(2);
        DispatchPredicate predicateC = new CountingDispatchPredicate(3);

        DispatchPredicate predicateOverAll = new CountingDispatchPredicate(10);

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .register("a", dlA, predicateA)
                .register("b", dlB, predicateB)
                .register("c", dlC, predicateC)
                .dispatchPredicate(predicateOverAll)
                .build();

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB, dlC)));
        assertThat(registry.getDataLoadersMap().keySet(), equalTo(asSet("a", "b", "c")));
        assertThat(asSet(registry.getDataLoadersMap().values()), equalTo(asSet(dlA, dlB, dlC)));
        assertThat(registry.getDispatchPredicate(), equalTo(predicateOverAll));
        assertThat(asSet(registry.getDataLoaderPredicates().values()), equalTo(asSet(predicateA, predicateB, predicateC)));

        // and unregister (fluently)
        DataLoaderRegistry dlR = registry.unregister("c");
        assertThat(dlR, equalTo(registry));

        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB)));
        assertThat(registry.getDispatchPredicate(), equalTo(predicateOverAll));
        assertThat(asSet(registry.getDataLoaderPredicates().values()), equalTo(asSet(predicateA, predicateB)));

        // direct on the registry works
        registry.register("c", dlC, predicateC);
        assertThat(registry.getDataLoaders(), equalTo(asList(dlA, dlB, dlC)));
        assertThat(registry.getDispatchPredicate(), equalTo(predicateOverAll));
        assertThat(asSet(registry.getDataLoaderPredicates().values()), equalTo(asSet(predicateA, predicateB, predicateC)));

    }

    @Test
    public void predicate_firing_works() {
        DataLoader<Object, Object> dlA = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlB = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlC = newDataLoader(identityBatchLoader);

        DispatchPredicate predicateA = new CountingDispatchPredicate(1);
        DispatchPredicate predicateB = new CountingDispatchPredicate(2);
        DispatchPredicate predicateC = new CountingDispatchPredicate(3);

        DispatchPredicate predicateOverAll = new CountingDispatchPredicate(10);

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .register("a", dlA, predicateA)
                .register("b", dlB, predicateB)
                .register("c", dlC, predicateC)
                .dispatchPredicate(predicateOverAll)
                .build();


        CompletableFuture<Object> cfA = dlA.load("A");
        CompletableFuture<Object> cfB = dlB.load("B");
        CompletableFuture<Object> cfC = dlC.load("C");

        int count = registry.dispatchAllWithCount(); // first firing
        // none should fire
        assertThat(count, equalTo(0));
        assertThat(cfA.isDone(), equalTo(false));
        assertThat(cfB.isDone(), equalTo(false));
        assertThat(cfC.isDone(), equalTo(false));

        count = registry.dispatchAllWithCount(); // second firing
        // one should fire
        assertThat(count, equalTo(1));
        assertThat(cfA.isDone(), equalTo(true));
        assertThat(cfA.join(), equalTo("A"));

        assertThat(cfB.isDone(), equalTo(false));
        assertThat(cfC.isDone(), equalTo(false));

        count = registry.dispatchAllWithCount(); // third firing
        assertThat(count, equalTo(1));
        assertThat(cfA.isDone(), equalTo(true));
        assertThat(cfB.isDone(), equalTo(true));
        assertThat(cfB.join(), equalTo("B"));
        assertThat(cfC.isDone(), equalTo(false));

        count = registry.dispatchAllWithCount(); // fourth firing
        assertThat(count, equalTo(1));
        assertThat(cfA.isDone(), equalTo(true));
        assertThat(cfB.isDone(), equalTo(true));
        assertThat(cfC.isDone(), equalTo(true));
        assertThat(cfC.join(), equalTo("C"));
    }

    @Test
    public void test_the_registry_overall_predicate_firing_works() {
        DataLoader<Object, Object> dlA = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlB = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlC = newDataLoader(identityBatchLoader);

        DispatchPredicate predicateOverAllOnThree = new CountingDispatchPredicate(3);

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .register("a", dlA, DISPATCH_NEVER)
                .register("b", dlB, DISPATCH_NEVER)
                .register("c", dlC, DISPATCH_NEVER)
                .dispatchPredicate(predicateOverAllOnThree)
                .build();


        CompletableFuture<Object> cfA = dlA.load("A");
        CompletableFuture<Object> cfB = dlB.load("B");
        CompletableFuture<Object> cfC = dlC.load("C");

        int count = registry.dispatchAllWithCount(); // first firing
        // none should fire
        assertThat(count, equalTo(0));
        assertThat(cfA.isDone(), equalTo(false));
        assertThat(cfB.isDone(), equalTo(false));
        assertThat(cfC.isDone(), equalTo(false));

        count = registry.dispatchAllWithCount(); // second firing but the overall been asked 3 times already
        assertThat(count, equalTo(3));
        assertThat(cfA.isDone(), equalTo(true));
        assertThat(cfB.isDone(), equalTo(true));
        assertThat(cfC.isDone(), equalTo(true));
    }

    @Test
    public void dispatch_immediate_firing_works() {
        DataLoader<Object, Object> dlA = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlB = newDataLoader(identityBatchLoader);
        DataLoader<Object, Object> dlC = newDataLoader(identityBatchLoader);

        DispatchPredicate predicateA = new CountingDispatchPredicate(1);
        DispatchPredicate predicateB = new CountingDispatchPredicate(2);
        DispatchPredicate predicateC = new CountingDispatchPredicate(3);

        DispatchPredicate predicateOverAll = new CountingDispatchPredicate(10);

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .register("a", dlA, predicateA)
                .register("b", dlB, predicateB)
                .register("c", dlC, predicateC)
                .dispatchPredicate(predicateOverAll)
                .build();


        CompletableFuture<Object> cfA = dlA.load("A");
        CompletableFuture<Object> cfB = dlB.load("B");
        CompletableFuture<Object> cfC = dlC.load("C");

        int count = registry.dispatchAllWithCountImmediately(); // all should fire
        assertThat(count, equalTo(3));
        assertThat(cfA.isDone(), equalTo(true));
        assertThat(cfA.join(), equalTo("A"));
        assertThat(cfB.isDone(), equalTo(true));
        assertThat(cfB.join(), equalTo("B"));
        assertThat(cfC.isDone(), equalTo(true));
        assertThat(cfC.join(), equalTo("C"));
    }

}
