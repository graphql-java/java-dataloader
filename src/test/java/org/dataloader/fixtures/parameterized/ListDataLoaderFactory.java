package org.dataloader.fixtures.parameterized;

import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.fixtures.TestKit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.dataloader.DataLoaderFactory.newDataLoader;

public class ListDataLoaderFactory implements TestDataLoaderFactory {
    @Override
    public <K> DataLoader<K, K> idLoader(DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return newDataLoader(keys -> {
            loadCalls.add(new ArrayList<>(keys));
            return completedFuture(keys);
        }, options);
    }

    @Override
    public <K> DataLoader<K, K> idLoaderWithContext(DataLoaderOptions options, List<Collection<K>> loadCalls, AtomicReference<BatchLoaderEnvironment> environmentREF) {
        return newDataLoader((keys, env) -> {
            environmentREF.set(env);
            loadCalls.add(new ArrayList<>(keys));
            return completedFuture(keys);
        }, options);
    }

    @Override
    public <K> DataLoader<K, K> idLoaderDelayed(DataLoaderOptions options, List<Collection<K>> loadCalls, Duration delay) {
        return newDataLoader(keys -> CompletableFuture.supplyAsync(() -> {
            TestKit.snooze(delay.toMillis());
            loadCalls.add(new ArrayList<>(keys));
            return keys;
        }));
    }

    @Override
    public <K> DataLoader<K, K> idLoaderBlowsUps(
            DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return newDataLoader(keys -> {
            loadCalls.add(new ArrayList<>(keys));
            return TestKit.futureError();
        }, options);
    }

    @Override
    public <K> DataLoader<K, Object> idLoaderAllExceptions(DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return newDataLoader(keys -> {
            loadCalls.add(new ArrayList<>(keys));

            List<Object> errors = keys.stream().map(k -> new IllegalStateException("Error")).collect(Collectors.toList());
            return completedFuture(errors);
        }, options);
    }

    @Override
    public DataLoader<Integer, Object> idLoaderOddEvenExceptions(DataLoaderOptions options, List<Collection<Integer>> loadCalls) {
        return newDataLoader(keys -> {
            loadCalls.add(new ArrayList<>(keys));

            List<Object> errors = new ArrayList<>();
            for (Integer key : keys) {
                if (key % 2 == 0) {
                    errors.add(key);
                } else {
                    errors.add(new IllegalStateException("Error"));
                }
            }
            return completedFuture(errors);
        }, options);
    }

    @Override
    public DataLoader<String, String> onlyReturnsNValues(int N, DataLoaderOptions options, ArrayList<Object> loadCalls) {
        return newDataLoader(keys -> {
            loadCalls.add(new ArrayList<>(keys));
            return completedFuture(keys.subList(0, N));
        }, options);
    }

    @Override
    public DataLoader<String, String> idLoaderReturnsTooMany(int howManyMore, DataLoaderOptions options, ArrayList<Object> loadCalls) {
        return newDataLoader(keys -> {
            loadCalls.add(new ArrayList<>(keys));
            List<String> l = new ArrayList<>(keys);
            for (int i = 0; i < howManyMore; i++) {
                l.add("extra-" + i);
            }
            return completedFuture(l);
        }, options);
    }
}
