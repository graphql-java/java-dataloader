package org.dataloader.instrumentation;

import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.fixtures.TestKit;
import org.dataloader.fixtures.parameterized.TestDataLoaderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.awaitility.Awaitility.await;
import static org.dataloader.DataLoaderOptions.newOptions;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class ChainedDataLoaderInstrumentationTest {

    CapturingInstrumentation capturingA;
    CapturingInstrumentation capturingB;
    CapturingInstrumentation capturingButReturnsNull;


    @BeforeEach
    void setUp() {
        capturingA = new CapturingInstrumentation("A");
        capturingB = new CapturingInstrumentation("B");
        capturingButReturnsNull = new CapturingInstrumentationReturnsNull("NULL");
    }

    @Test
    void canChainTogetherZeroInstrumentation() {
        // just to prove its useless but harmless
        ChainedDataLoaderInstrumentation chainedItn = new ChainedDataLoaderInstrumentation();

        DataLoaderOptions options = newOptions().setInstrumentation(chainedItn);

        DataLoader<String, String> dl = DataLoaderFactory.newDataLoader(TestKit.keysAsValues(), options);

        dl.load("A");
        dl.load("B");

        CompletableFuture<List<String>> dispatch = dl.dispatch();

        await().until(dispatch::isDone);
        assertThat(dispatch.join(), equalTo(List.of("A", "B")));
    }

    @Test
    void canChainTogetherOneInstrumentation() {
        CapturingInstrumentation capturingA = new CapturingInstrumentation("A");

        ChainedDataLoaderInstrumentation chainedItn = new ChainedDataLoaderInstrumentation()
                .add(capturingA);

        DataLoaderOptions options = newOptions().setInstrumentation(chainedItn);

        DataLoader<String, String> dl = DataLoaderFactory.newDataLoader(TestKit.keysAsValues(), options);

        dl.load("A");
        dl.load("B");

        CompletableFuture<List<String>> dispatch = dl.dispatch();

        await().until(dispatch::isDone);

        assertThat(capturingA.methods, equalTo(List.of("A_beginDispatch",
                "A_beginBatchLoader", "A_beginBatchLoader_onDispatched", "A_beginBatchLoader_onCompleted",
                "A_beginDispatch_onDispatched", "A_beginDispatch_onCompleted")));
    }


    @ParameterizedTest
    @MethodSource("org.dataloader.fixtures.parameterized.TestDataLoaderFactories#get")
    public void canChainTogetherManyInstrumentationsWithDifferentBatchLoaders(TestDataLoaderFactory factory) {

        ChainedDataLoaderInstrumentation chainedItn = new ChainedDataLoaderInstrumentation()
                .add(capturingA)
                .add(capturingB)
                .add(capturingButReturnsNull);

        DataLoaderOptions options = newOptions().setInstrumentation(chainedItn);

        DataLoader<String, String> dl = factory.idLoader(options);

        dl.load("A");
        dl.load("B");

        CompletableFuture<List<String>> dispatch = dl.dispatch();

        await().until(dispatch::isDone);

        //
        // A_beginBatchLoader happens before A_beginDispatch_onDispatched because these are sync
        // and no async - a batch scheduler or async batch loader would change that
        //
        assertThat(capturingA.methods, equalTo(List.of("A_beginDispatch",
                "A_beginBatchLoader", "A_beginBatchLoader_onDispatched", "A_beginBatchLoader_onCompleted",
                "A_beginDispatch_onDispatched", "A_beginDispatch_onCompleted")));

        assertThat(capturingB.methods, equalTo(List.of("B_beginDispatch",
                "B_beginBatchLoader", "B_beginBatchLoader_onDispatched", "B_beginBatchLoader_onCompleted",
                "B_beginDispatch_onDispatched", "B_beginDispatch_onCompleted")));

        // it returned null on all its contexts - nothing to call back on
        assertThat(capturingButReturnsNull.methods, equalTo(List.of("NULL_beginDispatch", "NULL_beginBatchLoader")));
    }

    @Test
    void addition_works() {
        ChainedDataLoaderInstrumentation chainedItn = new ChainedDataLoaderInstrumentation()
                .add(capturingA).prepend(capturingB).addAll(List.of(capturingButReturnsNull));

        assertThat(chainedItn.getInstrumentations(), equalTo(List.of(capturingB, capturingA, capturingButReturnsNull)));
    }
}