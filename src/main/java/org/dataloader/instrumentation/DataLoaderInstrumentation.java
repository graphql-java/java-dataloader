package org.dataloader.instrumentation;

import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DispatchResult;
import org.dataloader.annotations.PublicSpi;

import java.util.List;

/**
 * This interface is called when certain actions happen inside a data loader
 */
@PublicSpi
public interface DataLoaderInstrumentation {
    /**
     * This call back is done just before the {@link DataLoader#load(Object)} methods are invoked,
     * and it completes when the load promise is completed.  If the value is a cached {@link java.util.concurrent.CompletableFuture}
     * then it might return almost immediately, otherwise it will return
     * when the batch load function is invoked and values get returned
     *
     * @param dataLoader  the {@link DataLoader} in question
     * @param key         the key used during the {@link DataLoader#load(Object)} call
     * @param loadContext the load context used during the {@link DataLoader#load(Object, Object)} call
     * @return a DataLoaderInstrumentationContext or null to be more performant
     */
    default DataLoaderInstrumentationContext<Object> beginLoad(DataLoader<?, ?> dataLoader, Object key, Object loadContext) {
        return null;
    }

    /**
     * This call back is done just before the {@link DataLoader#dispatch()} is invoked,
     * and it completes when the dispatch call promise is done.
     *
     * @param dataLoader the {@link DataLoader} in question
     * @return a DataLoaderInstrumentationContext or null to be more performant
     */
    default DataLoaderInstrumentationContext<DispatchResult<?>> beginDispatch(DataLoader<?, ?> dataLoader) {
        return null;
    }

    /**
     * This call back is done just before the `batch loader` of a {@link DataLoader} is invoked.  Remember a batch loader
     * could be called multiple times during a dispatch event (because of max batch sizes)
     *
     * @param dataLoader  the {@link DataLoader} in question
     * @param keys        the set of keys being fetched
     * @param environment the {@link BatchLoaderEnvironment}
     * @return a DataLoaderInstrumentationContext or null to be more performant
     */
    default DataLoaderInstrumentationContext<List<?>> beginBatchLoader(DataLoader<?, ?> dataLoader, List<?> keys, BatchLoaderEnvironment environment) {
        return null;
    }
}
