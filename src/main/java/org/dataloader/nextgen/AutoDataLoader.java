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
import java.util.function.Consumer;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;

/**
 *
 * @author gkesler
 */
public class AutoDataLoader<K, V> extends DataLoader<K, V> implements AutoCloseable {
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
        this.dispatcher = dispatcher.register(this::dispatchFully);
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }

    @Override
    public void close() throws Exception {
        dispatcher.unregister(this::dispatchFully);
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
    }
    
    protected CompletableFuture<List<V>> dispatchFully () {
        addResult = AutoDataLoader::newResult;
//        return super.dispatch()
//            .thenCompose(value -> {
//                CompletableFuture<List<V>> result = results.peek();
//                result.complete(value);
//                return result;
//            });
        return deepDispatch()
                .thenCompose(value -> {
                    CompletableFuture<List<V>> result = results.peek();
                    result.complete(value);
                    return result;
                });
    }
    
    private CompletableFuture<List<V>> deepDispatch () {
        addResult = AutoDataLoader::newResult;
        return super.dispatch()
            .thenCombine(dispatchDepth() > 0 ? deepDispatch() : completedFuture(emptyList()), 
                (left, right) -> {
                    left.addAll((List<V>)right);
                    return left;
                });
    }
    
    @Override
    public CompletableFuture<List<V>> dispatch() {
        return dispatchResult();
    }
    
    public List<V> dispatchAndJoin() {
        return dispatchResult().join();
//        return super.dispatchAndJoin();
    }
    
    private class Result extends CompletableFuture<List<V>> {
        @Override
        public boolean complete(List<V> value) {
            System.out.println("completing...value=" + value);
            addValue(value);
            if (dispatchDepth() == 0) {
                System.out.println("completed! result=" + result);
                return super.complete(result);
            }
            
            return false;
        }
        
        private void addValue (List<V> value) {
            if (result == null)
                result = value;
            else 
                result.addAll(value);
        }
        
        private List<V> result;
    }
}
