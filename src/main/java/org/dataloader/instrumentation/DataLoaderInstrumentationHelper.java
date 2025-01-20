package org.dataloader.instrumentation;

public class DataLoaderInstrumentationHelper {

    private static final DataLoaderInstrumentationContext<?> NOOP_CTX = new DataLoaderInstrumentationContext<>() {
        @Override
        public void onDispatched() {
        }

        @Override
        public void onCompleted(Object result, Throwable t) {
        }
    };

    public static <T> DataLoaderInstrumentationContext<T> noOpCtx() {
        //noinspection unchecked
        return (DataLoaderInstrumentationContext<T>) NOOP_CTX;
    }

    public static final DataLoaderInstrumentation NOOP_INSTRUMENTATION = new DataLoaderInstrumentation() {
    };

    /**
     * Check the {@link DataLoaderInstrumentationContext} to see if its null and returns a noop if it is or else the original
     * context
     *
     * @param ic  the context in play
     * @param <T> for two
     * @return a non null context
     */
    public static <T> DataLoaderInstrumentationContext<T> ctxOrNoopCtx(DataLoaderInstrumentationContext<T> ic) {
        return ic == null ? noOpCtx() : ic;
    }
}
