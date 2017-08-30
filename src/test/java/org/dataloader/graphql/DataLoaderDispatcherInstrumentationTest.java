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
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class DataLoaderDispatcherInstrumentationTest {

    @Test
    public void basic_invocation() throws Exception {

        AtomicInteger invocationCount = new AtomicInteger();
        final List<Object> loadedKeys = new ArrayList<>();
        final BatchLoader<Object, Object> identityBatchLoader = keys -> {
            invocationCount.incrementAndGet();
            loadedKeys.add(keys);
            return CompletableFuture.completedFuture(keys);
        };

        DataLoader<Object, Object> dlA = new DataLoader<>(identityBatchLoader);
        DataLoader<Object, Object> dlB = new DataLoader<>(identityBatchLoader);
        DataLoader<Object, Object> dlC = new DataLoader<>(identityBatchLoader);
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

        assertThat(invocationCount.get(), equalTo(3));

        // will be [[A],[B],[C]]
        assertThat(loadedKeys, equalTo(asList(singletonList("A"), singletonList("B"), singletonList("C"))));
    }
}