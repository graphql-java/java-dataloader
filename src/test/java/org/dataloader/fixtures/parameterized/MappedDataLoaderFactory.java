package org.dataloader.fixtures.parameterized;

import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.fixtures.TestKit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.dataloader.DataLoaderFactory.newMappedDataLoader;
import static org.dataloader.fixtures.TestKit.futureError;

public class MappedDataLoaderFactory implements TestDataLoaderFactory {

    @Override
    public <K> DataLoader<K, K> idLoader(
            DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return newMappedDataLoader((keys) -> {
            loadCalls.add(new ArrayList<>(keys));
            Map<K, K> map = new HashMap<>();
            keys.forEach(k -> map.put(k, k));
            return completedFuture(map);
        }, options);
    }

    @Override
    public <K> DataLoader<K, K> idLoaderWithContext(DataLoaderOptions options, List<Collection<K>> loadCalls, AtomicReference<BatchLoaderEnvironment> environmentREF) {
        return newMappedDataLoader((keys, environment) -> {
            environmentREF.set(environment);
            loadCalls.add(new ArrayList<>(keys));
            Map<K, K> map = new HashMap<>();
            keys.forEach(k -> map.put(k, k));
            return completedFuture(map);
        }, options);
    }

    @Override
    public <K> DataLoader<K, K> idLoaderDelayed(
            DataLoaderOptions options, List<Collection<K>> loadCalls, Duration delay) {
        return newMappedDataLoader(keys -> CompletableFuture.supplyAsync(() -> {
            TestKit.snooze(delay.toMillis());
            loadCalls.add(new ArrayList<>(keys));
            Map<K, K> map = new HashMap<>();
            keys.forEach(k -> map.put(k, k));
            return map;
        }));
    }

    @Override
    public <K> DataLoader<K, K> idLoaderBlowsUps(DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return newMappedDataLoader((keys) -> {
            loadCalls.add(new ArrayList<>(keys));
            return futureError();
        }, options);
    }

    @Override
    public <K> DataLoader<K, Object> idLoaderAllExceptions(
            DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return newMappedDataLoader(keys -> {
            loadCalls.add(new ArrayList<>(keys));
            Map<K, Object> errorByKey = new HashMap<>();
            keys.forEach(k -> errorByKey.put(k, new IllegalStateException("Error")));
            return completedFuture(errorByKey);
        }, options);
    }

    @Override
    public DataLoader<Integer, Object> idLoaderOddEvenExceptions(
            DataLoaderOptions options, List<Collection<Integer>> loadCalls) {
        return newMappedDataLoader(keys -> {
            loadCalls.add(new ArrayList<>(keys));

            Map<Integer, Object> errorByKey = new HashMap<>();
            for (Integer key : keys) {
                if (key % 2 == 0) {
                    errorByKey.put(key, key);
                } else {
                    errorByKey.put(key, new IllegalStateException("Error"));
                }
            }
            return completedFuture(errorByKey);
        }, options);
    }

    @Override
    public DataLoader<String, String> onlyReturnsNValues(int N, DataLoaderOptions options, ArrayList<Object> loadCalls) {
        return newMappedDataLoader(keys -> {
            loadCalls.add(new ArrayList<>(keys));

            Map<String, String> collect = List.copyOf(keys).subList(0, N).stream().collect(Collectors.toMap(
                    k -> k, v -> v
            ));
            return completedFuture(collect);
        }, options);
    }

    @Override
    public DataLoader<String, String> idLoaderReturnsTooMany(int howManyMore, DataLoaderOptions options, ArrayList<Object> loadCalls) {
        return newMappedDataLoader(keys -> {
            loadCalls.add(new ArrayList<>(keys));

            List<String> l = new ArrayList<>(keys);
            for (int i = 0; i < howManyMore; i++) {
                l.add("extra-" + i);
            }

            Map<String, String> collect = l.stream().collect(Collectors.toMap(
                    k -> k, v -> v
            ));
            return completedFuture(collect);
        }, options);
    }
}
