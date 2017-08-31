package org.dataloader.graphql;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class DataLoaderDispatcherInstrumentationTest {

    class CountingLoader implements BatchLoader<Object, Object> {
        int invocationCount = 0;
        List<Object> loadedKeys = new ArrayList<>();

        @Override
        public CompletionStage<List<Object>> load(List<Object> keys) {
            invocationCount++;
            loadedKeys.add(keys);
            return CompletableFuture.completedFuture(keys);
        }
    }

    @Test
    public void basic_invocation() throws Exception {

        final CountingLoader batchLoader = new CountingLoader();

        DataLoader<Object, Object> dlA = new DataLoader<>(batchLoader);
        DataLoader<Object, Object> dlB = new DataLoader<>(batchLoader);
        DataLoader<Object, Object> dlC = new DataLoader<>(batchLoader);
        DataLoaderRegistry registry = new DataLoaderRegistry()
                .register(dlA)
                .register(dlB)
                .register(dlC);

        DataLoaderDispatcherInstrumentation dispatcher = new DataLoaderDispatcherInstrumentation(registry);
        InstrumentationContext<CompletableFuture<ExecutionResult>> context = dispatcher.beginExecutionStrategy(null);

        // cause some activity
        dlA.load("A");
        dlB.load("B");
        dlC.load("C");

        context.onEnd(null, null);

        assertThat(batchLoader.invocationCount, equalTo(3));

        // will be [[A],[B],[C]]
        assertThat(batchLoader.loadedKeys, equalTo(asList(singletonList("A"), singletonList("B"), singletonList("C"))));
    }

    @Test
    public void exceptions_wont_cause_dispatches() throws Exception {
        final CountingLoader batchLoader = new CountingLoader();

        DataLoader<Object, Object> dlA = new DataLoader<>(batchLoader);
        DataLoaderRegistry registry = new DataLoaderRegistry()
                .register(dlA);

        DataLoaderDispatcherInstrumentation dispatcher = new DataLoaderDispatcherInstrumentation(registry);
        InstrumentationContext<CompletableFuture<ExecutionResult>> context = dispatcher.beginExecutionStrategy(null);

        // cause some activity
        dlA.load("A");

        context.onEnd(null, new RuntimeException("Should not run"));

        assertThat(batchLoader.invocationCount, equalTo(0));
    }
}