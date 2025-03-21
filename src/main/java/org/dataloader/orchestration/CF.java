package org.dataloader.orchestration;

import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

public class CF<T> {

    private AtomicReference<Object> result = new AtomicReference<>();

    private static final Object NULL = new Object();

    private static class ExceptionWrapper {
        private final Throwable throwable;

        private ExceptionWrapper(Throwable throwable) {
            this.throwable = throwable;
        }
    }

    private final LinkedBlockingDeque<CompleteAction<?, ?>> dependedActions = new LinkedBlockingDeque<>();

    private static class CompleteAction<T, V> implements Runnable {
        Executor executor;
        CF<V> toComplete;
        CF<T> src;
        BiFunction<? super T, Throwable, ? extends V> mapperFn;

        public CompleteAction(CF<V> toComplete, CF<T> src, BiFunction<? super T, Throwable, ? extends V> mapperFn, Executor executor) {
            this.toComplete = toComplete;
            this.src = src;
            this.mapperFn = mapperFn;
            this.executor = executor;
        }

        public void execute() {
            if (executor != null) {
                executor.execute(this);
            } else {
                toComplete.completeViaMapper(mapperFn, src.result.get());
            }

        }

        @Override
        public void run() {
            toComplete.completeViaMapper(mapperFn, src.result.get());
            src.dependedActions.remove(this);
        }
    }

    private CF() {

    }

    public <U> CF<U> newIncomplete() {
        return new CF<U>();
    }

    public static <T> CF<T> newComplete(T completed) {
        CF<T> result = new CF<>();
        result.encodeAndSetResult(completed);
        return result;
    }

    public static <T> CF<T> newExceptionally(Throwable e) {
        CF<T> result = new CF<>();
        result.encodeAndSetResult(e);
        return result;
    }


    public <U> CF<U> map(BiFunction<? super T, Throwable, ? extends U> fn) {
        CF<U> newResult = new CF<>();
        dependedActions.push(new CompleteAction<>(newResult, this, fn, null));
        return newResult;
    }

    public <U> CF<U> mapAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        CF<U> newResult = new CF<>();
        dependedActions.push(new CompleteAction<>(newResult, this, fn, executor));
        return newResult;
    }


    public <U> CF<U> compose(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
        CF<U> newResult = new CF<>();
        dependedActions.push(new CompleteAction<>(newResult, this, fn, executor));
        return newResult;
    }


    public boolean complete(T value) {
        boolean success = result.compareAndSet(null, value);
        return success;
    }

    private boolean encodeAndSetResult(Object rawValue) {
        if (rawValue == null) {
            return result.compareAndSet(null, NULL);
        } else if (rawValue instanceof Throwable) {
            return result.compareAndSet(null, new ExceptionWrapper((Throwable) rawValue));
        } else {
            return result.compareAndSet(null, rawValue);
        }
    }

    private Object decodeResult(Object rawValue) {
        if (rawValue instanceof ExceptionWrapper) {
            return ((ExceptionWrapper) rawValue).throwable;
        } else if (rawValue == NULL) {
            return null;
        } else {
            return rawValue;
        }
    }

    private void fireDependentActions() {
        Iterator<CompleteAction<?, ?>> iterator = dependedActions.iterator();
        while (iterator.hasNext()) {
            iterator.next().execute();
        }
    }

    private <T, U> void completeViaMapper(BiFunction<? super T, Throwable, ? extends U> fn, Object encodedResult) {
        try {
            Object decodedResult = decodeResult(encodedResult);
            Object mappedResult = fn.apply(
                    (T) decodedResult,
                    decodedResult instanceof Throwable ? (Throwable) decodedResult : null
            );
            this.result.compareAndSet(null, mappedResult);
        } catch (Throwable t) {
            encodeAndSetResult(t);
        }


    }


}
