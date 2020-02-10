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
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
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
public class Dispatcher implements Runnable, AutoCloseable {
    private final Map<AutoDataLoader, Thread> dataLoaders = new WeakHashMap<>();
    private final Queue<Command> commands = new ConcurrentLinkedQueue<>();
    private final Executor executor;
    
    private static final int IDLE = 0;
    private static final int RUNNING = 1;
    private static final int CLOSING = 2;
    private static final int CLOSED = 3;
    
    private volatile int state = IDLE;    
    private static final AtomicIntegerFieldUpdater<Dispatcher> STATE = AtomicIntegerFieldUpdater
        .newUpdater(Dispatcher.class, "state");
    
    private static final Logger LOGGER = LoggerFactory.getLogger(Dispatcher.class);
    
    /**
     * Creates a new instance that uses ForkJoinPool.commonPool()
     */
    public Dispatcher () {
        this(ForkJoinPool.commonPool());
    }
    
    /**
     * Creates a new instance with specified Executor.
     * 
     * @param executor executor to run Dispatcher tasks
     */
    public Dispatcher (Executor executor) {
        Objects.requireNonNull(executor);
        
        this.executor = executor;
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
        
        commands.offer(command);
        if (STATE.compareAndSet(this, IDLE, RUNNING)) {
            LOGGER.debug("scheduling execution for pended commands");
            executor.execute(this);
            // FIXME: should I allow multithreaded dispatch for different dataloaders?
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
        if (STATE.compareAndSet(this, RUNNING, CLOSING)) {
            request(new CloseCommand());
            LOGGER.debug("close requested...");

            int s;
            while ((s = state) == CLOSING);
        }

        state = CLOSED;
        LOGGER.debug("closed!");
    }

    @Override
    public void run() {
        try {
            int s;
            Command command;            
            while ((s = state) == RUNNING && (command = commands.poll()) != null) {
                command.run();                
            }
        } finally {
            LOGGER.debug("no more commands");
            STATE.compareAndSet(this, RUNNING, IDLE);
        }
    }
    
    protected abstract class Command implements Runnable {
        protected abstract void execute ();
        protected void handle (Throwable e) {
            LOGGER.warn("{}", e);
        }
                
        @Override
        public void run() {
            try {                
                LOGGER.debug("executing command {}", this);                
                execute();
            } catch (Throwable e) {
                handle(e);
            } finally {
                LOGGER.debug("finished command {}", this);
            }            
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
            if (!isWaiting(owner)) {
                // wait while thread is running
                commands.offer(this);
            } else {
                // and now do the dispatch
                dataLoader.run();
            }
        }

        @Override
        public String toString() {
            return "DispatchCommand{" + "dataLoader=" + dataLoader + ", owner=" + owner + '}';
        }
    }
    
    private class CloseCommand extends Command {
        @Override
        protected void execute() {
            commands.clear();
            STATE.lazySet(Dispatcher.this, CLOSED);
        }

        @Override
        public String toString() {
            return "CloseCommand{" + '}';
        }
    }
}
