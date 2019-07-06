package org.dataloader.caching;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.dataloader.CacheMap;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.awaitility.Awaitility.await;
import static org.dataloader.DataLoaderOptions.newOptions;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThat;

public class CaffieneCacheTest {

    static class CaffieneCache implements CacheMap<String, CompletableFuture<String>> {

        Cache<String, CompletableFuture<String>> caffeineCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                //.refreshAfterWrite(1, TimeUnit.MINUTES)
                .build();

        @NonNull
        private String buildValue(@NonNull String key) {
            return key;
        }

        @Override
        public boolean containsKey(String key) {
            return caffeineCache.getIfPresent(key) != null;
        }

        @Override
        public CompletableFuture<String> get(String key) {
            return caffeineCache.getIfPresent(key);
        }

        @Override
        public CacheMap<String, CompletableFuture<String>> set(String key, CompletableFuture<String> value) {
            caffeineCache.put(key, value);
            return this;
        }

        @Override
        public CacheMap<String, CompletableFuture<String>> delete(String key) {
            caffeineCache.invalidate(key);
            return this;
        }

        @Override
        public CacheMap<String, CompletableFuture<String>> clear() {
            caffeineCache.invalidateAll();
            return this;
        }

        public Set<String> keySet() {
            ConcurrentMap<String, CompletableFuture<String>> map = caffeineCache.asMap();
            return new TreeSet<>(map.keySet());
        }
    }


    private static <K, V> DataLoader<K, V> idLoader(DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return DataLoader.newDataLoader(keys -> {
            loadCalls.add(new ArrayList<>(keys));
            @SuppressWarnings("unchecked")
            List<V> values = keys.stream()
                    .map(k -> (V) k)
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(values);
        }, options);
    }

    @Test
    public void can_run_truly_custom_cache() throws ExecutionException, InterruptedException {
        CaffieneCache customMap = new CaffieneCache();
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setCacheMap(customMap);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        // Fetches as expected

        CompletableFuture future1 = identityLoader.load("a");
        CompletableFuture future2 = identityLoader.load("b");
        CompletableFuture<List<String>> composite = identityLoader.dispatch();

        await().until(composite::isDone);
        assertThat(future1.get(), equalTo("a"));
        assertThat(future2.get(), equalTo("b"));

        assertThat(loadCalls, equalTo(singletonList(asList("a", "b"))));
        assertArrayEquals(customMap.keySet().toArray(), asList("a", "b").toArray());

        CompletableFuture future3 = identityLoader.load("c");
        CompletableFuture future2a = identityLoader.load("b");
        composite = identityLoader.dispatch();

        await().until(composite::isDone);
        assertThat(future3.get(), equalTo("c"));
        assertThat(future2a.get(), equalTo("b"));

        assertThat(loadCalls, equalTo(asList(asList("a", "b"), singletonList("c"))));
        assertArrayEquals(customMap.keySet().toArray(), asList("a", "b", "c").toArray());

        // Supports clear

        identityLoader.clear("b");
        assertArrayEquals(customMap.keySet().toArray(), asList("a", "c").toArray());

        CompletableFuture future2b = identityLoader.load("b");
        composite = identityLoader.dispatch();

        await().until(composite::isDone);
        assertThat(future2b.get(), equalTo("b"));
        assertThat(loadCalls, equalTo(asList(asList("a", "b"),
                singletonList("c"), singletonList("b"))));

        assertArrayEquals(customMap.keySet().toArray(), asList("a", "b", "c").toArray());

        // Supports clear all

        identityLoader.clearAll();

        assertArrayEquals(customMap.keySet().toArray(), emptyList().toArray());

    }
}
