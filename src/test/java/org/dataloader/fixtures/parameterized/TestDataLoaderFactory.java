package org.dataloader.fixtures.parameterized;

import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public interface TestDataLoaderFactory {
    <K> DataLoader<K, K> idLoader(DataLoaderOptions options, List<Collection<K>> loadCalls);

    <K> DataLoader<K, K> idLoaderDelayed(DataLoaderOptions options, List<Collection<K>> loadCalls, Duration delay);

    <K> DataLoader<K, K> idLoaderBlowsUps(DataLoaderOptions options, List<Collection<K>> loadCalls);

    <K> DataLoader<K, Object> idLoaderAllExceptions(DataLoaderOptions options, List<Collection<K>> loadCalls);

    DataLoader<Integer, Object> idLoaderOddEvenExceptions(DataLoaderOptions options, List<Collection<Integer>> loadCalls);

    DataLoader<String, String> onlyReturnsNValues(int N, DataLoaderOptions options, ArrayList<Object> loadCalls);

    DataLoader<String, String> idLoaderReturnsTooMany(int howManyMore, DataLoaderOptions options, ArrayList<Object> loadCalls);

    // similar to above but batch loaders with context

    <K> DataLoader<K, K> idLoaderWithContext(DataLoaderOptions options, List<Collection<K>> loadCalls, AtomicReference<BatchLoaderEnvironment> environmentREF);


    // Convenience methods

    default <K> DataLoader<K, K> idLoader(DataLoaderOptions options) {
        return idLoader(options, new ArrayList<>());
    }

    default <K> DataLoader<K, K> idLoader(List<Collection<K>> calls) {
        return idLoader(null, calls);
    }
    default <K> DataLoader<K, K> idLoader() {
        return idLoader(null, new ArrayList<>());
    }

    default <K> DataLoader<K, K> idLoaderDelayed(Duration delay) {
        return idLoaderDelayed(null, new ArrayList<>(), delay);
    }

    default TestDataLoaderFactory unwrap() {
        return this;
    }
}
