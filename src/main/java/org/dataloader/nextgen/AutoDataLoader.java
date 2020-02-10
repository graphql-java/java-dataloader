/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dataloader.nextgen;

import java.util.ArrayList;
import static java.util.Collections.emptyList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import static java.util.concurrent.CompletableFuture.completedFuture;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An extension to the existing DataLoader that is automatically triggered 
 * by a background thread when the loading thread awaits for the data to arrive.
 * 
 * @author <a href="https://github.com/gkesler/">Greg Kesler</a>
 */
public class AutoDataLoader<K, V> extends DataLoader<K, V> implements Runnable, AutoCloseable {
    private final Dispatcher dispatcher;
    private volatile int requested;
    private volatile CompletableFuture<List<V>> result = completedFuture(emptyList());
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoDataLoader.class);
    
    public AutoDataLoader(BatchLoader<K, V> batchLoadFunction) {
        this(batchLoadFunction, new Dispatcher());
    }

    public AutoDataLoader(BatchLoader<K, V> batchLoadFunction, Dispatcher dispatcher) {
        this(batchLoadFunction, null, dispatcher);
    }
    
    public AutoDataLoader(BatchLoader<K, V> batchLoadFunction, AutoDataLoaderOptions options) {
        this(batchLoadFunction, options, options.dispatcher());
    }
    
    private AutoDataLoader(BatchLoader<K, V> batchLoadFunction, AutoDataLoaderOptions options, Dispatcher dispatcher) {
        super(batchLoadFunction, options);
        
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

    private synchronized void newResult () {
        result = new Result();
        LOGGER.debug("created new result future");
    }

    @Override
    public synchronized CompletableFuture<V> load(K key, Object keyContext) {
        LOGGER.debug("load requested, key={}", key);
        CompletableFuture<V> load = super.load(key, keyContext);

        if (loaderOptions.batchingEnabled()) {
            LOGGER.debug("requesting dispatch for batched loader");
            // set read/modify/update fence here to ensure correct order of operations
            if (++requested == 1) {
                newResult();
            }

            dispatcher.scheduleBatch(this);
        }

        return load;
    }

    @Override
    public void run() {        
        dispatchFully()
            .thenAccept(value -> {
                synchronized (this) {
                    if (result.complete(value)) {
                        requested = 0;
                        LOGGER.debug("run completed!");
                    }
                }
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
    public int dispatchDepth() {
        int dispatchDepth = super.dispatchDepth();
        LOGGER.debug("dispatchDept={}, requested={}", dispatchDepth, requested);
        return dispatchDepth;
    }
    
    
    @Override
    public synchronized CompletableFuture<List<V>> dispatch() {
        return result;
    }
    
    @Override
    public List<V> dispatchAndJoin() {
        return dispatch().join();
    }
    
    private class Result extends CompletableFuture<List<V>> {
        @Override
        public boolean complete(List<V> value) {
            // set read fence here to ensure correct order of operations
            int requestedCount = requested;
            result.addAll(value);
            int resultSize = result.size();
            LOGGER.debug("completing...requested={}, received={}", requestedCount, resultSize);
            
            return requestedCount == resultSize &&
                    super.complete(result);
        }
        
        private final List<V> result = new ArrayList<>();
    }
}
