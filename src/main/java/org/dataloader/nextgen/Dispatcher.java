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
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Magic" class to run AutoDataLoader batched dispatch mechanizm when
 * the data loader client thread becomes awaiting for results. 
 * Upon the requestor starts waiting, the temporary dispatcher thread
 * kicks off and triggers AutoDataLoader.run method. Inferred load requests
 * are also batched into the final result available via overriden AutoDataLoader.dispatch()
 * method.
 * 
 * To create AutoDataLoader that fully utilizes ForkJoinPool lightweight threading
 * use the following code
 * {@code 
 *    Dispatcher commonPoolDispatcher = new Dispatcher();
 *    BatchLoader<K, V> batchLoader = ...
 *    AutoDataLoader<K, V> commonPoolLoader = new AutoDataLoader<>(batchLoader, commonPoolDispatcher);
 * }
 * 
 * To create AutoDataLoader that is limited to run on 1 thread use the folllowing code
 * {@code 
 *    Dispatcher commonPoolDispatcher = new Dispatcher(Executors.newSingleThreadExecutor());
 *    BatchLoader<K, V> batchLoader = ...
 *    AutoDataLoader<K, V> commonPoolLoader = new AutoDataLoader<>(batchLoader, commonPoolDispatcher);
 * }
 * 
 * @see AutoDataLoader#load(java.lang.Object) 
 * @see AutoDataLoader#loadMany(java.util.List) 
 * @see AutoDataLoader#run() 
 * @see AutoDataLoader#dispatch() 
 * 
 * @author <a href="https://github.com/gkesler/">Greg Kesler</a>
 */
public class Dispatcher implements AutoCloseable {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final Map<AutoDataLoader, Thread> dataLoaders = new ConcurrentHashMap<>();
    private final Queue<Command> commands = new ConcurrentLinkedQueue<>();
    private volatile Executor executor;
    private final BiFunction<Command, Executor, Command> invoker;

    private static final Executor CLOSED_EXECUTOR = new Executor() {
        @Override
        public void execute(Runnable command) {
            throw new IllegalStateException("Can't execute new command, Dispatcher is closed");
        }
    };
    private static final Logger LOGGER = LoggerFactory.getLogger(Dispatcher.class);
    
    /**
     * Creates a new instance with specified Executor.
     * 
     * @param executor executor to run Dispatcher tasks
     */
    public Dispatcher (Executor executor) {
        this(executor, Command::executeWith);
    }
    
    /**
     * Creates a new instance with specified ForkJoinPool.
     * 
     * @param executor executor to run Dispatcher tasks
     */
    public Dispatcher (ForkJoinPool executor) {
        this(executor, Command::forkWith);
    }

    /**
     * Creates a new instance that uses ForkJoinPool.commonPool()
     * 
     * @param executor executor to run Dispatcher tasks
     */
    public Dispatcher () {
        this(ForkJoinPool.commonPool());
    }
    
    private Dispatcher (Executor executor, BiFunction<Command, Executor, Command> invoker) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(invoker);
        
        this.executor = executor;
        this.invoker = invoker;
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }

    /**
     * Registers an new AutoDataLoader with this Dispatcher.
     * Dispatcher will associate current thread with the new data loader
     * and it will monitor the state in order to trigger data loader dispatch
     * 
     * @param dataLoader a data loader to monitor
     * @return this instance to allow method chaining
     */
    public Dispatcher register (AutoDataLoader dataLoader) {
        Objects.requireNonNull(dataLoader);
        
        dataLoaders.put(dataLoader, Thread.currentThread());
        return this;
    }
    
    /**
     * Unregisters a AutoDataLoader from this Dispatcher.
     * 
     * @param dataLoader a data loader to unregister
     * @return this instance to allow method chaining
     */
    public Dispatcher unregister (AutoDataLoader dataLoader) {
        Objects.requireNonNull(dataLoader);
        
        dataLoaders.remove(dataLoader);
        return this;
    }
    
    /**
     * Requests to schedule batching for the specified data loader
     * @param dataLoader data loader to schedule dispatch
     */
    public void addToBatch (AutoDataLoader dataLoader) {
        LOGGER.trace("dispatch requested for {}", dataLoader);
        request(new DispatchCommand(dataLoader));
    }
    
    protected void request (Command command) {
        Objects.requireNonNull(command);
        
        if (running.compareAndSet(false, true)) {
            LOGGER.trace("scheduling execution for {}", command);
            invoker.apply(command, executor);
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
    
    protected abstract class Command extends RecursiveAction implements RunnableFuture<Void> {
        protected abstract void execute ();
                
        @Override
        protected void compute() {
            try {
                LOGGER.trace("executing command {}", this);
                execute();
            } finally {
                Command next = next();
                if (next != null) {
                    LOGGER.trace("scheduling next command {}", next);
                    invoker.apply(next, executor);
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
        
        public Command forkWith (Executor executor) {
            return ForkJoinTask.inForkJoinPool()
                ? (Command)fork()
                : executeWith(executor);
        }
        
        public Command executeWith (Executor executor) {
            executor.execute(this);
            return this;
        }
    }
    
    private class DispatchCommand extends Command {
        private final AutoDataLoader dataLoader;
        private final Thread requestor;

        DispatchCommand (AutoDataLoader dataLoader) {
            Objects.requireNonNull(dataLoader);
            
            Thread thread = dataLoaders.get(dataLoader);
            if (thread == null)
                throw new IllegalArgumentException("Unknown DataLoader " + dataLoader);
            
            this.dataLoader = dataLoader;
            this.requestor = thread;
        }
        
        @Override
        protected void execute() {
            // wait while thread is running
            while (closing.compareAndSet(false, false) && isRunning(requestor));
            
            // and now do the dispatch
            dataLoader.run();
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + Objects.hashCode(this.dataLoader);
            hash = 59 * hash + Objects.hashCode(this.requestor);
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
            if (!Objects.equals(this.requestor, other.requestor)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "DispatchCommand{" + "dataLoader=" + dataLoader + ", owner=" + requestor + '}';
        }
    }
}
