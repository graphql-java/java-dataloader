package org.dataloader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.dataloader.impl.CompletableFutureKit.failedFuture;

public class TestKit {

    public static Collection<Integer> listFrom(int i, int max) {
        List<Integer> ints = new ArrayList<>();
        for (int j = i; j < max; j++) {
            ints.add(j);
        }
        return ints;
    }

    public static <K, V> DataLoader<K, V> idLoader(DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return DataLoaderFactory.newDataLoader(keys -> {
            loadCalls.add(new ArrayList<>(keys));
            @SuppressWarnings("unchecked")
            List<V> values = keys.stream()
                    .map(k -> (V) k)
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(values);
        }, options);
    }

    public static <K, V> DataLoader<K, V> idLoaderBlowsUps(
            DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return newDataLoader(keys -> {
            loadCalls.add(new ArrayList<>(keys));
            return TestKit.futureError();
        }, options);
    }

    public static CacheKey<JsonObject> getJsonObjectCacheMapFn() {
        return key -> key.stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .sorted()
                .collect(Collectors.joining());
    }


    static <V> CompletableFuture<V> futureError() {
        return failedFuture(new IllegalStateException("Error"));
    }
}
