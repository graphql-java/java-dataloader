package org.dataloader.orchestration;

import java.util.concurrent.Executor;

class ImmediateExecutor implements Executor {
    static final ImmediateExecutor INSTANCE = new ImmediateExecutor();

    @Override
    public void execute(Runnable command) {
        command.run();
    }
}
