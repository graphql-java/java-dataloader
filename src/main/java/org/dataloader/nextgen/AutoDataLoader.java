/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dataloader.nextgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import static java.util.Collections.emptyList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import java.util.function.Consumer;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;

/**
 *
 * @author gkesler
 */
public class AutoDataLoader<K, V> extends DataLoader<K, V> implements Runnable, AutoCloseable {
    private final Dispatcher dispatcher;
    private final Deque<CompletableFuture<List<V>>> results = new ArrayDeque<>();
    private volatile Consumer<AutoDataLoader<K, V>> addResult;
    
    public AutoDataLoader(BatchLoader<K, V> batchLoadFunction) {
        this(batchLoadFunction, new Dispatcher());
    }

    public AutoDataLoader(BatchLoader<K, V> batchLoadFunction, Dispatcher dispatcher) {
        this(batchLoadFunction, null, dispatcher);
    }
    
    public AutoDataLoader(BatchLoader<K, V> batchLoadFunction, AutoDataLoaderOptions options) {
        this(batchLoadFunction, options, options.getDispatcher());
    }
    
    public AutoDataLoader(BatchLoader<K, V> batchLoadFunction, AutoDataLoaderOptions options, Dispatcher dispatcher) {
        super(batchLoadFunction, options);

        newResult();
        
        Objects.requireNonNull(dispatcher);
        this.dispatcher = dispatcher.register(this);
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }

    @Override
    public void close() throws Exception {
        dispatcher.unregister(this);
    }

    private void newResult () {
        results.push(new CompletableFuture<List<V>>());
        addResult = o -> {};
    }
    
    public CompletableFuture<List<V>> dispatchResult () {
        return results.peek();
    }
    
    @Override
    public CompletableFuture<List<V>> loadMany(List<K> keys, List<Object> keyContexts) {
        addResult.accept(this);
        return super.loadMany(keys, keyContexts); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public CompletableFuture<V> load(K key, Object keyContext) {
        addResult.accept(this);
        return super.load(key, keyContext);
//                .thenApply(value -> {
//                    System.out.println("load: " + Thread.currentThread().getName() + " key=" + key + ", value=" + value + ", depth=" + dispatchDepth());
//                    return value;
//                });
    }

    @Override
    public void run() {
        if (!dispatchResult().isDone()) {
            dispatchFully().join();
        }
    }
    
    protected CompletableFuture<List<V>> dispatchFully () {
        return deepDispatch()
                .thenCompose(value -> {
                    CompletableFuture<List<V>> result = dispatchResult();
                    if (result.complete(value)) {
                        addResult = AutoDataLoader::newResult;
//                        System.out.println("dispatchFully: " + Thread.currentThread().getName() + " completed, value=" + value + ", depth=" + dispatchDepth());
                    }
                    
                    return result;
                });
    }
    
    private CompletableFuture<List<V>> deepDispatch () {
        addResult = AutoDataLoader::newResult;
        return super.dispatch()
            .thenApply(value -> {
                System.out.println("deepDispatch: " + Thread.currentThread().getName() + " value=" + value + ", depth=" + dispatchDepth());
                return value;
            })
//            .thenCombine(dispatchDepth() > 0 ? deepDispatch() : completedFuture(emptyList()), 
//                (left, right) -> {
//                    left.addAll((List<V>)right);
//                    return left;
//                });
            .thenApply(value -> {
                if (dispatchDepth() > 0) {
                    value.addAll(deepDispatch().join());
                }
                
                return value;
            });
    }
    
    @Override
    public CompletableFuture<List<V>> dispatch() {
        return dispatchResult();
    }
    
    public List<V> dispatchAndJoin() {
        return dispatchResult().join();
    }
}
