package org.dataloader.instrumentation;

import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DispatchResult;
import org.dataloader.fixtures.TestKit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

class DataLoaderInstrumentationTest {

    BatchLoader<String, String> snoozingBatchLoader = keys -> CompletableFuture.supplyAsync(() -> {
        TestKit.snooze(100);
        return keys;
    });

    @Test
    void canMonitorDispatching() {
        AtomicLong timer = new AtomicLong();
        AtomicReference<DataLoader<?, ?>> dlRef = new AtomicReference<>();

        DataLoaderInstrumentation instrumentation = new DataLoaderInstrumentation() {

            @Override
            public DataLoaderInstrumentationContext<DispatchResult<?>> beginDispatch(DataLoader<?, ?> dataLoader) {
                dlRef.set(dataLoader);

                long then = System.currentTimeMillis();
                return new DataLoaderInstrumentationContext<>() {
                    @Override
                    public void onCompleted(DispatchResult<?> result, Throwable t) {
                        timer.set(System.currentTimeMillis() - then);
                    }
                };
            }

            @Override
            public DataLoaderInstrumentationContext<List<?>> beginBatchLoader(DataLoader<?, ?> dataLoader, List<?> keys, BatchLoaderEnvironment environment) {
                return DataLoaderInstrumentationHelper.noOpCtx();
            }
        };

        DataLoaderOptions options = new DataLoaderOptions().setInstrumentation(instrumentation).setMaxBatchSize(5);

        DataLoader<String, String> dl = DataLoaderFactory.newDataLoader(snoozingBatchLoader, options);

        for (int i = 0; i < 20; i++) {
             dl.load("X"+ i);
        }

        CompletableFuture<List<String>> dispatch = dl.dispatch();

        await().until(dispatch::isDone);
        assertThat(timer.get(), greaterThan(150L)); // we must have called batch load 4 times
        assertThat(dlRef.get(), is(dl));
    }

    @Test
    void canMonitorBatchLoading() {
        AtomicLong timer = new AtomicLong();
        AtomicReference<BatchLoaderEnvironment> beRef = new AtomicReference<>();
        AtomicReference<DataLoader<?, ?>> dlRef = new AtomicReference<>();

        DataLoaderInstrumentation instrumentation = new DataLoaderInstrumentation() {

            @Override
            public DataLoaderInstrumentationContext<List<?>> beginBatchLoader(DataLoader<?, ?> dataLoader, List<?> keys, BatchLoaderEnvironment environment) {
                dlRef.set(dataLoader);
                beRef.set(environment);

                long then = System.currentTimeMillis();
                return new DataLoaderInstrumentationContext<>() {
                    @Override
                    public void onCompleted(List<?> result, Throwable t) {
                        timer.set(System.currentTimeMillis() - then);
                    }
                };
            }
        };

        DataLoaderOptions options = new DataLoaderOptions().setInstrumentation(instrumentation);
        DataLoader<String, String> dl = DataLoaderFactory.newDataLoader(snoozingBatchLoader, options);

        dl.load("A");
        dl.load("B");

        CompletableFuture<List<String>> dispatch = dl.dispatch();

        await().until(dispatch::isDone);
        assertThat(timer.get(), greaterThan(50L));
        assertThat(dlRef.get(), is(dl));
        assertThat(beRef.get().getKeyContexts().keySet(), equalTo(Set.of("A", "B")));
    }
}