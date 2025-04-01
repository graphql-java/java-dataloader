package org.dataloader.fixtures.parameterized;

import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DelegatingDataLoader;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DelegatingDataLoaderFactory implements TestDataLoaderFactory {
    // its delegates all the way down to the turtles
    private final TestDataLoaderFactory delegateFactory;

    public DelegatingDataLoaderFactory(TestDataLoaderFactory delegateFactory) {
        this.delegateFactory = delegateFactory;
    }

    @Override
    public String toString() {
        return "DelegatingDataLoaderFactory{" +
                "delegateFactory=" + delegateFactory +
                '}';
    }

    @Override
    public TestDataLoaderFactory unwrap() {
        return delegateFactory.unwrap();
    }

    private <K, V> DataLoader<K, V> mkDelegateDataLoader(DataLoader<K, V> dataLoader) {
        return new DelegatingDataLoader<>(dataLoader);
    }

    @Override
    public <K> DataLoader<K, K> idLoader(DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return mkDelegateDataLoader(delegateFactory.idLoader(options, loadCalls));
    }

    @Override
    public <K> DataLoader<K, K> idLoaderDelayed(DataLoaderOptions options, List<Collection<K>> loadCalls, Duration delay) {
        return mkDelegateDataLoader(delegateFactory.idLoaderDelayed(options, loadCalls, delay));
    }

    @Override
    public <K> DataLoader<K, K> idLoaderBlowsUps(
            DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return mkDelegateDataLoader(delegateFactory.idLoaderBlowsUps(options, loadCalls));
    }

    @Override
    public <K> DataLoader<K, Object> idLoaderAllExceptions(DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return mkDelegateDataLoader(delegateFactory.idLoaderAllExceptions(options, loadCalls));
    }

    @Override
    public DataLoader<Integer, Object> idLoaderOddEvenExceptions(DataLoaderOptions options, List<Collection<Integer>> loadCalls) {
        return mkDelegateDataLoader(delegateFactory.idLoaderOddEvenExceptions(options, loadCalls));
    }

    @Override
    public DataLoader<String, String> onlyReturnsNValues(int N, DataLoaderOptions options, ArrayList<Object> loadCalls) {
        return mkDelegateDataLoader(delegateFactory.onlyReturnsNValues(N, options, loadCalls));
    }

    @Override
    public DataLoader<String, String> idLoaderReturnsTooMany(int howManyMore, DataLoaderOptions options, ArrayList<Object> loadCalls) {
        return mkDelegateDataLoader(delegateFactory.idLoaderReturnsTooMany(howManyMore, options, loadCalls));
    }
}
