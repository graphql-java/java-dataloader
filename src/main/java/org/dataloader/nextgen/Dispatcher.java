/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dataloader.nextgen;

import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author <a href="https://github.com/gkesler/">Greg Kesler</a>
 */
public class Dispatcher implements AutoCloseable {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final Map<AutoDataLoader, Thread> dataLoaders = new ConcurrentHashMap<>();
    private final Queue<Command> commands = new ConcurrentLinkedQueue<>();
    private volatile Executor executor;
    private final UnaryOperator<Command> invoker;

    private static final Executor CLOSED_EXECUTOR = new Executor() {
        @Override
        public void execute(Runnable command) {
            throw new IllegalStateException("Can't execute new command, Dispatcher is closed");
        }
    };
    private static final Logger LOGGER = LoggerFactory.getLogger(Dispatcher.class);
    
    public Dispatcher (Executor executor) {
        this(executor, Invoker::invoke);
    }
    
    public Dispatcher (ForkJoinPool executor) {
        this(executor, command -> (Command)command.fork());
    }

    public Dispatcher () {
        this(ForkJoinPool.commonPool());
    }
    
    private Dispatcher (Executor executor, UnaryOperator<Command> invoker) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(invoker);
        
        this.executor = executor;
        this.invoker = invoker;
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }

    public Dispatcher register (AutoDataLoader dataLoader) {
        Objects.requireNonNull(dataLoader);
        
        dataLoaders.put(dataLoader, Thread.currentThread());
        return this;
    }
    
    public Dispatcher unregister (AutoDataLoader dataLoader) {
        Objects.requireNonNull(dataLoader);
        
        dataLoaders.remove(dataLoader);
        return this;
    }
    
    public void dispatch (AutoDataLoader dataLoader) {
        LOGGER.trace("dispatch requested for {}", dataLoader);
        request(new DispatchCommand(dataLoader));
    }
    
    protected void request (Command command) {
        Objects.requireNonNull(command);
        
        if (running.compareAndSet(false, true)) {
            LOGGER.trace("scheduling execution for {}", command);
            executor.execute(command);
        } else {
            LOGGER.trace("enqued command {}", command);
            commands.offer(command);
        }
    } 
            
    private static boolean isRunning (Thread thread) {
        switch (thread.getState()) {
            case NEW:
            case RUNNABLE:
                return true;
        }
        
        return false;
    }

    @Override
    public void close() {
        LOGGER.trace("close requested...");
        // close executor
        executor = CLOSED_EXECUTOR;
        closing.set(true);
        // what for all dispatched tasks to stop
        while (running.compareAndSet(true, true));
        LOGGER.trace("closed!");
    }
    
    private static class Invoker {
        static Command invoke (Command command) {
            Objects.requireNonNull(command);

            command.run();
            return command;
        }
    }
    
    protected abstract class Command extends RecursiveAction implements RunnableFuture<Void> {
        protected abstract void execute ();
                
        @Override
        protected void compute() {
            try {
                LOGGER.trace("executing command {}", this);
                execute();
            } finally {
//                Command next = next();
                Command next = commands.poll();
                if (next != null) {
                    LOGGER.trace("scheduling next command {}", next);
                    invoker
                        .apply(next)
                        .quietlyJoin();
                } else {
                    LOGGER.trace("no more commands");
                    running.lazySet(false);
                }
                LOGGER.debug("finished command {}", this);
            }            
        }

        protected Command next () {
            Command next;
            while ((next = commands.poll()) != null && equals(next));
            
            return next;
        }
        
        @Override
        public void run() {
            invoke();
        }
    }
    
    private class DispatchCommand extends Command {
        private final AutoDataLoader dataLoader;
        private final Thread owner;

        DispatchCommand (AutoDataLoader dataLoader) {
            Objects.requireNonNull(dataLoader);
            
            Thread thread = dataLoaders.get(dataLoader);
            if (thread == null)
                throw new IllegalArgumentException("Unknown DataLoader " + dataLoader);
            
            this.dataLoader = dataLoader;
            this.owner = thread;
        }
        
        @Override
        protected void execute() {
            // wait while thread is running
            while (closing.compareAndSet(false, false) && isRunning(owner));
            
            // and now do the dispatch
            dataLoader.run();
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + Objects.hashCode(this.dataLoader);
            hash = 59 * hash + Objects.hashCode(this.owner);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final DispatchCommand other = (DispatchCommand) obj;
            if (!Objects.equals(this.dataLoader, other.dataLoader)) {
                return false;
            }
            if (!Objects.equals(this.owner, other.owner)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "DispatchCommand{" + "dataLoader=" + dataLoader + ", owner=" + owner + '}';
        }
    }
}
