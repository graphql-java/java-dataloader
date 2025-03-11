package org.dataloader;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

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

        Collection<CompletableFuture<Integer>> futures = dataLoader.getCacheMap().getAll();
        assertThat(futures.size(), equalTo(2));
    }

    @Test
    public void should_access_to_future_dependants() {
        DataLoader<Integer, Integer> dataLoader = newDataLoader(keysAsValues());

        dataLoader.load(1).handle((v, t) -> t);
        dataLoader.load(2).handle((v, t) -> t);
        dataLoader.load(1).handle((v, t) -> t);

        Collection<CompletableFuture<Integer>> futures = dataLoader.getCacheMap().getAll();

        List<CompletableFuture<Integer>> futuresList = new ArrayList<>(futures);
        assertThat(futuresList.get(0).getNumberOfDependents(), equalTo(4)); // instrumentation is depending on the CF completing
        assertThat(futuresList.get(1).getNumberOfDependents(), equalTo(2));
    }
}
