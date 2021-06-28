package org.dataloader;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.dataloader.fixtures.CaffeineCachedValueStore;
import org.dataloader.fixtures.CustomCachedValueStore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.awaitility.Awaitility.await;
import static org.dataloader.DataLoaderOptions.newOptions;
import static org.dataloader.fixtures.TestKit.idLoader;
import static org.dataloader.impl.CompletableFutureKit.failedFuture;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class DataLoaderCachedValueStoreTest {

    @Test
    public void test_by_default_we_have_no_value_caching() {
        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions();
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        CompletableFuture<String> fA = identityLoader.load("a");
        CompletableFuture<String> fB = identityLoader.load("b");

        assertFalse(fA.isDone());
        assertFalse(fB.isDone());

        await().until(identityLoader.dispatch()::isDone);
        assertThat(fA.join(), equalTo("a"));
        assertThat(fB.join(), equalTo("b"));

        assertThat(loadCalls, equalTo(singletonList(asList("a", "b"))));

        // futures are still cached but not values

        fA = identityLoader.load("a");
        fB = identityLoader.load("b");

        assertTrue(fA.isDone());
        assertTrue(fB.isDone());

        await().until(identityLoader.dispatch()::isDone);
        assertThat(fA.join(), equalTo("a"));
        assertThat(fB.join(), equalTo("b"));

        assertThat(loadCalls, equalTo(singletonList(asList("a", "b"))));

    }

    @Test
    public void should_accept_a_remote_value_store_for_caching() {
        CustomCachedValueStore customStore = new CustomCachedValueStore();
        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setCachedValueStore(customStore);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        // Fetches as expected

        CompletableFuture<String> fA = identityLoader.load("a");
        CompletableFuture<String> fB = identityLoader.load("b");

        await().until(identityLoader.dispatch()::isDone);
        assertThat(fA.join(), equalTo("a"));
        assertThat(fB.join(), equalTo("b"));

        assertThat(loadCalls, equalTo(singletonList(asList("a", "b"))));
        assertArrayEquals(customStore.store.keySet().toArray(), asList("a", "b").toArray());

        CompletableFuture<String> future3 = identityLoader.load("c");
        CompletableFuture<String> future2a = identityLoader.load("b");

        await().until(identityLoader.dispatch()::isDone);
        assertThat(future3.join(), equalTo("c"));
        assertThat(future2a.join(), equalTo("b"));

        assertThat(loadCalls, equalTo(asList(asList("a", "b"), singletonList("c"))));
        assertArrayEquals(customStore.store.keySet().toArray(), asList("a", "b", "c").toArray());

        // Supports clear

        CompletableFuture<Void> fC = new CompletableFuture<>();
        identityLoader.clear("b", (v, e) -> fC.complete(v));
        await().until(fC::isDone);
        assertArrayEquals(customStore.store.keySet().toArray(), asList("a", "c").toArray());

        // Supports clear all

        CompletableFuture<Void> fCa = new CompletableFuture<>();
        identityLoader.clearAll((v, e) -> fCa.complete(v));
        await().until(fCa::isDone);
        assertArrayEquals(customStore.store.keySet().toArray(), emptyList().toArray());
    }

    @Test
    public void can_use_caffeine_for_caching() {
        //
        // Mostly to prove that some other CACHE library could be used
        // as the backing value cache.  Not really Caffeine specific.
        //
        Cache<String, Object> caffeineCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(100)
                .build();

        CachedValueStore<String, Object> customStore = new CaffeineCachedValueStore(caffeineCache);

        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setCachedValueStore(customStore);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        // Fetches as expected

        CompletableFuture<String> fA = identityLoader.load("a");
        CompletableFuture<String> fB = identityLoader.load("b");

        await().until(identityLoader.dispatch()::isDone);
        assertThat(fA.join(), equalTo("a"));
        assertThat(fB.join(), equalTo("b"));

        assertThat(loadCalls, equalTo(singletonList(asList("a", "b"))));
        assertArrayEquals(caffeineCache.asMap().keySet().toArray(), asList("a", "b").toArray());

        CompletableFuture<String> fC = identityLoader.load("c");
        CompletableFuture<String> fBa = identityLoader.load("b");

        await().until(identityLoader.dispatch()::isDone);
        assertThat(fC.join(), equalTo("c"));
        assertThat(fBa.join(), equalTo("b"));

        assertThat(loadCalls, equalTo(asList(asList("a", "b"), singletonList("c"))));
        assertArrayEquals(caffeineCache.asMap().keySet().toArray(), asList("a", "b", "c").toArray());
    }

    @Test
    public void will_invoke_loader_if_CACHE_CONTAINS_call_throws_exception() {
        CustomCachedValueStore customStore = new CustomCachedValueStore() {
            @Override
            public CompletableFuture<Boolean> containsKey(String key) {
                if (key.equals("a")) {
                    return failedFuture(new IllegalStateException("no A"));
                }
                if (key.equals("c")) {
                    return completedFuture(false);
                }
                return super.containsKey(key);
            }
        };
        customStore.set("a", "Not From Cache A");
        customStore.set("b", "From Cache");
        customStore.set("c", "Not From Cache C");

        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setCachedValueStore(customStore);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        // Fetches as expected

        CompletableFuture<String> fA = identityLoader.load("a");
        CompletableFuture<String> fB = identityLoader.load("b");
        CompletableFuture<String> fC = identityLoader.load("c");

        await().until(identityLoader.dispatch()::isDone);
        assertThat(fA.join(), equalTo("a"));
        assertThat(fB.join(), equalTo("From Cache"));
        assertThat(fC.join(), equalTo("c"));

        // a was not in cache (according to containsKey) and hence needed to be loaded
        assertThat(loadCalls, equalTo(singletonList(asList("a", "c"))));

        // the failed containsKey calls will be SET back into the value cache after batch loading
        assertArrayEquals(customStore.store.keySet().toArray(), asList("a", "b", "c").toArray());
        assertArrayEquals(customStore.store.values().toArray(), asList("a", "From Cache", "c").toArray());
    }

    @Test
    public void will_invoke_loader_if_CACHE_GET_call_throws_exception() {
        CustomCachedValueStore customStore = new CustomCachedValueStore() {

            @Override
            public CompletableFuture<Object> get(String key) {
                if (key.equals("a")) {
                    return failedFuture(new IllegalStateException("no A"));
                }
                return super.get(key);
            }
        };
        customStore.set("a", "Not From Cache");
        customStore.set("b", "From Cache");

        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setCachedValueStore(customStore);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        CompletableFuture<String> fA = identityLoader.load("a");
        CompletableFuture<String> fB = identityLoader.load("b");

        await().until(identityLoader.dispatch()::isDone);
        assertThat(fA.join(), equalTo("a"));
        assertThat(fB.join(), equalTo("From Cache"));

        // a was not in cache (according to get) and hence needed to be loaded
        assertThat(loadCalls, equalTo(singletonList(singletonList("a"))));
    }

    @Test
    public void will_still_work_if_CACHE_SET_call_throws_exception() {
        CustomCachedValueStore customStore = new CustomCachedValueStore() {
            @Override
            public CompletableFuture<Object> set(String key, Object value) {
                if (key.equals("a")) {
                    return failedFuture(new IllegalStateException("no A"));
                }
                return super.set(key, value);
            }
        };

        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setCachedValueStore(customStore);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        CompletableFuture<String> fA = identityLoader.load("a");
        CompletableFuture<String> fB = identityLoader.load("b");

        await().until(identityLoader.dispatch()::isDone);
        assertThat(fA.join(), equalTo("a"));
        assertThat(fB.join(), equalTo("b"));

        // a was not in cache (according to get) and hence needed to be loaded
        assertThat(loadCalls, equalTo(singletonList(asList("a", "b"))));
        assertArrayEquals(customStore.store.keySet().toArray(), singletonList("b").toArray());
    }
}
