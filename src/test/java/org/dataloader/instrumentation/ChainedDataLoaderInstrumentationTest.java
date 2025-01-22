package org.dataloader.instrumentation;

import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DispatchResult;
import org.dataloader.fixtures.TestKit;
import org.dataloader.fixtures.parameterized.TestDataLoaderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.awaitility.Awaitility.await;
import static org.dataloader.DataLoaderOptions.newOptions;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class ChainedDataLoaderInstrumentationTest {

    static class CapturingInstrumentation implements DataLoaderInstrumentation {
        String name;
        List<String> methods = new ArrayList<>();

        public CapturingInstrumentation(String name) {
            this.name = name;
        }

        @Override
        public DataLoaderInstrumentationContext<DispatchResult<?>> beginDispatch(DataLoader<?, ?> dataLoader) {
            methods.add(name + "_beginDispatch");
            return new DataLoaderInstrumentationContext<>() {
                @Override
                public void onDispatched() {
                    methods.add(name + "_beginDispatch_onDispatched");
                }

                @Override
                public void onCompleted(DispatchResult<?> result, Throwable t) {
                    methods.add(name + "_beginDispatch_onCompleted");
                }
            };
        }

        @Override
        public DataLoaderInstrumentationContext<List<?>> beginBatchLoader(DataLoader<?, ?> dataLoader, List<?> keys, BatchLoaderEnvironment environment) {
            methods.add(name + "_beginBatchLoader");
            return new DataLoaderInstrumentationContext<>() {
                @Override
                public void onDispatched() {
                    methods.add(name + "_beginBatchLoader_onDispatched");
                }

                @Override
                public void onCompleted(List<?> result, Throwable t) {
                    methods.add(name + "_beginBatchLoader_onCompleted");
                }
            };
        }
    }


    static class CapturingInstrumentationReturnsNull extends CapturingInstrumentation {

        public CapturingInstrumentationReturnsNull(String name) {
            super(name);
        }

        @Override
        public DataLoaderInstrumentationContext<DispatchResult<?>> beginDispatch(DataLoader<?, ?> dataLoader) {
            methods.add(name + "_beginDispatch");
            return null;
        }

        @Override
        public DataLoaderInstrumentationContext<List<?>> beginBatchLoader(DataLoader<?, ?> dataLoader, List<?> keys, BatchLoaderEnvironment environment) {
            methods.add(name + "_beginBatchLoader");
            return null;
        }
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
        CapturingInstrumentation capturingA = new CapturingInstrumentation("A");
        CapturingInstrumentation capturingB = new CapturingInstrumentation("B");
        CapturingInstrumentation capturingButReturnsNull = new CapturingInstrumentationReturnsNull("NULL");

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
}