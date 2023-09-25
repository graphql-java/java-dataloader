package org.dataloader.fixtures;

import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.stream.Collectors.toList;
import static org.dataloader.impl.CompletableFutureKit.failedFuture;

public class TestKit {

    public static <T> BatchLoader<T, T> keysAsValues() {
        return CompletableFuture::completedFuture;
    }

    public static <K, V> BatchLoader<K, V> keysAsValues(List<List<K>> loadCalls) {
        return keys -> {
            List<K> ks = new ArrayList<>(keys);
            loadCalls.add(ks);
            @SuppressWarnings("unchecked")
            List<V> values = keys.stream()
                    .map(k -> (V) k)
                    .collect(toList());
            return CompletableFuture.completedFuture(values);
        };
    }

    public static <K, V> BatchLoader<K, V> keysAsValuesAsync(Duration delay) {
        return keysAsValuesAsync(new ArrayList<>(), delay);
    }

    public static <K, V> BatchLoader<K, V> keysAsValuesAsync(List<List<K>> loadCalls, Duration delay) {
        return keys -> CompletableFuture.supplyAsync(() -> {
            snooze(delay.toMillis());
            List<K> ks = new ArrayList<>(keys);
            loadCalls.add(ks);
            @SuppressWarnings("unchecked")
            List<V> values = keys.stream()
                    .map(k -> (V) k)
                    .collect(toList());
            return values;
        });
    }

    public static <K, V> DataLoader<K, V> idLoader() {
        return idLoader(null, new ArrayList<>());
    }

    public static <K, V> DataLoader<K, V> idLoader(List<List<K>> loadCalls) {
        return idLoader(null, loadCalls);
    }

    public static <K, V> DataLoader<K, V> idLoader(DataLoaderOptions options, List<List<K>> loadCalls) {
        return DataLoaderFactory.newDataLoader(keysAsValues(loadCalls), options);
    }

    public static <K, V> DataLoader<K, V> idLoaderAsync(Duration delay) {
        return idLoaderAsync(null, new ArrayList<>(), delay);
    }

    public static <K, V> DataLoader<K, V> idLoaderAsync(DataLoaderOptions options, List<List<K>> loadCalls, Duration delay) {
        return DataLoaderFactory.newDataLoader(keysAsValuesAsync(loadCalls, delay), options);
    }

    public static Collection<Integer> listFrom(int i, int max) {
        List<Integer> ints = new ArrayList<>();
        for (int j = i; j < max; j++) {
            ints.add(j);
        }
        return ints;
    }

    public static <V> CompletableFuture<V> futureError() {
        return failedFuture(new IllegalStateException("Error"));
    }

    public static void snooze(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    public static <T> List<T> sort(Collection<? extends T> collection) {
        return collection.stream().sorted().collect(toList());
    }
}
