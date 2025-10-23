package org.dataloader.fixtures;

import org.dataloader.CacheMap;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class CustomCacheMap implements CacheMap<String, Object> {

    public ConcurrentHashMap<String, CompletableFuture<Object>> stash;

    public CustomCacheMap() {
        stash = new ConcurrentHashMap<>();
    }

    @Override
    public boolean containsKey(String key) {
        return stash.containsKey(key);
    }

    @Override
    public CompletableFuture<Object> get(String key) {
        return stash.get(key);
    }

    @Override
    public Collection<CompletableFuture<Object>> getAll() {
        return stash.values();
    }

    @Override
    public CompletableFuture<Object> putIfAbsentAtomically(String key, CompletableFuture<Object> value) {
        return stash.putIfAbsent(key, value);
    }

    @Override
    public CacheMap<String, Object> delete(String key) {
        stash.remove(key);
        return this;
    }

    @Override
    public CacheMap<String, Object> clear() {
        stash.clear();
        return this;
    }

    @Override
    public int size() {
        return stash.size();
    }
}
