package org.dataloader.instrumentation;

import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DispatchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class CapturingInstrumentation implements DataLoaderInstrumentation {
    protected String name;
    protected List<String> methods = new ArrayList<>();

    public CapturingInstrumentation(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<String> methods() {
        return methods;
    }

    public List<String> notLoads() {
        return methods.stream().filter(method -> !method.contains("beginLoad")).collect(Collectors.toList());
    }

    public List<String> onlyLoads() {
        return methods.stream().filter(method -> method.contains("beginLoad")).collect(Collectors.toList());
    }


    @Override
    public DataLoaderInstrumentationContext<Object> beginLoad(DataLoader<?, ?> dataLoader, Object key, Object loadContext) {
        methods.add(name + "_beginLoad" +"_k:" + key);
        return new DataLoaderInstrumentationContext<>() {
            @Override
            public void onDispatched() {
                methods.add(name + "_beginLoad_onDispatched"+"_k:" + key);
            }

            @Override
            public void onCompleted(Object result, Throwable t) {
                methods.add(name + "_beginLoad_onCompleted"+"_k:" + key);
            }
        };
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
