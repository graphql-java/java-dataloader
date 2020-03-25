package org.dataloader;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.awaitility.Awaitility.await;
import static org.dataloader.DataLoaderOptions.newOptions;
import static org.dataloader.TestKit.getJsonObjectCacheMapFn;
import static org.dataloader.TestKit.idLoader;
import static org.dataloader.TestKit.idLoaderBlowsUps;
import static org.dataloader.impl.CompletableFutureKit.cause;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThat;

public class DataLoaderCacheTest {

    @Test
    public void should_Cache_repeated_requests() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(asList("A", "B"))));

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future3 = identityLoader.load("C");
        identityLoader.dispatch();

        await().until(() -> future1a.isDone() && future3.isDone());
        assertThat(future1a.get(), equalTo("A"));
        assertThat(future3.get(), equalTo("C"));
        assertThat(loadCalls, equalTo(asList(asList("A", "B"), singletonList("C"))));

        CompletableFuture<String> future1b = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
        CompletableFuture<String> future3a = identityLoader.load("C");
        identityLoader.dispatch();

        await().until(() -> future1b.isDone() && future2a.isDone() && future3a.isDone());
        assertThat(future1b.get(), equalTo("A"));
        assertThat(future2a.get(), equalTo("B"));
        assertThat(future3a.get(), equalTo("C"));
        assertThat(loadCalls, equalTo(asList(asList("A", "B"), singletonList("C"))));
    }

    @Test
    public void should_Accept_a_custom_cache_map_implementation() throws ExecutionException, InterruptedException {
        CustomCacheMap customMap = new CustomCacheMap();
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setCacheMap(customMap);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        // Fetches as expected

        CompletableFuture<?> future1 = identityLoader.load("a");
        CompletableFuture<?> future2 = identityLoader.load("b");
        CompletableFuture<List<String>> composite = identityLoader.dispatch().getPromisedResults();

        await().until(composite::isDone);
        assertThat(future1.get(), equalTo("a"));
        assertThat(future2.get(), equalTo("b"));

        assertThat(loadCalls, equalTo(singletonList(asList("a", "b"))));
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "b").toArray());

        CompletableFuture<?> future3 = identityLoader.load("c");
        CompletableFuture<?> future2a = identityLoader.load("b");
        composite = identityLoader.dispatch().getPromisedResults();

        await().until(composite::isDone);
        assertThat(future3.get(), equalTo("c"));
        assertThat(future2a.get(), equalTo("b"));

        assertThat(loadCalls, equalTo(asList(asList("a", "b"), singletonList("c"))));
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "b", "c").toArray());

        // Supports clear

        identityLoader.clear("b");
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "c").toArray());

        CompletableFuture<?> future2b = identityLoader.load("b");
        composite = identityLoader.dispatch().getPromisedResults();

        await().until(composite::isDone);
        assertThat(future2b.get(), equalTo("b"));
        assertThat(loadCalls, equalTo(asList(asList("a", "b"),
                singletonList("c"), singletonList("b"))));
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "c", "b").toArray());

        // Supports clear all

        identityLoader.clearAll();
        assertArrayEquals(customMap.stash.keySet().toArray(), emptyList().toArray());
    }

    @Test
    public void should_allow_values_extracted_from_cache_on_load() {
        CustomCacheMap customMap = new CustomCacheMap();
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setCacheMap(customMap);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        customMap.set("a", "cachedVal"); // will prevent a batch load

        CompletableFuture<?> future1 = identityLoader.load("a");
        CompletableFuture<?> future2 = identityLoader.load("b");
        CompletableFuture<List<String>> composite = identityLoader.dispatch().getPromisedResults();

        await().until(composite::isDone);
        assertThat(future1.join(), equalTo("cachedVal"));
        assertThat(future2.join(), equalTo("b"));

        assertThat(loadCalls, equalTo(singletonList(singletonList("b"))));
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "b").toArray());
    }

    @Test
    public void should_allow_promise_map_to_be_used() {
        CustomCacheMap customMap = new CustomCacheMap();
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setPromiseCacheMap(customMap);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        customMap.set("a", CompletableFuture.completedFuture("customValue"));

        CompletableFuture<?> future1 = identityLoader.load("a");
        CompletableFuture<?> future2 = identityLoader.load("b");
        CompletableFuture<List<String>> composite = identityLoader.dispatch().getPromisedResults();

        await().until(composite::isDone);
        assertThat(future1.join(), equalTo("customValue"));
        assertThat(future2.join(), equalTo("b"));

        assertThat(loadCalls, equalTo(singletonList(singletonList("b"))));
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "b").toArray());
    }

    @Test
    public void should_Cache_on_redispatch() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<String> future1 = identityLoader.load("A");
        identityLoader.dispatch();

        CompletableFuture<List<String>> future2 = identityLoader.loadMany(asList("A", "B"));
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo(asList("A", "B")));
        assertThat(loadCalls, equalTo(asList(singletonList("A"), singletonList("B"))));
    }

    @Test
    public void should_Clear_single_value_in_loader() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(asList("A", "B"))));

        identityLoader.clear("A");

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1a.isDone() && future2a.isDone());
        assertThat(future1a.get(), equalTo("A"));
        assertThat(future2a.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(asList(asList("A", "B"), singletonList("A"))));
    }

    @Test
    public void should_Clear_all_values_in_loader() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(asList("A", "B"))));

        identityLoader.clearAll();

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1a.isDone() && future2a.isDone());
        assertThat(future1a.get(), equalTo("A"));
        assertThat(future2a.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(asList(asList("A", "B"), asList("A", "B"))));
    }

    @Test
    public void should_Allow_priming_the_cache() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        identityLoader.prime("A", "A");

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(singletonList("B"))));
    }

    @Test
    public void should_Not_prime_keys_that_already_exist() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        identityLoader.prime("A", "X");

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        CompletableFuture<List<String>> composite = identityLoader.dispatch().getPromisedResults();

        await().until(composite::isDone);
        assertThat(future1.get(), equalTo("X"));
        assertThat(future2.get(), equalTo("B"));

        identityLoader.prime("A", "Y");
        identityLoader.prime("B", "Y");

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
        CompletableFuture<List<String>> composite2 = identityLoader.dispatch().getPromisedResults();

        await().until(composite2::isDone);
        assertThat(future1a.get(), equalTo("X"));
        assertThat(future2a.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(singletonList("B"))));
    }

    @Test
    public void should_Allow_to_forcefully_prime_the_cache() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        identityLoader.prime("A", "X");

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        CompletableFuture<List<String>> composite = identityLoader.dispatch().getPromisedResults();

        await().until(composite::isDone);
        assertThat(future1.get(), equalTo("X"));
        assertThat(future2.get(), equalTo("B"));

        identityLoader.clear("A").prime("A", "Y");
        identityLoader.clear("B").prime("B", "Y");

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
        CompletableFuture<List<String>> composite2 = identityLoader.dispatch().getPromisedResults();

        await().until(composite2::isDone);
        assertThat(future1a.get(), equalTo("Y"));
        assertThat(future2a.get(), equalTo("Y"));
        assertThat(loadCalls, equalTo(singletonList(singletonList("B"))));
    }

    @Test
    public void should_not_Cache_failed_fetches_on_complete_failure() {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> errorLoader = idLoaderBlowsUps(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Integer> future1 = errorLoader.load(1);
        errorLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(cause(future1), instanceOf(IllegalStateException.class));

        CompletableFuture<Integer> future2 = errorLoader.load(1);
        errorLoader.dispatch();

        await().until(future2::isDone);
        assertThat(future2.isCompletedExceptionally(), is(true));
        assertThat(cause(future2), instanceOf(IllegalStateException.class));
        assertThat(loadCalls, equalTo(asList(singletonList(1), singletonList(1))));
    }

    @Test
    public void should_Clear_values_from_cache_after_errors() {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> errorLoader = idLoaderBlowsUps(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Integer> future1 = errorLoader.load(1);
        future1.handle((value, t) -> {
            if (t != null) {
                // Presumably determine if this error is transient, and only clear the cache in that case.
                errorLoader.clear(1);
            }
            return null;
        });
        errorLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(cause(future1), instanceOf(IllegalStateException.class));

        CompletableFuture<Integer> future2 = errorLoader.load(1);
        future2.handle((value, t) -> {
            if (t != null) {
                // Again, only do this if you can determine the error is transient.
                errorLoader.clear(1);
            }
            return null;
        });
        errorLoader.dispatch();

        await().until(future2::isDone);
        assertThat(future2.isCompletedExceptionally(), is(true));
        assertThat(cause(future2), instanceOf(IllegalStateException.class));
        assertThat(loadCalls, equalTo(asList(singletonList(1), singletonList(1))));
    }


    @Test
    public void should_Allow_priming_the_cache_with_an_object_key() throws ExecutionException, InterruptedException {
        List<Collection<JsonObject>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setCacheKeyFunction(getJsonObjectCacheMapFn());
        DataLoader<JsonObject, JsonObject> identityLoader = idLoader(options, loadCalls);

        JsonObject key1 = new JsonObject().put("id", 123);
        JsonObject key2 = new JsonObject().put("id", 123);

        identityLoader.prime(key1, key1);

        CompletableFuture<JsonObject> future1 = identityLoader.load(key1);
        CompletableFuture<JsonObject> future2 = identityLoader.load(key2);
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(loadCalls, equalTo(emptyList()));
        assertThat(future1.get(), equalTo(key1));
        assertThat(future2.get(), equalTo(key1));
    }

    @Test
    public void should_Handle_priming_the_cache_with_an_error() {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        identityLoader.prime(1, new IllegalStateException("Error"));

        CompletableFuture<Integer> future1 = identityLoader.load(1);
        identityLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(cause(future1), instanceOf(IllegalStateException.class));
        assertThat(loadCalls, equalTo(emptyList()));
    }

}
