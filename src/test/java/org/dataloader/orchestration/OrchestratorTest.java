package org.dataloader.orchestration;

import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.fixtures.TestKit;
import org.dataloader.orchestration.observation.Tracker;
import org.dataloader.orchestration.observation.TrackingObserver;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.dataloader.fixtures.TestKit.alternateCaseBatchLoader;
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
    DataLoader<String, String> dlAlternateCase = newDataLoader(alternateCaseBatchLoader(), cachingAndBatchingOptions);

    @Test
    void canOrchestrate() {

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .register("upper", dlUpper)
                .register("lower", dlLower)
                .register("reverse", dlReverse)
                .register("alternateCase", dlAlternateCase)
                .build();

        Orchestrator<String, String> orchestrator = Orchestrator.orchestrate(dlUpper).build();
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
                .register("alternateCase", dlAlternateCase)
                .build();

        ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
        Orchestrator<String, String> orchestrator = Orchestrator.orchestrate(dlUpper).executor(forkJoinPool).build();
        CompletableFuture<String> cf = orchestrator.load("aBc", null)
                .with(dlLower).thenLoad(key1 -> key1)
                .with(dlReverse).thenLoad(key -> key)
                .with(dlAlternateCase).thenLoadAsync(key -> key)
                .toCompletableFuture();

        for (int i = 0; i < 10; i++) {
            TestKit.snooze(50); // TODO - hack or now
            registry.dispatchAll();
            System.out.println("Waiting for " + i + " to complete...");
        }

        await().until(cf::isDone);

        assertThat(cf.join(), equalTo("cBa"));
    }

    @Test
    void can_observe_orchestration_happening() {

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .register("upper", dlUpper)
                .register("lower", dlLower)
                .register("reverse", dlReverse)
                .register("alternateCase", dlAlternateCase)
                .build();

        AtomicInteger stepCount = new AtomicInteger();
        TrackingObserver observer = new TrackingObserver() {
            @Override
            public void onStart(Tracker tracker) {
                System.out.println("starting - step count : " + tracker.getStepCount());
                stepCount.set(tracker.getStepCount());
            }

            @Override
            public void onLoad(Tracker tracker, int stepIndex, DataLoader<?, ?> dl) {
                System.out.println("onLoad : " + stepIndex);
            }

            @Override
            public void onLoadComplete(Tracker tracker, int stepIndex, DataLoader<?, ?> dl, Throwable throwable) {
                System.out.println("onLoadComplete : " + stepIndex);
            }
        };

        Orchestrator<String, String> orchestrator = Orchestrator.orchestrate(dlUpper).observer(observer).build();
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
        assertThat(stepCount.get(), equalTo(3));
    }
}