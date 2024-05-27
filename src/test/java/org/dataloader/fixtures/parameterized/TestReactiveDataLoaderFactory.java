package org.dataloader.fixtures.parameterized;

import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;

import java.util.Collection;
import java.util.List;

public interface TestReactiveDataLoaderFactory {
    <K> DataLoader<K, K> idLoaderBlowsUpsAfterN(int N, DataLoaderOptions options, List<Collection<K>> loadCalls);
}
