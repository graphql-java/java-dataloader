package org.dataloader.fixtures;


import com.github.benmanes.caffeine.cache.Cache;
import org.dataloader.ValueCache;
import org.dataloader.impl.CompletableFutureKit;

import java.util.concurrent.CompletableFuture;

public class CaffeineValueCache implements ValueCache<String, Object> {

    public final Cache<String, Object> cache;

    public CaffeineValueCache(Cache<String, Object> cache) {
        this.cache = cache;
    }

    @Override
    public CompletableFuture<Object> get(String key) {
        Object value = cache.getIfPresent(key);
        if (value == null) {
            // we use get exceptions here to indicate not in cache
            return CompletableFutureKit.failedFuture(new RuntimeException(key + " not present"));
        }
        return CompletableFuture.completedFuture(value);
    }

    @Override
    public CompletableFuture<Object> set(String key, Object value) {
        cache.put(key, value);
        return CompletableFuture.completedFuture(value);
    }

    @Override
    public CompletableFuture<Void> delete(String key) {
        cache.invalidate(key);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> clear() {
        cache.invalidateAll();
        return CompletableFuture.completedFuture(null);
    }
}