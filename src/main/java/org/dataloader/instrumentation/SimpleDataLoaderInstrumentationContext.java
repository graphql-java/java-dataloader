package org.dataloader.instrumentation;


import org.dataloader.annotations.Internal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * A simple implementation of {@link DataLoaderInstrumentationContext}
 */
@Internal
@NullMarked
class SimpleDataLoaderInstrumentationContext<T> implements DataLoaderInstrumentationContext<T> {

    private final @Nullable BiConsumer<@Nullable T, @Nullable Throwable> codeToRunOnComplete;
    private final @Nullable Runnable codeToRunOnDispatch;

    SimpleDataLoaderInstrumentationContext(@Nullable Runnable codeToRunOnDispatch, @Nullable BiConsumer<@Nullable T, @Nullable Throwable> codeToRunOnComplete) {
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
    public void onCompleted(@Nullable T result, @Nullable Throwable t) {
        if (codeToRunOnComplete != null) {
            codeToRunOnComplete.accept(result, t);
        }
    }
}
