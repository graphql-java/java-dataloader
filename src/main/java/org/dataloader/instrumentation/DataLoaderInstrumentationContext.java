package org.dataloader.instrumentation;

import org.dataloader.annotations.PublicSpi;

import java.util.concurrent.CompletableFuture;

/**
 * When a {@link DataLoaderInstrumentation}.'beginXXX()' method is called then it must return a {@link DataLoaderInstrumentationContext}
 * that will be invoked when the step is first dispatched and then when it completes.  Sometimes this is effectively the same time
 * whereas at other times it's when an asynchronous {@link CompletableFuture} completes.
 * <p>
 * This pattern of construction of an object then call back is intended to allow "timers" to be created that can instrument what has
 * just happened or "loggers" to be called to record what has happened.
 */
@PublicSpi
public interface DataLoaderInstrumentationContext<T> {
    /**
     * This is invoked when the instrumentation step is initially dispatched.  Note this is NOT
     * the same time as the {@link DataLoaderInstrumentation}`beginXXX()` starts, but rather after all the inner
     * work has been done.
     */
    default void onDispatched() {
    }

    /**
     * This is invoked when the instrumentation step is fully completed.
     *
     * @param result the result of the step (which may be null)
     * @param t      this exception will be non-null if an exception was thrown during the step
     */
    default void onCompleted(T result, Throwable t) {
    }
}
