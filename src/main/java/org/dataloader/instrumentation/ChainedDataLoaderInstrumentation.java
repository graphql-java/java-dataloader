package org.dataloader.instrumentation;

import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DispatchResult;
import org.dataloader.annotations.PublicApi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This {@link DataLoaderInstrumentation} can chain together multiple instrumentations and have them all called in
 * the order of the provided list.
 */
@PublicApi
public class ChainedDataLoaderInstrumentation implements DataLoaderInstrumentation {
    private final List<DataLoaderInstrumentation> instrumentations;

    public ChainedDataLoaderInstrumentation() {
        instrumentations = List.of();
    }

    public ChainedDataLoaderInstrumentation(List<DataLoaderInstrumentation> instrumentations) {
        this.instrumentations = List.copyOf(instrumentations);
    }

    public List<DataLoaderInstrumentation> getInstrumentations() {
        return instrumentations;
    }

    /**
     * Adds a new {@link DataLoaderInstrumentation} to the list and creates a new {@link ChainedDataLoaderInstrumentation}
     *
     * @param instrumentation the one to add
     * @return a new ChainedDataLoaderInstrumentation object
     */
    public ChainedDataLoaderInstrumentation add(DataLoaderInstrumentation instrumentation) {
        ArrayList<DataLoaderInstrumentation> list = new ArrayList<>(this.instrumentations);
        list.add(instrumentation);
        return new ChainedDataLoaderInstrumentation(list);
    }

    /**
     * Prepends a new {@link DataLoaderInstrumentation} to the list and creates a new {@link ChainedDataLoaderInstrumentation}
     *
     * @param instrumentation the one to add
     * @return a new ChainedDataLoaderInstrumentation object
     */
    public ChainedDataLoaderInstrumentation prepend(DataLoaderInstrumentation instrumentation) {
        ArrayList<DataLoaderInstrumentation> list = new ArrayList<>();
        list.add(instrumentation);
        list.addAll(this.instrumentations);
        return new ChainedDataLoaderInstrumentation(list);
    }

    /**
     * Adds a collection of {@link DataLoaderInstrumentation} to the list and creates a new {@link ChainedDataLoaderInstrumentation}
     *
     * @param instrumentations the new ones to add
     * @return a new ChainedDataLoaderInstrumentation object
     */
    public ChainedDataLoaderInstrumentation addAll(Collection<DataLoaderInstrumentation> instrumentations) {
        ArrayList<DataLoaderInstrumentation> list = new ArrayList<>(this.instrumentations);
        list.addAll(instrumentations);
        return new ChainedDataLoaderInstrumentation(list);
    }

    @Override
    public DataLoaderInstrumentationContext<DispatchResult<?>> beginDispatch(DataLoader<?, ?> dataLoader) {
        return chainedCtx(it -> it.beginDispatch(dataLoader));
    }

    @Override
    public DataLoaderInstrumentationContext<List<?>> beginBatchLoader(DataLoader<?, ?> dataLoader, List<?> keys, BatchLoaderEnvironment environment) {
        return chainedCtx(it -> it.beginBatchLoader(dataLoader, keys, environment));
    }

    private <T> DataLoaderInstrumentationContext<T> chainedCtx(Function<DataLoaderInstrumentation, DataLoaderInstrumentationContext<T>> mapper) {
        // if we have zero or 1 instrumentations (and 1 is the most common), then we can avoid an object allocation
        // of the ChainedInstrumentationContext since it won't be needed
        if (instrumentations.isEmpty()) {
            return DataLoaderInstrumentationHelper.noOpCtx();
        }
        if (instrumentations.size() == 1) {
            return mapper.apply(instrumentations.get(0));
        }
        return new ChainedInstrumentationContext<>(dropNullContexts(mapper));
    }

    private <T> List<DataLoaderInstrumentationContext<T>> dropNullContexts(Function<DataLoaderInstrumentation, DataLoaderInstrumentationContext<T>> mapper) {
        return instrumentations.stream()
                .map(mapper)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static class ChainedInstrumentationContext<T> implements DataLoaderInstrumentationContext<T> {
        private final List<DataLoaderInstrumentationContext<T>> contexts;

        public ChainedInstrumentationContext(List<DataLoaderInstrumentationContext<T>> contexts) {
            this.contexts = contexts;
        }

        @Override
        public void onDispatched() {
            contexts.forEach(DataLoaderInstrumentationContext::onDispatched);
        }

        @Override
        public void onCompleted(T result, Throwable t) {
            contexts.forEach(it -> it.onCompleted(result, t));
        }
    }
}
