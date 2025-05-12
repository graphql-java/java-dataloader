package org.dataloader.instrumentation;

import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DispatchResult;
import org.dataloader.fixtures.Stopwatch;
import org.dataloader.fixtures.TestKit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

public class DataLoaderInstrumentationTest {

    BatchLoader<String, String> snoozingBatchLoader = keys -> CompletableFuture.supplyAsync(() -> {
        TestKit.snooze(100);
        return keys;
    });

    @Test
    void canMonitorLoading() {
        AtomicReference<DataLoader<?, ?>> dlRef = new AtomicReference<>();

        CapturingInstrumentation instrumentation = new CapturingInstrumentation("x") {

            @Override
            public DataLoaderInstrumentationContext<Object> beginLoad(DataLoader<?, ?> dataLoader, Object key, Object loadContext) {
                DataLoaderInstrumentationContext<Object> superCtx = super.beginLoad(dataLoader, key, loadContext);
                dlRef.set(dataLoader);
                return superCtx;
            }

            @Override
            public DataLoaderInstrumentationContext<List<?>> beginBatchLoader(DataLoader<?, ?> dataLoader, List<?> keys, BatchLoaderEnvironment environment) {
                return DataLoaderInstrumentationHelper.noOpCtx();
            }
        };

        DataLoaderOptions options = DataLoaderOptions.newOptions()
                .setInstrumentation(instrumentation)
                .setMaxBatchSize(5)
                .build();

        DataLoader<String, String> dl = DataLoaderFactory.newDataLoader(snoozingBatchLoader, options);

        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String key = "X" + i;
            keys.add(key);
            dl.load(key);
        }

        // load a key that is cached
        dl.load("X0");

        CompletableFuture<List<String>> dispatch = dl.dispatch();

        await().until(dispatch::isDone);
        assertThat(dlRef.get(), is(dl));
        assertThat(dispatch.join(), equalTo(keys));

        // the batch loading means they start and are instrumentation dispatched before they all end up completing
        assertThat(instrumentation.onlyLoads(),
                equalTo(List.of(
                        "x_beginLoad_k:X0", "x_beginLoad_onDispatched_k:X0",
                        "x_beginLoad_k:X1", "x_beginLoad_onDispatched_k:X1",
                        "x_beginLoad_k:X2", "x_beginLoad_onDispatched_k:X2",
                        "x_beginLoad_k:X0", "x_beginLoad_onDispatched_k:X0", // second cached call counts
                        "x_beginLoad_onCompleted_k:X0",
                        "x_beginLoad_onCompleted_k:X0", // each load call counts
                        "x_beginLoad_onCompleted_k:X1", "x_beginLoad_onCompleted_k:X2")));

    }


    @Test
    void canMonitorDispatching() {
        Stopwatch stopwatch = Stopwatch.stopwatchUnStarted();
        AtomicReference<DataLoader<?, ?>> dlRef = new AtomicReference<>();

        DataLoaderInstrumentation instrumentation = new DataLoaderInstrumentation() {

            @Override
            public DataLoaderInstrumentationContext<DispatchResult<?>> beginDispatch(DataLoader<?, ?> dataLoader) {
                dlRef.set(dataLoader);
                stopwatch.start();
                return new DataLoaderInstrumentationContext<>() {
                    @Override
                    public void onCompleted(DispatchResult<?> result, Throwable t) {
                        stopwatch.stop();
                    }
                };
            }

            @Override
            public DataLoaderInstrumentationContext<List<?>> beginBatchLoader(DataLoader<?, ?> dataLoader, List<?> keys, BatchLoaderEnvironment environment) {
                return DataLoaderInstrumentationHelper.noOpCtx();
            }
        };

        DataLoaderOptions options = DataLoaderOptions.newOptions()
                .setInstrumentation(instrumentation)
                .setMaxBatchSize(5)
                .build();

        DataLoader<String, String> dl = DataLoaderFactory.newDataLoader(snoozingBatchLoader, options);

        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String key = "X" + i;
            keys.add(key);
            dl.load(key);
        }

        CompletableFuture<List<String>> dispatch = dl.dispatch();

        await().until(dispatch::isDone);
        // we must have called batch load 4 times at 100ms snooze  per call
        // but its in parallel via supplyAsync
        assertThat(stopwatch.elapsed(), greaterThan(75L));
        assertThat(dlRef.get(), is(dl));
        assertThat(dispatch.join(), equalTo(keys));
    }

    @Test
    void canMonitorBatchLoading() {
        Stopwatch stopwatch = Stopwatch.stopwatchUnStarted();
        AtomicReference<BatchLoaderEnvironment> beRef = new AtomicReference<>();
        AtomicReference<DataLoader<?, ?>> dlRef = new AtomicReference<>();

        DataLoaderInstrumentation instrumentation = new DataLoaderInstrumentation() {

            @Override
            public DataLoaderInstrumentationContext<List<?>> beginBatchLoader(DataLoader<?, ?> dataLoader, List<?> keys, BatchLoaderEnvironment environment) {
                dlRef.set(dataLoader);
                beRef.set(environment);

                stopwatch.start();
                return new DataLoaderInstrumentationContext<>() {
                    @Override
                    public void onCompleted(List<?> result, Throwable t) {
                        stopwatch.stop();
                    }
                };
            }
        };

        DataLoaderOptions options = DataLoaderOptions.newOptions().setInstrumentation(instrumentation).build();
        DataLoader<String, String> dl = DataLoaderFactory.newDataLoader(snoozingBatchLoader, options);

        dl.load("A", "kcA");
        dl.load("B", "kcB");

        CompletableFuture<List<String>> dispatch = dl.dispatch();

        await().until(dispatch::isDone);
        assertThat(stopwatch.elapsed(), greaterThan(50L));
        assertThat(dlRef.get(), is(dl));
        assertThat(beRef.get().getKeyContexts().keySet(), equalTo(Set.of("A", "B")));
    }
}