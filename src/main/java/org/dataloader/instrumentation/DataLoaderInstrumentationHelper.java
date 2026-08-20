package org.dataloader.instrumentation;

import org.dataloader.annotations.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

@PublicApi
@NullMarked
public class DataLoaderInstrumentationHelper {

    @SuppressWarnings("RedundantMethodOverride")
    private static final DataLoaderInstrumentationContext<?> NOOP_CTX = new DataLoaderInstrumentationContext<>() {
        @Override
        public void onDispatched() {
        }

        @Override
        public void onCompleted(@Nullable Object result, @Nullable Throwable t) {
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
     * Allows for the more fluent away to return an instrumentation context that runs the specified
     * code on instrumentation step dispatch.
     *
     * @param codeToRun the code to run on dispatch
     * @param <U>       the generic type
     * @return an instrumentation context
     */
    public static <U> DataLoaderInstrumentationContext<U> whenDispatched(Runnable codeToRun) {
        return new SimpleDataLoaderInstrumentationContext<>(codeToRun, null);
    }

    /**
     * Allows for the more fluent away to return an instrumentation context that runs the specified
     * code on instrumentation step completion.
     *
     * @param codeToRun the code to run on completion
     * @param <U>       the generic type
     * @return an instrumentation context
     */
    @SuppressWarnings("NullAway") // NullAway has issues with generic type inference for BiConsumer with nullable type arguments
    public static <U> DataLoaderInstrumentationContext<U> whenCompleted(BiConsumer<@Nullable U, @Nullable Throwable> codeToRun) {
        return new SimpleDataLoaderInstrumentationContext<>(null, codeToRun);
    }


    /**
     * Check the {@link DataLoaderInstrumentationContext} to see if its null and returns a noop if it is or else the original
     * context.  This is a bit of a helper method.
     *
     * @param ic  the context in play
     * @param <T> for two
     * @return a non null context
     */
    public static <T> DataLoaderInstrumentationContext<T> ctxOrNoopCtx(@Nullable DataLoaderInstrumentationContext<T> ic) {
        return ic == null ? noOpCtx() : ic;
    }
}
