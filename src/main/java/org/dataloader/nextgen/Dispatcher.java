/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dataloader.nextgen;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Magic" class to run AutoDataLoader batched dispatch mechanism when
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
 *    AutoDataLoader<K, V> commonPoolLoader = new AutoDataLoader<>(batchLoader, new AutoDataLoaderOptions());
 * }
 * 
 * To create AutoDataLoader that is limited to run on 1 thread use the folllowing code
 * {@code 
 *    Dispatcher singleThreadDispatcher = new Dispatcher(Executors.newSingleThreadExecutor());
 *    BatchLoader<K, V> batchLoader = ...
 *    AutoDataLoader<K, V> commonPoolLoader = new AutoDataLoader<>(batchLoader, singleThreadDispatcher);
 * }
 * 
 * To create AutoDataLoader with DataLoaderOptions and to choose threading model, use the code
 * below:
 * {@code 
 *    Dispatcher singleThreadDispatcher = new Dispatcher(Executors.newSingleThreadExecutor());
 *    AutoDataLoaderOptions options = new AutoDataLoaderOptions().setDispatcher(singleThreadDispatcher);
 *    BatchLoader<K, V> batchLoader = ...
 *    AutoDataLoader<K, V> commonPoolLoader = new AutoDataLoader<>(batchLoader, options);
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
    private final AtomicReference<State> state = new AtomicReference<>(State.Idle);
    private final Map<AutoDataLoader, Thread> dataLoaders = new WeakHashMap<>();
    private final Queue<Command> commands = new ConcurrentLinkedQueue<>();
    private final Executor executor;
    private final BiFunction<Command, Executor, Command> invoker;

    private static final Logger LOGGER = LoggerFactory.getLogger(Dispatcher.class);
    
    private enum State {
        Idle,
        Running,
        Closing,
        Closed
    }
    
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
    public synchronized Dispatcher register (AutoDataLoader dataLoader) {
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
    public synchronized Dispatcher unregister (AutoDataLoader dataLoader) {
        Objects.requireNonNull(dataLoader);
        
        dataLoaders.remove(dataLoader);
        return this;
    }
    
    private synchronized Optional<Thread> ownerOf (AutoDataLoader dataLoader) {
        return Optional.ofNullable(dataLoaders.get(dataLoader));
    }
    
    /**
     * Requests to schedule batching for the specified data loader
     * @param dataLoader data loader to schedule dispatch
     */
    public void scheduleBatch (AutoDataLoader dataLoader) {
        LOGGER.debug("dispatch requested for {}", dataLoader);
        request(new DispatchCommand(dataLoader));
    }
    
    protected void request (Command command) {
        Objects.requireNonNull(command);
        
        if (state.compareAndSet(State.Idle, State.Running)) {
            LOGGER.debug("scheduling execution for {}", command);
            invoker.apply(command, executor);
        } else if (state.get() == State.Running) {
            LOGGER.debug("enqued command {}", command);
            commands.offer(command);
        } else {
            throw new IllegalStateException("Dispatcher is closed");
        }
    } 
            
    private static boolean isWaiting (Thread thread) {
        switch (thread.getState()) {
            case WAITING:
            case TIMED_WAITING:
                return true;
        }
        
        return false;
    }

    @Override
    public void close() {
        if (state.compareAndSet(State.Running, State.Closing)) {
            LOGGER.debug("close requested...");

            while (state.get() == State.Closing);
        }
        
        state.set(State.Closed);

        LOGGER.debug("closed!");
    }
    
    protected abstract class Command implements Runnable {
        protected abstract void execute ();
                
        @Override
        public void run() {
            try {                
                LOGGER.debug("executing command {}", this);
                
                execute();
            } finally {
                Command next = next();
                if (next != null) {
                    LOGGER.debug("scheduling next command {}", next);
                    invoker.apply(next, executor);
                } else {
                    LOGGER.debug("no more commands");
                    state.set(State.Idle);
                }
                
                LOGGER.debug("finished command {}", this);
            }            
        }
        
        protected Command next () {
            Command next;
            while ((next = commands.poll()) != null && skip(next));
            
            return next;
        }
        
        protected boolean skip (Command next) {
            return equals(next);
        }
        
        public Command forkWith (Executor executor) {
            if (ForkJoinTask.inForkJoinPool()) {
                ForkJoinTask.adapt(this).fork();
                return this;
            } else {
                return executeWith(executor);
            }
        }
        
        public Command executeWith (Executor executor) {
            executor.execute(this);
            return this;
        }
    }
    
    private class DispatchCommand extends Command {
        private final AutoDataLoader dataLoader;
        private final Thread owner;

        DispatchCommand (AutoDataLoader dataLoader) {
            Objects.requireNonNull(dataLoader);
            
            this.dataLoader = dataLoader;
            this.owner = ownerOf(dataLoader)
                .orElseThrow(() -> new IllegalArgumentException("Unknown DataLoader " + dataLoader));
        }
        
        @Override
        protected void execute() {
            // wait while thread is running
            State s;
            while ((s = state.get()) == State.Running && !isWaiting(owner));
            
            if (s == State.Running) {
                // and now do the dispatch
                dataLoader.run();
            }
        }

        @Override
        protected boolean skip(Command next) {
            return dataLoader.dispatchDepth() == 0 && super.skip(next);
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
