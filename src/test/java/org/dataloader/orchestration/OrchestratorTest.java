package org.dataloader.orchestration;

import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.awaitility.Awaitility.await;
import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.dataloader.fixtures.TestKit.lowerCaseBatchLoader;
import static org.dataloader.fixtures.TestKit.reverseBatchLoader;
import static org.dataloader.fixtures.TestKit.upperCaseBatchLoader;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class OrchestratorTest {

    DataLoaderOptions cachingAndBatchingOptions = DataLoaderOptions.newOptions().setBatchingEnabled(true).setCachingEnabled(true);

    DataLoader<String, String> dlUpper = newDataLoader(upperCaseBatchLoader(), cachingAndBatchingOptions);
    DataLoader<String, String> dlLower = newDataLoader(lowerCaseBatchLoader(), cachingAndBatchingOptions);
    DataLoader<String, String> dlReverse = newDataLoader(reverseBatchLoader(), cachingAndBatchingOptions);

    @Test
    void canOrchestrate() {

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .register("upper", dlUpper)
                .register("lower", dlLower)
                .register("reverse", dlReverse)
                .build();

        Orchestrator<String, String> orchestrator = Orchestrator.orchestrate(dlUpper);
        Step<String, String> step1 = orchestrator.load("aBc", null);
        With<String, String> with1 = step1.with(dlLower);
        Step<String, String> step2 = with1.thenLoad(key -> key);
        With<String, String> with2 = step2.with(dlReverse);
        Step<String, String> step3 = with2.thenLoad(key -> key);
        CompletableFuture<String> cf = step3.toCompletableFuture();

        // because all the dls are dispatched in "perfect order" here they all end up dispatching
        // at JUST the right time.  A change in order would be different
        registry.dispatchAll();

        await().until(cf::isDone);

        assertThat(cf.join(), equalTo("cba"));
    }

    @Test
    void canOrchestrateWhenNotInPerfectOrder() {

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .register("reverse", dlReverse)
                .register("lower", dlLower)
                .register("upper", dlUpper)
                .build();

        Orchestrator<String, String> orchestrator = Orchestrator.orchestrate(dlUpper);
        CompletableFuture<String> cf = orchestrator.load("aBc", null)
                .with(dlLower).thenLoad(key1 -> key1)
                .with(dlReverse).thenLoad(key -> key)
                .toCompletableFuture();

        registry.dispatchAll();

        assertThat(cf.isDone(), equalTo(false));

        assertThat(orchestrator.getTracker().getOutstandingLoadCount(),equalTo(2));

        await().until(cf::isDone);

        assertThat(cf.join(), equalTo("cba"));
    }
}