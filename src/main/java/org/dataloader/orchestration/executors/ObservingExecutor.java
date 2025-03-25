package org.dataloader.orchestration.executors;

import org.dataloader.annotations.Internal;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

@Internal
public class ObservingExecutor<T> implements Executor {

    private final Executor delegate;
    private final T state;
    private final Consumer<T> callback;

    public ObservingExecutor(Executor delegate, T state, Consumer<T> callback) {
        this.delegate = delegate;
        this.state = state;
        this.callback = callback;
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(command);
        callback.accept(state);
    }
}
