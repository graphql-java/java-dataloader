package org.dataloader.fixtures.parameterized;

import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface TestDataLoaderFactory {
    <K> DataLoader<K, K> idLoader(DataLoaderOptions options, List<Collection<K>> loadCalls);

    <K> DataLoader<K, K> idLoaderBlowsUps(DataLoaderOptions options, List<Collection<K>> loadCalls);

    <K> DataLoader<K, Object> idLoaderAllExceptions(DataLoaderOptions options, List<Collection<K>> loadCalls);

    DataLoader<Integer, Object> idLoaderOddEvenExceptions(DataLoaderOptions options, List<Collection<Integer>> loadCalls);

    DataLoader<String, String> onlyReturnsNValues(int N, DataLoaderOptions options, ArrayList<Object> loadCalls);

    DataLoader<String, String> idLoaderReturnsTooMany(int howManyMore, DataLoaderOptions options, ArrayList<Object> loadCalls);
}
