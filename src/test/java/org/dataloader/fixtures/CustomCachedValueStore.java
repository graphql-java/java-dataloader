package org.dataloader.fixtures;


import org.dataloader.CachedValueStore;
import org.dataloader.impl.CompletableFutureKit;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class CustomCachedValueStore implements CachedValueStore<String, Object> {

    public final Map<String, Object> store = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Object> get(String key) {
        if (!store.containsKey(key)) {
            return CompletableFutureKit.failedFuture(new RuntimeException("The key is missing"));
        }
        return CompletableFuture.completedFuture(store.get(key));
    }

    @Override
    public CompletableFuture<Object> set(String key, Object value) {
        store.put(key, value);
        return CompletableFuture.completedFuture(value);
    }

    @Override
    public CompletableFuture<Void> delete(String key) {
        store.remove(key);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> clear() {
        store.clear();
        return CompletableFuture.completedFuture(null);
    }
}