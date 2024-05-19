package org.dataloader;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.dataloader.fixtures.CaffeineValueCache;
import org.dataloader.fixtures.CustomValueCache;
import org.dataloader.impl.DataLoaderAssertionException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.awaitility.Awaitility.await;
import static org.dataloader.DataLoaderOptions.newOptions;
import static org.dataloader.fixtures.TestKit.idLoader;
import static org.dataloader.fixtures.TestKit.snooze;
import static org.dataloader.fixtures.TestKit.sort;
import static org.dataloader.impl.CompletableFutureKit.failedFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataLoaderValueCacheTest {

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
        loadCalls.clear();

        fA = identityLoader.load("a");
        fB = identityLoader.load("b");

        assertTrue(fA.isDone());
        assertTrue(fB.isDone());

        await().until(identityLoader.dispatch()::isDone);
        assertThat(fA.join(), equalTo("a"));
        assertThat(fB.join(), equalTo("b"));

        assertThat(loadCalls, equalTo(emptyList()));
    }

    @Test
    public void should_accept_a_remote_value_store_for_caching() {
        CustomValueCache customValueCache = new CustomValueCache();
        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setValueCache(customValueCache);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        // Fetches as expected

        CompletableFuture<String> fA = identityLoader.load("a");
        CompletableFuture<String> fB = identityLoader.load("b");

        await().until(identityLoader.dispatch()::isDone);
        assertThat(fA.join(), equalTo("a"));
        assertThat(fB.join(), equalTo("b"));

        assertThat(loadCalls, equalTo(singletonList(asList("a", "b"))));
        assertArrayEquals(customValueCache.store.keySet().toArray(), asList("a", "b").toArray());

        CompletableFuture<String> future3 = identityLoader.load("c");
        CompletableFuture<String> future2a = identityLoader.load("b");

        await().until(identityLoader.dispatch()::isDone);
        assertThat(future3.join(), equalTo("c"));
        assertThat(future2a.join(), equalTo("b"));

        assertThat(loadCalls, equalTo(asList(asList("a", "b"), singletonList("c"))));
        assertArrayEquals(customValueCache.store.keySet().toArray(), asList("a", "b", "c").toArray());

        // Supports clear

        CompletableFuture<Void> fC = new CompletableFuture<>();
        identityLoader.clear("b", (v, e) -> fC.complete(v));
        await().until(fC::isDone);
        assertArrayEquals(customValueCache.store.keySet().toArray(), asList("a", "c").toArray());

        // Supports clear all

        CompletableFuture<Void> fCa = new CompletableFuture<>();
        identityLoader.clearAll((v, e) -> fCa.complete(v));
        await().until(fCa::isDone);
        assertArrayEquals(customValueCache.store.keySet().toArray(), emptyList().toArray());
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

        ValueCache<String, Object> caffeineValueCache = new CaffeineValueCache(caffeineCache);

        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setValueCache(caffeineValueCache);
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
    public void will_invoke_loader_if_CACHE_GET_call_throws_exception() {
        CustomValueCache customValueCache = new CustomValueCache() {

            @Override
            public CompletableFuture<Object> get(String key) {
                if (key.equals("a")) {
                    return failedFuture(new IllegalStateException("no A"));
                }
                return super.get(key);
            }
        };
        customValueCache.set("a", "Not From Cache");
        customValueCache.set("b", "From Cache");

        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setValueCache(customValueCache);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        CompletableFuture<String> fA = identityLoader.load("a");
        CompletableFuture<String> fB = identityLoader.load("b");

        await().until(identityLoader.dispatch()::isDone);
        assertThat(fA.join(), equalTo("a"));
        assertThat(fB.join(), equalTo("From Cache"));

        // "a" was not in cache (according to get) and hence needed to be loaded
        assertThat(loadCalls, equalTo(singletonList(singletonList("a"))));
    }

    @Test
    public void will_still_work_if_CACHE_SET_call_throws_exception() {
        CustomValueCache customValueCache = new CustomValueCache() {
            @Override
            public CompletableFuture<Object> set(String key, Object value) {
                if (key.equals("a")) {
                    return failedFuture(new IllegalStateException("no A"));
                }
                return super.set(key, value);
            }
        };

        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setValueCache(customValueCache);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        CompletableFuture<String> fA = identityLoader.load("a");
        CompletableFuture<String> fB = identityLoader.load("b");

        await().until(identityLoader.dispatch()::isDone);
        assertThat(fA.join(), equalTo("a"));
        assertThat(fB.join(), equalTo("b"));

        // "a" was not in cache (according to get) and hence needed to be loaded
        assertThat(loadCalls, equalTo(singletonList(asList("a", "b"))));
        assertArrayEquals(customValueCache.store.keySet().toArray(), singletonList("b").toArray());
    }

    @Test
    public void caching_can_take_some_time_complete() {
        CustomValueCache customValueCache = new CustomValueCache() {

            @Override
            public CompletableFuture<Object> get(String key) {
                if (key.startsWith("miss")) {
                    return CompletableFuture.supplyAsync(() -> {
                        snooze(1000);
                        throw new IllegalStateException("no a in cache");
                    });
                } else {
                    return CompletableFuture.supplyAsync(() -> {
                        snooze(1000);
                        return key;
                    });
                }
            }

        };


        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setValueCache(customValueCache);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        CompletableFuture<String> fA = identityLoader.load("a");
        CompletableFuture<String> fB = identityLoader.load("b");
        CompletableFuture<String> fC = identityLoader.load("missC");
        CompletableFuture<String> fD = identityLoader.load("missD");

        await().until(identityLoader.dispatch()::isDone);

        assertThat(fA.join(), equalTo("a"));
        assertThat(fB.join(), equalTo("b"));
        assertThat(fC.join(), equalTo("missC"));
        assertThat(fD.join(), equalTo("missD"));

        assertThat(loadCalls, equalTo(singletonList(asList("missC", "missD"))));
    }

    @Test
    public void batch_caching_works_as_expected() {
        CustomValueCache customValueCache = new CustomValueCache() {

            @Override
            public CompletableFuture<List<Try<Object>>> getValues(List<String> keys) {
                List<Try<Object>> cacheCalls = new ArrayList<>();
                for (String key : keys) {
                    if (key.startsWith("miss")) {
                        cacheCalls.add(Try.alwaysFailed());
                    } else {
                        cacheCalls.add(Try.succeeded(key));
                    }
                }
                return CompletableFuture.supplyAsync(() -> {
                    snooze(1000);
                    return cacheCalls;
                });
            }
        };


        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setValueCache(customValueCache);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        CompletableFuture<String> fA = identityLoader.load("a");
        CompletableFuture<String> fB = identityLoader.load("b");
        CompletableFuture<String> fC = identityLoader.load("missC");
        CompletableFuture<String> fD = identityLoader.load("missD");

        await().until(identityLoader.dispatch()::isDone);

        assertThat(fA.join(), equalTo("a"));
        assertThat(fB.join(), equalTo("b"));
        assertThat(fC.join(), equalTo("missC"));
        assertThat(fD.join(), equalTo("missD"));

        assertThat(loadCalls, equalTo(singletonList(asList("missC", "missD"))));

        List<Object> values = new ArrayList<>(customValueCache.asMap().values());
        // it will only set back in values that are missed - it won't set in values that successfully
        // came out of the cache
        assertThat(values, equalTo(asList("missC", "missD")));
    }

    @Test
    public void assertions_will_be_thrown_if_the_cache_does_not_follow_contract() {
        CustomValueCache customValueCache = new CustomValueCache() {

            @Override
            public CompletableFuture<List<Try<Object>>> getValues(List<String> keys) {
                List<Try<Object>> cacheCalls = new ArrayList<>();
                for (String key : keys) {
                    if (key.startsWith("miss")) {
                        cacheCalls.add(Try.alwaysFailed());
                    } else {
                        cacheCalls.add(Try.succeeded(key));
                    }
                }
                List<Try<Object>> renegOnContract = cacheCalls.subList(1, cacheCalls.size() - 1);
                return CompletableFuture.completedFuture(renegOnContract);
            }
        };

        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setValueCache(customValueCache);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        CompletableFuture<String> fA = identityLoader.load("a");
        CompletableFuture<String> fB = identityLoader.load("b");
        CompletableFuture<String> fC = identityLoader.load("missC");
        CompletableFuture<String> fD = identityLoader.load("missD");

        await().until(identityLoader.dispatch()::isDone);

        assertTrue(isAssertionException(fA));
        assertTrue(isAssertionException(fB));
        assertTrue(isAssertionException(fC));
        assertTrue(isAssertionException(fD));
    }

    private boolean isAssertionException(CompletableFuture<String> fA) {
        Throwable throwable = Try.tryFuture(fA).join().getThrowable();
        return throwable instanceof DataLoaderAssertionException;
    }


    @Test
    public void if_caching_is_off_its_never_hit() {
        AtomicInteger getCalls = new AtomicInteger();
        CustomValueCache customValueCache = new CustomValueCache() {

            @Override
            public CompletableFuture<Object> get(String key) {
                getCalls.incrementAndGet();
                return super.get(key);
            }
        };

        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setValueCache(customValueCache).setCachingEnabled(false);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        CompletableFuture<String> fA = identityLoader.load("a");
        CompletableFuture<String> fB = identityLoader.load("b");
        CompletableFuture<String> fC = identityLoader.load("missC");
        CompletableFuture<String> fD = identityLoader.load("missD");

        await().until(identityLoader.dispatch()::isDone);

        assertThat(fA.join(), equalTo("a"));
        assertThat(fB.join(), equalTo("b"));
        assertThat(fC.join(), equalTo("missC"));
        assertThat(fD.join(), equalTo("missD"));

        assertThat(loadCalls, equalTo(singletonList(asList("a", "b", "missC", "missD"))));
        assertThat(getCalls.get(), equalTo(0));
        assertTrue(customValueCache.asMap().isEmpty());
    }

    @Test
    public void if_everything_is_cached_no_batching_happens() {
        AtomicInteger getCalls = new AtomicInteger();
        AtomicInteger setCalls = new AtomicInteger();
        CustomValueCache customValueCache = new CustomValueCache() {

            @Override
            public CompletableFuture<Object> get(String key) {
                getCalls.incrementAndGet();
                return super.get(key);
            }

            @Override
            public CompletableFuture<List<Object>> setValues(List<String> keys, List<Object> values) {
                setCalls.incrementAndGet();
                return super.setValues(keys, values);
            }
        };
        customValueCache.asMap().put("a", "cachedA");
        customValueCache.asMap().put("b", "cachedB");
        customValueCache.asMap().put("c", "cachedC");

        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setValueCache(customValueCache).setCachingEnabled(true);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        CompletableFuture<String> fA = identityLoader.load("a");
        CompletableFuture<String> fB = identityLoader.load("b");
        CompletableFuture<String> fC = identityLoader.load("c");

        await().until(identityLoader.dispatch()::isDone);

        assertThat(fA.join(), equalTo("cachedA"));
        assertThat(fB.join(), equalTo("cachedB"));
        assertThat(fC.join(), equalTo("cachedC"));

        assertThat(loadCalls, equalTo(emptyList()));
        assertThat(getCalls.get(), equalTo(3));
        assertThat(setCalls.get(), equalTo(0));
    }


    @Test
    public void if_batching_is_off_it_still_can_cache() {
        AtomicInteger getCalls = new AtomicInteger();
        AtomicInteger setCalls = new AtomicInteger();
        CustomValueCache customValueCache = new CustomValueCache() {

            @Override
            public CompletableFuture<Object> get(String key) {
                getCalls.incrementAndGet();
                return super.get(key);
            }

            @Override
            public CompletableFuture<List<Object>> setValues(List<String> keys, List<Object> values) {
                setCalls.incrementAndGet();
                return super.setValues(keys, values);
            }
        };
        customValueCache.asMap().put("a", "cachedA");

        List<List<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setValueCache(customValueCache).setCachingEnabled(true).setBatchingEnabled(false);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        CompletableFuture<String> fA = identityLoader.load("a");
        CompletableFuture<String> fB = identityLoader.load("b");
        CompletableFuture<String> fC = identityLoader.load("c");

        assertTrue(fA.isDone()); // with batching off they are dispatched immediately
        assertTrue(fB.isDone());
        assertTrue(fC.isDone());

        await().until(identityLoader.dispatch()::isDone);

        assertThat(fA.join(), equalTo("cachedA"));
        assertThat(fB.join(), equalTo("b"));
        assertThat(fC.join(), equalTo("c"));

        assertThat(loadCalls, equalTo(asList(singletonList("b"), singletonList("c"))));
        assertThat(getCalls.get(), equalTo(3));
        assertThat(setCalls.get(), equalTo(2));

        assertThat(sort(customValueCache.asMap().values()), equalTo(sort(asList("b", "c", "cachedA"))));
    }
}
