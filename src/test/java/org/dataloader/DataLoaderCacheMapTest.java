package org.dataloader;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Tests for cacheMap functionality..
 */
public class DataLoaderCacheMapTest {

    private <T> BatchLoader<T, T> keysAsValues() {
        return CompletableFuture::completedFuture;
    }

    @Test
    public void should_provide_all_futures_from_cache() {
        DataLoader<Integer, Integer> dataLoader = newDataLoader(keysAsValues());

        dataLoader.load(1);
        dataLoader.load(2);
        dataLoader.load(1);

        Collection<CompletableFuture<Integer>> futures = dataLoader.getCacheFutures();
        assertThat(futures.size(), equalTo(2));
    }

    @Test
    public void should_access_to_future_dependants() {
        DataLoader<Integer, Integer> dataLoader = newDataLoader(keysAsValues());

        dataLoader.load(1).handle((v, t) -> t);
        dataLoader.load(2).handle((v, t) -> t);
        dataLoader.load(1).handle((v, t) -> t);

        Collection<CompletableFuture<Integer>> futures = dataLoader.getCacheFutures();

        List<CompletableFuture<Integer>> futuresList = new ArrayList<>(futures);
        assertThat(futuresList.get(0).getNumberOfDependents(), equalTo(2));
        assertThat(futuresList.get(1).getNumberOfDependents(), equalTo(1));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void should_throw_exception__on_mutation_attempt() {
        DataLoader<Integer, Integer> dataLoader = newDataLoader(keysAsValues());

        dataLoader.load(1).handle((v, t) -> t);

        Collection<CompletableFuture<Integer>> futures = dataLoader.getCacheFutures();

        futures.add(CompletableFuture.completedFuture(2));
    }
}
