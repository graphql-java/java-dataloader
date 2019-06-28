/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dataloader.nextgen;

import static java.util.Collections.emptyList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import static java.util.concurrent.CompletableFuture.completedFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
    private volatile List<V> received = emptyList();
    private volatile CompletableFuture<List<V>> result = completedFuture(emptyList());
    private volatile Consumer<AutoDataLoader<K, V>> resultCreator = AutoDataLoader::newResult;
    
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

    private void newResult () {
        requested = 0;
        received = new CopyOnWriteArrayList<>();
        result = new CompletableFuture<List<V>>();
        resultCreator = o -> {};
        LOGGER.debug("created new result future");
    }
    
    @Override
    public CompletableFuture<List<V>> loadMany(List<K> keys, List<Object> keyContexts) {
        LOGGER.debug("loadMany requested, keys={}", keys);
        return dispatchIfNecessary(() -> super.loadMany(keys, keyContexts));
    }

    @Override
    public CompletableFuture<V> load(K key, Object keyContext) {
        LOGGER.debug("load requested, key={}", key);
        return dispatchIfNecessary(() -> super.load(key, keyContext));
    }

    private synchronized <E> CompletableFuture<E> dispatchIfNecessary (Supplier<CompletableFuture<E>> loader) {
        CompletableFuture<E> load = loader.get();
        
        if (loaderOptions.batchingEnabled()) {
            LOGGER.debug("requesting dispatch for batched loader");
            resultCreator.accept(this);
            dispatcher.scheduleBatch(this);
        }
        
        return load;
    }
    
    @Override
    public void run() {        
        dispatchFully()
            .thenAccept(value -> {
                received.addAll(value);
                int receivedSize = received.size();
                LOGGER.debug("completing...requested={}, received={}", requested, receivedSize);
                if (requested == receivedSize) {
                    if (!result.complete(received)) {
                        LOGGER.debug("attempt to complete already completed result");
                    }

                    resultCreator = AutoDataLoader::newResult;
                    LOGGER.debug("run completed!");
                }
            });
    }

    private CompletableFuture<List<V>> dispatchFully () {
        int dispatchDepth;
        synchronized (this) {
            dispatchDepth = dispatchDepth();
            requested += dispatchDepth;
        }
        LOGGER.debug("dispatchFully...dispatchDept={}, requested={}", dispatchDepth, requested);
        
        return (dispatchDepth > 0)
            ? super.dispatch()
                .thenCombine(dispatchFully(), (value, temp) -> {
                    value.addAll(temp);
                    return value;
                })
            : completedFuture(emptyList());
    }
    
    
    @Override
    public CompletableFuture<List<V>> dispatch() {
        return result;
    }
    
    @Override
    public List<V> dispatchAndJoin() {
        return result.join();
    }
}
