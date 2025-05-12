package org.dataloader.instrumentation;

import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.fixtures.TestKit;
import org.dataloader.fixtures.parameterized.TestDataLoaderFactory;
import org.dataloader.registries.ScheduledDataLoaderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

public class DataLoaderRegistryInstrumentationTest {
    DataLoader<String, String> dlX;
    DataLoader<String, String> dlY;
    DataLoader<String, String> dlZ;

    CapturingInstrumentation instrA;
    CapturingInstrumentation instrB;
    ChainedDataLoaderInstrumentation chainedInstrA;
    ChainedDataLoaderInstrumentation chainedInstrB;

    @BeforeEach
    void setUp() {
        dlX = TestKit.idLoader("X");
        dlY = TestKit.idLoader("Y");
        dlZ = TestKit.idLoader("Z");
        instrA = new CapturingInstrumentation("A");
        instrB = new CapturingInstrumentation("B");
        chainedInstrA = new ChainedDataLoaderInstrumentation().add(instrA);
        chainedInstrB = new ChainedDataLoaderInstrumentation().add(instrB);
    }

    @Test
    void canInstrumentRegisteredDLsViaBuilder() {

        assertThat(dlX.getOptions().getInstrumentation(), equalTo(DataLoaderInstrumentationHelper.NOOP_INSTRUMENTATION));

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .instrumentation(chainedInstrA)
                .register("X", dlX)
                .register("Y", dlY)
                .register("Z", dlZ)
                .build();

        assertThat(registry.getInstrumentation(), equalTo(chainedInstrA));

        for (String key : List.of("X", "Y", "Z")) {
            DataLoaderInstrumentation instrumentation = registry.getDataLoader(key).getOptions().getInstrumentation();
            assertThat(instrumentation, instanceOf(ChainedDataLoaderInstrumentation.class));
            List<DataLoaderInstrumentation> instrumentations = ((ChainedDataLoaderInstrumentation) instrumentation).getInstrumentations();
            assertThat(instrumentations, equalTo(List.of(instrA)));
        }
    }

    @Test
    void canInstrumentRegisteredDLsViaBuilderCombined() {

        DataLoaderRegistry registry1 = DataLoaderRegistry.newRegistry()
                .register("X", dlX)
                .register("Y", dlY)
                .build();

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .instrumentation(chainedInstrA)
                .register("Z", dlZ)
                .registerAll(registry1)
                .build();

        for (String key : List.of("X", "Y", "Z")) {
            DataLoaderInstrumentation instrumentation = registry.getDataLoader(key).getOptions().getInstrumentation();
            assertThat(instrumentation, instanceOf(ChainedDataLoaderInstrumentation.class));
            List<DataLoaderInstrumentation> instrumentations = ((ChainedDataLoaderInstrumentation) instrumentation).getInstrumentations();
            assertThat(instrumentations, equalTo(List.of(instrA)));
        }
    }

    @Test
    void canInstrumentViaMutativeRegistration() {

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .instrumentation(chainedInstrA)
                .build();

        registry.register("X", dlX);
        registry.computeIfAbsent("Y", l -> dlY);
        registry.computeIfAbsent("Z", l -> dlZ);

        for (String key : List.of("X", "Y", "Z")) {
            DataLoaderInstrumentation instrumentation = registry.getDataLoader(key).getOptions().getInstrumentation();
            assertThat(instrumentation, instanceOf(ChainedDataLoaderInstrumentation.class));
            List<DataLoaderInstrumentation> instrumentations = ((ChainedDataLoaderInstrumentation) instrumentation).getInstrumentations();
            assertThat(instrumentations, equalTo(List.of(instrA)));
        }
    }

    @Test
    void wontDoAnyThingIfThereIsNoRegistryInstrumentation() {
        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .register("X", dlX)
                .register("Y", dlY)
                .register("Z", dlZ)
                .build();

        for (String key : List.of("X", "Y", "Z")) {
            DataLoaderInstrumentation instrumentation = registry.getDataLoader(key).getOptions().getInstrumentation();
            assertThat(instrumentation, equalTo(DataLoaderInstrumentationHelper.NOOP_INSTRUMENTATION));
        }
    }

