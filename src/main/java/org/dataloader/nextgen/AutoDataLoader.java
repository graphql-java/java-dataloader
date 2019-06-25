/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dataloader.nextgen;

import java.util.ArrayDeque;
import static java.util.Collections.emptyList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import static java.util.concurrent.CompletableFuture.completedFuture;
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
        return super.loadMany(keys, keyContexts); 
    }

    @Override
    public CompletableFuture<V> load(K key, Object keyContext) {
        addResult.accept(this);
        return super.load(key, keyContext);
    }

    @Override
    public void run() {        
        dispatchFully()
            .thenAccept(value -> {
                dispatchResult().complete(value);
                addResult = AutoDataLoader::newResult;                    
            });
    }

    private CompletableFuture<List<V>> dispatchFully () {
        return (dispatchDepth() > 0)
            ? super.dispatch()
                .thenCombine(dispatchFully(), (value, temp) -> {
                    value.addAll(temp);
                    return value;
                })
            : completedFuture(emptyList());
    }
    
    
    @Override
    public CompletableFuture<List<V>> dispatch() {
        return dispatchResult();
    }
    
    public List<V> dispatchAndJoin() {
        return dispatchResult().join();
    }
}
