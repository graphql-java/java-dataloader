/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dataloader.nextgen;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;

/**
 *
 * @author <a href="https://github.com/gkesler/">Greg Kesler</a>
 */
public class Dispatcher extends RecursiveAction implements RunnableFuture<Void>, Executor, AutoCloseable {
    private final AtomicBoolean running;
    private final Map<Runnable, Thread> dispatchers = new ConcurrentHashMap<>();
    private final Map<Thread, Collection<ForkJoinTask<?>>> pendingTasks = new ConcurrentHashMap<>();
    private final UnaryOperator<Collection<ForkJoinTask<?>>> invokeAll;

    public Dispatcher (Executor executor) {
        this(executor, Invoker::invokeAll);
    }
    
    public Dispatcher (ForkJoinPool executor) {
        this(executor, ForkJoinTask::invokeAll);
    }

    public Dispatcher () {
        this(ForkJoinPool.commonPool());
    }
    
    private Dispatcher (Executor executor, UnaryOperator<Collection<ForkJoinTask<?>>> tasksInvoker) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(tasksInvoker);
        
        this.invokeAll = tasksInvoker;
        this.running = new AtomicBoolean(true);
                
        executor.execute(this);
    }

    public Dispatcher register (Runnable dispatcher) {
        Objects.requireNonNull(dispatcher);
        
        dispatchers.put(dispatcher, Thread.currentThread());
        return this;
    }
    
    public Dispatcher unregister (Runnable dispatcher) {
        Objects.requireNonNull(dispatcher);
        
        dispatchers.remove(dispatcher);
        return this;
    }
    
    private static boolean isWaiting (Thread thread) {
        switch (thread.getState()) {
            case NEW:
            case RUNNABLE:
                return false;
        }
        
        return true;
    }
    
    private static <T> T defaultIfNull (T value, T defaultValue) {
        return (value == null)
            ? defaultValue
            : value;
    }
    
    @Override
    protected void compute() {
        while (running.compareAndSet(true, true)) {
            // first group dispatchers by their threads
            Map<Thread, List<Runnable>> waiters = dispatchers
                .entrySet()
                .stream()
                .filter(e -> isWaiting(e.getValue()))
                // owning thread is waiting
                .collect(groupingBy(Map.Entry::getValue, mapping(Map.Entry::getKey, toList())));
            
            // collect dispatchers for this thread first
            // then collect pending tasks if any
            List<ForkJoinTask<?>> tasks = waiters
                .entrySet()
                .stream()
                .flatMap(e -> Stream
                    .concat(
                        e.getValue()
                            .stream()
                            .map(Dispatcher::wrap), 
                        defaultIfNull(pendingTasks.remove(e.getKey()), Collections.<ForkJoinTask<?>>emptyList())
                            .stream()
                    )
                )
                .collect(toList());
            
            // start tasks execution
            invokeAll
                .apply(tasks)
                .forEach(ForkJoinTask::quietlyJoin);

            // force yield to other threads
            Thread.yield();
        }
    }
    
    @Override
    public void run() {
        invoke();
    }
    
    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command);
        
        pendingTasks
            .computeIfAbsent(Thread.currentThread(), t -> new ArrayDeque<>())
            .add(wrap(command));
    }
    
    private static ForkJoinTask<?> wrap (Runnable r) {
        return (r instanceof ForkJoinTask)
            ? (ForkJoinTask<?>)r
            : adapt(r);
    }

    @Override
    public void close() {
        // request to close
        running.set(false);
        // and wait for completion
        quietlyJoin();
    }
    
    private static class Invoker {
        static <T extends ForkJoinTask<?>> Collection<T> invokeAll (Collection<T> tasks) {
            Objects.requireNonNull(tasks);

            tasks
                .stream()
                .map(Objects::requireNonNull)
                .forEach(r -> ((Runnable)r).run())/*
                .forEach(ForkJoinTask::quietlyJoin)*/;

            return tasks;
        }
    }
}
