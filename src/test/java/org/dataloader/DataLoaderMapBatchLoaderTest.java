package org.dataloader;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.awaitility.Awaitility.await;
import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.dataloader.DataLoaderOptions.newOptions;
import static org.dataloader.TestKit.futureError;
import static org.dataloader.TestKit.listFrom;
import static org.dataloader.impl.CompletableFutureKit.cause;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Much of the tests that related to {@link MappedBatchLoader} also related to
 * {@link org.dataloader.BatchLoader}.  This is white box testing somewhat because we could have repeated
 * ALL the tests in {@link org.dataloader.DataLoaderTest} here as well but chose not to because we KNOW that
 * DataLoader differs only a little in how it handles the 2 types of loader functions. We choose to grab some
 * common functionality for repeat testing and otherwise rely on the very complete other tests.
 */
public class DataLoaderMapBatchLoaderTest {

    MappedBatchLoader<String, String> evensOnlyMappedBatchLoader = (keys) -> {
        Map<String, String> mapOfResults = new HashMap<>();

        AtomicInteger index = new AtomicInteger();
        keys.forEach(k -> {
            int i = index.getAndIncrement();
            if (i % 2 == 0) {
                mapOfResults.put(k, k);
            }
        });
        return CompletableFuture.completedFuture(mapOfResults);
    };

    private static <K, V> DataLoader<K, V> idMapLoader(DataLoaderOptions options, List<Collection<K>> loadCalls) {
        MappedBatchLoader<K, V> kvBatchLoader = (keys) -> {
            loadCalls.add(new ArrayList<>(keys));
            Map<K, V> map = new HashMap<>();
            //noinspection unchecked
            keys.forEach(k -> map.put(k, (V) k));
            return CompletableFuture.completedFuture(map);
        };
        return DataLoaderFactory.newMappedDataLoader(kvBatchLoader, options);
    }

    private static <K, V> DataLoader<K, V> idMapLoaderBlowsUps(
            DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return newDataLoader((keys) -> {
            loadCalls.add(new ArrayList<>(keys));
            return futureError();
        }, options);
    }


    @Test
    public void basic_map_batch_loading() {
        DataLoader<String, String> loader = DataLoaderFactory.newMappedDataLoader(evensOnlyMappedBatchLoader);

        loader.load("A");
        loader.load("B");
        loader.loadMany(asList("C", "D"));

        List<String> results = loader.dispatchAndJoin();

        assertThat(results.size(), equalTo(4));
        assertThat(results, equalTo(asList("A", null, "C", null)));
    }


    @Test
    public void should_map_Batch_multiple_requests() throws ExecutionException, InterruptedException {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = idMapLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Integer> future1 = identityLoader.load(1);
        CompletableFuture<Integer> future2 = identityLoader.load(2);
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo(1));
        assertThat(future2.get(), equalTo(2));
        assertThat(loadCalls, equalTo(singletonList(asList(1, 2))));
    }

    @Test
    public void can_split_max_batch_sizes_correctly() {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = idMapLoader(newOptions().setMaxBatchSize(5), loadCalls);

        for (int i = 0; i < 21; i++) {
            identityLoader.load(i);
        }
        List<Collection<Integer>> expectedCalls = new ArrayList<>();
        expectedCalls.add(listFrom(0, 5));
        expectedCalls.add(listFrom(5, 10));
        expectedCalls.add(listFrom(10, 15));
        expectedCalls.add(listFrom(15, 20));
        expectedCalls.add(listFrom(20, 21));

        List<Integer> result = identityLoader.dispatch().join();

        assertThat(result, equalTo(listFrom(0, 21)));
        assertThat(loadCalls, equalTo(expectedCalls));
    }

    @Test
    public void should_Propagate_error_to_all_loads() {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> errorLoader = idMapLoaderBlowsUps(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Integer> future1 = errorLoader.load(1);
        CompletableFuture<Integer> future2 = errorLoader.load(2);
        errorLoader.dispatch();

        await().until(future1::isDone);

        assertThat(future1.isCompletedExceptionally(), is(true));
        Throwable cause = cause(future1);
        assert cause != null;
        assertThat(cause, instanceOf(IllegalStateException.class));
        assertThat(cause.getMessage(), equalTo("Error"));

        await().until(future2::isDone);
        cause = cause(future2);
        assert cause != null;
        assertThat(cause.getMessage(), equalTo(cause.getMessage()));

        assertThat(loadCalls, equalTo(singletonList(asList(1, 2))));
    }

    @Test
    public void should_work_with_duplicate_keys_when_caching_disabled() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader =
                idMapLoader(newOptions().setCachingEnabled(false), loadCalls);

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        CompletableFuture<String> future3 = identityLoader.load("A");
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone() && future3.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(future3.get(), equalTo("A"));

        // the map batch functions use a set of keys as input and hence remove duplicates unlike list variant
        assertThat(loadCalls, equalTo(singletonList(asList("A", "B"))));
    }

    @Test
    public void should_work_with_duplicate_keys_when_caching_enabled() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader =
                idMapLoader(newOptions().setCachingEnabled(true), loadCalls);

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        CompletableFuture<String> future3 = identityLoader.load("A");
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone() && future3.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(future3.get(), equalTo("A"));
        assertThat(loadCalls, equalTo(singletonList(asList("A", "B"))));
    }


}
