package org.dataloader.instrumentation;


import org.dataloader.annotations.Internal;

import java.util.function.BiConsumer;

/**
 * A simple implementation of {@link DataLoaderInstrumentationContext}
 */
@Internal
class SimpleDataLoaderInstrumentationContext<T> implements DataLoaderInstrumentationContext<T> {

    private final BiConsumer<T, Throwable> codeToRunOnComplete;
    private final Runnable codeToRunOnDispatch;

    SimpleDataLoaderInstrumentationContext(Runnable codeToRunOnDispatch, BiConsumer<T, Throwable> codeToRunOnComplete) {
        this.codeToRunOnComplete = codeToRunOnComplete;
        this.codeToRunOnDispatch = codeToRunOnDispatch;
    }

    @Override
    public void onDispatched() {
        if (codeToRunOnDispatch != null) {
            codeToRunOnDispatch.run();
        }
    }

    @Override
    public void onCompleted(T result, Throwable t) {
        if (codeToRunOnComplete != null) {
            codeToRunOnComplete.accept(result, t);
        }
    }
}
