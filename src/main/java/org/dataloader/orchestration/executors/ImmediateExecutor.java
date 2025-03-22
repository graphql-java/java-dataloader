package org.dataloader.orchestration.executors;

import org.dataloader.annotations.Internal;

import java.util.concurrent.Executor;

@Internal
public class ImmediateExecutor implements Executor {
    public static final ImmediateExecutor INSTANCE = new ImmediateExecutor();

    @Override
    public void execute(Runnable command) {
        command.run();
    }
}
