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
@SuppressWarnings("NullableProblems")
public class DataLoaderCacheMapTest {

    private <T> BatchLoader<T, T> keysAsValues() {
        return CompletableFuture::completedFuture;
    }

    @Test
    public void should_provide_all_futures_from_cache() {
        DataLoader<Integer, Integer> dataLoader = newDataLoader(keysAsValues());

        CompletableFuture<Integer> cf1 = dataLoader.load(1);
        CompletableFuture<Integer> cf2 = dataLoader.load(2);
        CompletableFuture<Integer> cf3 = dataLoader.load(3);

        CacheMap<Object, Integer> cacheMap = dataLoader.getCacheMap();
        Collection<CompletableFuture<Integer>> futures = cacheMap.getAll();
        assertThat(futures.size(), equalTo(3));


        assertThat(cacheMap.get(1), equalTo(cf1));
        assertThat(cacheMap.get(2), equalTo(cf2));
        assertThat(cacheMap.get(3), equalTo(cf3));
        assertThat(cacheMap.containsKey(1), equalTo(true));
        assertThat(cacheMap.containsKey(2), equalTo(true));
        assertThat(cacheMap.containsKey(3), equalTo(true));
        assertThat(cacheMap.containsKey(4), equalTo(false));

        cacheMap.delete(1);
        assertThat(cacheMap.containsKey(1), equalTo(false));
        assertThat(cacheMap.containsKey(2), equalTo(true));

        cacheMap.clear();
        assertThat(cacheMap.containsKey(1), equalTo(false));
        assertThat(cacheMap.containsKey(2), equalTo(false));
        assertThat(cacheMap.containsKey(3), equalTo(false));
        assertThat(cacheMap.containsKey(4), equalTo(false));

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
