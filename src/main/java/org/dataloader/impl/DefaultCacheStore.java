package org.dataloader.impl;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.dataloader.CacheStore;
import org.dataloader.Internal;

@Internal
public class DefaultCacheStore<K, V> implements CacheStore<K, V> {

  private final Map<K, V> store = new ConcurrentHashMap<>();

  @Override
  public CompletableFuture<Boolean> has(K key) {
    return CompletableFuture.completedFuture(store.containsKey(key));
  }

  @Override
  public CompletableFuture<V> get(K key) {
    return CompletableFuture.completedFuture(store.get(key));
  }

  @Override
  public CompletableFuture<V> set(K key, V value) {
    return CompletableFuture.completedFuture(store.put(key, value));
  }

  @Override
  public CompletableFuture<Void> delete(K key) {
    store.remove(key);
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> clear() {
    store.clear();
    return CompletableFuture.completedFuture(null);
  }
}