    @Test
    void wontDoAnyThingIfThereTheyAreTheSameInstrumentationAlready() {
        DataLoader<String, String> newX = dlX.transform(builder -> builder.options(dlX.getOptions().transform(b-> b.setInstrumentation(instrA))));
        DataLoader<String, String> newY = dlY.transform(builder ->  builder.options(dlY.getOptions().transform(b-> b.setInstrumentation(instrA))));
        DataLoader<String, String> newZ = dlZ.transform(builder ->  builder.options(dlZ.getOptions().transform(b-> b.setInstrumentation(instrA))));
        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .instrumentation(instrA)
                .register("X", newX)
                .register("Y", newY)
                .register("Z", newZ)
                .build();

        Map<String, DataLoader<String, String>> dls = Map.of("X", newX, "Y", newY, "Z", newZ);

        assertThat(registry.getInstrumentation(), equalTo(instrA));

        for (String key : List.of("X", "Y", "Z")) {
            DataLoader<Object, Object> dataLoader = registry.getDataLoader(key);
            DataLoaderInstrumentation instrumentation = dataLoader.getOptions().getInstrumentation();
            assertThat(instrumentation, equalTo(instrA));
            // it's the same DL - it's not changed because it has the same instrumentation
            assertThat(dls.get(key), equalTo(dataLoader));
        }
    }

    @Test
    void ifTheDLHasAInstrumentationThenItsTurnedIntoAChainedOne() {
        DataLoaderOptions options = dlX.getOptions().transform(b -> b.setInstrumentation(instrA));
        DataLoader<String, String> newX = dlX.transform(builder -> builder.options(options));

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .instrumentation(instrB)
                .register("X", newX)
                .build();

        DataLoader<Object, Object> dataLoader = registry.getDataLoader("X");
        DataLoaderInstrumentation instrumentation = dataLoader.getOptions().getInstrumentation();
        assertThat(instrumentation, instanceOf(ChainedDataLoaderInstrumentation.class));

        List<DataLoaderInstrumentation> instrumentations = ((ChainedDataLoaderInstrumentation) instrumentation).getInstrumentations();
        // it gets turned into a chained one and the registry one goes first
        assertThat(instrumentations, equalTo(List.of(instrB, instrA)));
    }

    @Test
    void chainedInstrumentationsWillBeCombined() {
        DataLoaderOptions options = dlX.getOptions().transform(b -> b.setInstrumentation(chainedInstrB));
        DataLoader<String, String> newX = dlX.transform(builder -> builder.options(options));

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .instrumentation(instrA)
                .register("X", newX)
                .build();

        DataLoader<Object, Object> dataLoader = registry.getDataLoader("X");
        DataLoaderInstrumentation instrumentation = dataLoader.getOptions().getInstrumentation();
        assertThat(instrumentation, instanceOf(ChainedDataLoaderInstrumentation.class));

        List<DataLoaderInstrumentation> instrumentations = ((ChainedDataLoaderInstrumentation) instrumentation).getInstrumentations();
        // it gets turned into a chained one and the registry one goes first
        assertThat(instrumentations, equalTo(List.of(instrA, instrB)));
    }

    @SuppressWarnings("resource")
    @Test
    void canInstrumentScheduledRegistryViaBuilder() {

        assertThat(dlX.getOptions().getInstrumentation(), equalTo(DataLoaderInstrumentationHelper.NOOP_INSTRUMENTATION));

        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry.newScheduledRegistry()
                .instrumentation(chainedInstrA)
                .register("X", dlX)
                .register("Y", dlY)
                .register("Z", dlZ)
                .build();

        assertThat(registry.getInstrumentation(), equalTo(chainedInstrA));

        for (String key : List.of("X", "Y", "Z")) {
            DataLoaderInstrumentation instrumentation = registry.getDataLoader(key).getOptions().getInstrumentation();
            assertThat(instrumentation, instanceOf(ChainedDataLoaderInstrumentation.class));
            List<DataLoaderInstrumentation> instrumentations = ((ChainedDataLoaderInstrumentation) instrumentation).getInstrumentations();
            assertThat(instrumentations, equalTo(List.of(instrA)));
        }
    }

    @ParameterizedTest
    @MethodSource("org.dataloader.fixtures.parameterized.TestDataLoaderFactories#get")
    public void endToEndIntegrationTest(TestDataLoaderFactory factory) {
        DataLoader<String, String> dl = factory.idLoader();

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .instrumentation(instrA)
                .register("X", dl)
                .build();

        // since the data-loader changed when registered you MUST get the data loader from the registry
        // not direct to the old one
        DataLoader<String, String> dataLoader = registry.getDataLoader("X");
        CompletableFuture<String> loadA = dataLoader.load("A");

        registry.dispatchAll();

        await().until(loadA::isDone);
        assertThat(loadA.join(), equalTo("A"));

        assertThat(instrA.notLoads(), equalTo(List.of("A_beginDispatch",
                "A_beginBatchLoader", "A_beginBatchLoader_onDispatched", "A_beginBatchLoader_onCompleted",
                "A_beginDispatch_onDispatched", "A_beginDispatch_onCompleted")));
    }
}