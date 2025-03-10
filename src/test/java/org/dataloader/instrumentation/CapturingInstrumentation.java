package org.dataloader.instrumentation;

import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DispatchResult;

import java.util.ArrayList;
import java.util.List;

class CapturingInstrumentation implements DataLoaderInstrumentation {
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
