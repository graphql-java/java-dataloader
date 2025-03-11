package org.dataloader.instrumentation;

import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DispatchResult;

import java.util.List;

class CapturingInstrumentationReturnsNull extends CapturingInstrumentation {

    public CapturingInstrumentationReturnsNull(String name) {
        super(name);
    }

    @Override
    public DataLoaderInstrumentationContext<Object> beginLoad(DataLoader<?, ?> dataLoader, Object key, Object loadContext) {
        methods.add(name + "_beginLoad" +"_k:" + key);
        return null;
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
