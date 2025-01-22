package org.dataloader.instrumentation;

import org.dataloader.annotations.PublicApi;

@PublicApi
public class DataLoaderInstrumentationHelper {

    @SuppressWarnings("RedundantMethodOverride")
    private static final DataLoaderInstrumentationContext<?> NOOP_CTX = new DataLoaderInstrumentationContext<>() {
        @Override
        public void onDispatched() {
        }

        @Override
        public void onCompleted(Object result, Throwable t) {
        }
    };

    /**
     * Returns a noop {@link DataLoaderInstrumentationContext} of the right type
     *
     * @param <T> for two
     * @return a noop context
     */
    public static <T> DataLoaderInstrumentationContext<T> noOpCtx() {
        //noinspection unchecked
        return (DataLoaderInstrumentationContext<T>) NOOP_CTX;
    }

    /**
     * A well known noop {@link DataLoaderInstrumentation}
     */
    public static final DataLoaderInstrumentation NOOP_INSTRUMENTATION = new DataLoaderInstrumentation() {
    };

    /**
     * Check the {@link DataLoaderInstrumentationContext} to see if its null and returns a noop if it is or else the original
     * context.  This is a bit of a helper method.
     *
     * @param ic  the context in play
     * @param <T> for two
     * @return a non null context
     */
    public static <T> DataLoaderInstrumentationContext<T> ctxOrNoopCtx(DataLoaderInstrumentationContext<T> ic) {
        return ic == null ? noOpCtx() : ic;
    }
}
