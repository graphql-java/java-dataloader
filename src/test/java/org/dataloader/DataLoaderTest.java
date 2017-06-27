/*
 * Copyright (c) 2016 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.dataloader;

import org.dataloader.impl.FutureKit;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThat;

/**
 * Tests for {@link DataLoader}.
 * <p>
 * The tests are a port of the existing tests in
 * the <a href="https://github.com/facebook/dataloader">facebook/dataloader</a> project.
 * <p>
 * Acknowledgments go to <a href="https://github.com/leebyron">Lee Byron</a> for providing excellent coverage.
 *
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 */
public class DataLoaderTest {

    DataLoader<Integer, Integer> identityLoader;

    private static CacheKey<JsonObject> getJsonObjectCacheMapFn() {
        return key -> key.stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .sorted()
                .collect(Collectors.joining());
    }

    @SuppressWarnings("unchecked")
    private static <K, V> DataLoader<K, V> idLoader(DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return new DataLoader<>(keys -> {
            loadCalls.add(new ArrayList(keys));
            List<CompletableFuture<V>> futures = keys.stream()
                    .map(k -> (V) k)
                    .map(CompletableFuture::completedFuture)
                    .collect(Collectors.toList());
            return PromisedValues.allOf(futures);
        }, options);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> DataLoader<K, V> idLoaderAllErrors(
            DataLoaderOptions options, List<Collection<V>> loadCalls) {
        return new DataLoader<>(keys -> {
            loadCalls.add(new ArrayList(keys));
            List<CompletableFuture<V>> futures = keys.stream()
                    .map(k -> (V) k)
                    .map(failWithError())
                    .collect(Collectors.toList());
            return PromisedValues.allOf(futures);
        }, options);
    }

    private static DataLoader<Integer, Integer> idLoaderWithErrors(
            DataLoaderOptions options, List<Collection<Integer>> loadCalls) {
        return new DataLoader<>(keys -> {
            loadCalls.add(new ArrayList<>(keys));
            List<CompletableFuture<Integer>> futures = keys.stream()
                    .map(fail50PercentOfTheTime())
                    .collect(Collectors.toList());
            return PromisedValues.allOf(futures);
        }, options);
    }

    private static Function<Integer, CompletableFuture<Integer>> fail50PercentOfTheTime() {
        return value -> {
            if (value % 2 == 0) {
                return CompletableFuture.completedFuture(value);
            } else {
                return futureError();
            }
        };
    }

    private static <V> Function<V, CompletableFuture<V>> failWithError() {
        return value -> futureError();
    }

    private static <V> CompletableFuture<V> futureError() {
        return FutureKit.failedFuture(new IllegalStateException("Error"));
    }

    @Before
    public void setUp() {
        identityLoader = idLoader(new DataLoaderOptions(), new ArrayList<>());
    }

    @Test
    public void should_Build_a_really_really_simple_data_loader() {
        AtomicBoolean success = new AtomicBoolean();
        DataLoader<Integer, Integer> identityLoader = new DataLoader<>(keys -> {
            List<CompletableFuture<Integer>> collect = keys.stream()
                    .map(CompletableFuture::completedFuture)
                    .collect(Collectors.toList());
            return PromisedValues.allOf(collect);
        });

        CompletableFuture<Integer> future1 = identityLoader.load(1);

        future1.thenAccept(rh -> {
            assertThat(rh, equalTo(1));
            success.set(true);
        });
        identityLoader.dispatch();
        await().untilAtomic(success, is(true));
    }

    @Test
    public void should_Support_loading_multiple_keys_in_one_call() {
        AtomicBoolean success = new AtomicBoolean();
        DataLoader<Integer, Integer> identityLoader = new DataLoader<>(keys ->
                PromisedValues.allOf(keys.stream()
                        .map(CompletableFuture::completedFuture)
                        .collect(Collectors.toCollection(ArrayList::new))));

        PromisedValues<Integer> futureAll = identityLoader.loadMany(asList(1, 2));
        futureAll.thenAccept(rh -> {
            assertThat(rh.size(), is(2));
            success.set(rh.succeeded());
        });
        identityLoader.dispatch();
        await().untilAtomic(success, is(true));
        assertThat(futureAll.toList(), equalTo(asList(1, 2)));
    }

    @Test
    public void should_Resolve_to_empty_list_when_no_keys_supplied() {
        AtomicBoolean success = new AtomicBoolean();
        PromisedValues<Integer> futureEmpty = identityLoader.loadMany(emptyList());
        futureEmpty.thenAccept(rh -> {
            assertThat(rh.size(), is(0));
            success.set(rh.succeeded());
        });
        identityLoader.dispatch();
        await().untilAtomic(success, is(true));
        assertThat(futureEmpty.toList(), empty());
    }

    @Test
    public void should_Batch_multiple_requests() throws ExecutionException, InterruptedException {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Integer> future1 = identityLoader.load(1);
        CompletableFuture<Integer> future2 = identityLoader.load(2);
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo(1));
        assertThat(future2.get(), equalTo(2));
        assertThat(loadCalls, equalTo(singletonList(asList(1, 2))));
    }

    @Test
    public void should_Coalesce_identical_requests() throws ExecutionException, InterruptedException {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Integer> future1a = identityLoader.load(1);
        CompletableFuture<Integer> future1b = identityLoader.load(1);
        assertThat(future1a, equalTo(future1b));
        identityLoader.dispatch();

        await().until(future1a::isDone);
        assertThat(future1a.get(), equalTo(1));
        assertThat(future1b.get(), equalTo(1));
        assertThat(loadCalls, equalTo(singletonList(singletonList(1))));
    }

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
    public void should_Not_redispatch_previous_load() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<String> future1 = identityLoader.load("A");
        identityLoader.dispatch();

        CompletableFuture<String> future2 = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(asList(singletonList("A"), singletonList("B"))));
    }

    @Test
    public void should_Cache_on_redispatch() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<String> future1 = identityLoader.load("A");
        identityLoader.dispatch();

        PromisedValues future2 = identityLoader.loadMany(asList("A", "B"));
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.toList(), equalTo(asList("A", "B")));
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
        PromisedValues composite = identityLoader.dispatch();

        await().until((Callable<Boolean>) composite::succeeded);
        assertThat(future1.get(), equalTo("X"));
        assertThat(future2.get(), equalTo("B"));

        identityLoader.prime("A", "Y");
        identityLoader.prime("B", "Y");

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
        PromisedValues composite2 = identityLoader.dispatch();

        await().until((Callable<Boolean>) composite2::succeeded);
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
        PromisedValues composite = identityLoader.dispatch();

        await().until((Callable<Boolean>) composite::succeeded);
        assertThat(future1.get(), equalTo("X"));
        assertThat(future2.get(), equalTo("B"));

        identityLoader.clear("A").prime("A", "Y");
        identityLoader.clear("B").prime("B", "Y");

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
        PromisedValues composite2 = identityLoader.dispatch();

        await().until((Callable<Boolean>) composite2::succeeded);
        assertThat(future1a.get(), equalTo("Y"));
        assertThat(future2a.get(), equalTo("Y"));
        assertThat(loadCalls, equalTo(singletonList(singletonList("B"))));
    }

    @Test
    public void should_Resolve_to_error_to_indicate_failure() throws ExecutionException, InterruptedException {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> evenLoader = idLoaderWithErrors(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Integer> future1 = evenLoader.load(1);
        evenLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(FutureKit.cause(future1), instanceOf(IllegalStateException.class));

        CompletableFuture<Integer> future2 = evenLoader.load(2);
        evenLoader.dispatch();

        await().until(future2::isDone);
        assertThat(future2.get(), equalTo(2));
        assertThat(loadCalls, equalTo(asList(singletonList(1), singletonList(2))));
    }

    // Accept any kind of key.

    @Test
    public void should_Represent_failures_and_successes_simultaneously() throws ExecutionException, InterruptedException {
        AtomicBoolean success = new AtomicBoolean();
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> evenLoader = idLoaderWithErrors(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Integer> future1 = evenLoader.load(1);
        CompletableFuture<Integer> future2 = evenLoader.load(2);
        CompletableFuture<Integer> future3 = evenLoader.load(3);
        CompletableFuture<Integer> future4 = evenLoader.load(4);
        PromisedValues<Integer> result = evenLoader.dispatch();
        result.thenAccept(rh -> success.set(true));

        await().untilAtomic(success, is(true));
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(FutureKit.cause(future1), instanceOf(IllegalStateException.class));
        assertThat(future2.get(), equalTo(2));
        assertThat(future3.isCompletedExceptionally(), is(true));
        assertThat(future4.get(), equalTo(4));

        assertThat(loadCalls, equalTo(singletonList(asList(1, 2, 3, 4))));
    }

    // Accepts options

    @Test
    public void should_Cache_failed_fetches() {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> errorLoader = idLoaderAllErrors(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Integer> future1 = errorLoader.load(1);
        errorLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(FutureKit.cause(future1), instanceOf(IllegalStateException.class));

        CompletableFuture<Integer> future2 = errorLoader.load(1);
        errorLoader.dispatch();

        await().until(future2::isDone);
        assertThat(future2.isCompletedExceptionally(), is(true));
        assertThat(FutureKit.cause(future2), instanceOf(IllegalStateException.class));
        assertThat(loadCalls, equalTo(singletonList(singletonList(1))));
    }

    // Accepts object key in custom cacheKey function

    @Test
    public void should_Handle_priming_the_cache_with_an_error() {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        identityLoader.prime(1, new IllegalStateException("Error"));

        CompletableFuture<Integer> future1 = identityLoader.load(1);
        identityLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(FutureKit.cause(future1), instanceOf(IllegalStateException.class));
        assertThat(loadCalls, equalTo(emptyList()));
    }

    @Test
    public void should_Clear_values_from_cache_after_errors() {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> errorLoader = idLoaderAllErrors(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Integer> future1 = errorLoader.load(1);
        future1.handle((rh, t) -> {
            if (t != null) {
                // Presumably determine if this error is transient, and only clear the cache in that case.
                errorLoader.clear(1);
            }
            return null;
        });
        errorLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(FutureKit.cause(future1), instanceOf(IllegalStateException.class));

        CompletableFuture<Integer> future2 = errorLoader.load(1);
        future2.handle((rh, t) -> {
            if (t != null) {
                // Again, only do this if you can determine the error is transient.
                errorLoader.clear(1);
            }
            return null;
        });
        errorLoader.dispatch();

        await().until(future2::isDone);
        assertThat(future2.isCompletedExceptionally(), is(true));
        assertThat(FutureKit.cause(future2), instanceOf(IllegalStateException.class));
        assertThat(loadCalls, equalTo(asList(Collections.singletonList(1), Collections.singletonList(1))));
    }

    @Test
    public void should_Propagate_error_to_all_loads() {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> errorLoader = idLoaderAllErrors(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Integer> future1 = errorLoader.load(1);
        CompletableFuture<Integer> future2 = errorLoader.load(2);
        errorLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        Throwable cause = FutureKit.cause(future1);
        assert cause != null;
        assertThat(cause, instanceOf(IllegalStateException.class));
        assertThat(cause.getMessage(), equalTo("Error"));

        await().until(future2::isDone);
        cause = FutureKit.cause(future2);
        assert cause != null;
        assertThat(cause.getMessage(), equalTo(cause.getMessage()));
        assertThat(loadCalls, equalTo(singletonList(asList(1, 2))));
    }

    @Test
    public void should_Accept_objects_as_keys() {
        List<Collection<Object>> loadCalls = new ArrayList<>();
        DataLoader<Object, Object> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        Object keyA = new Object();
        Object keyB = new Object();

        // Fetches as expected

        identityLoader.load(keyA);
        identityLoader.load(keyB);

        identityLoader.dispatch().thenAccept(rh -> {
            assertThat(rh.succeeded(), is(true));
            assertThat(rh.get(0), equalTo(keyA));
            assertThat(rh.get(1), equalTo(keyB));
        });

        assertThat(loadCalls.size(), equalTo(1));
        assertThat(loadCalls.get(0).size(), equalTo(2));
        assertThat(loadCalls.get(0).toArray()[0], equalTo(keyA));
        assertThat(loadCalls.get(0).toArray()[1], equalTo(keyB));

        // Caching
        identityLoader.clear(keyA);
        //noinspection SuspiciousMethodCalls
        loadCalls.remove(keyA);

        identityLoader.load(keyA);
        identityLoader.load(keyB);

        identityLoader.dispatch().thenAccept(rh -> {
            assertThat(rh.succeeded(), is(true));
            assertThat(rh.get(0), equalTo(keyA));
            assertThat(identityLoader.getCacheKey(keyB), equalTo(keyB));
        });

        assertThat(loadCalls.size(), equalTo(2));
        assertThat(loadCalls.get(1).size(), equalTo(1));
        assertThat(loadCalls.get(1).toArray()[0], equalTo(keyA));
    }

    @Test
    public void should_Disable_caching() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader =
                idLoader(DataLoaderOptions.create().setCachingEnabled(false), loadCalls);

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
        assertThat(loadCalls, equalTo(asList(asList("A", "B"), asList("A", "C"))));

        CompletableFuture<String> future1b = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
        CompletableFuture<String> future3a = identityLoader.load("C");
        identityLoader.dispatch();

        await().until(() -> future1b.isDone() && future2a.isDone() && future3a.isDone());
        assertThat(future1b.get(), equalTo("A"));
        assertThat(future2a.get(), equalTo("B"));
        assertThat(future3a.get(), equalTo("C"));
        assertThat(loadCalls, equalTo(asList(asList("A", "B"),
                asList("A", "C"), asList("A", "B", "C"))));
    }

    // It is resilient to job queue ordering

    @Test
    public void should_Accept_objects_with_a_complex_key() throws ExecutionException, InterruptedException {
        List<Collection<JsonObject>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = DataLoaderOptions.create().setCacheKeyFunction(getJsonObjectCacheMapFn());
        DataLoader<JsonObject, Integer> identityLoader = idLoader(options, loadCalls);

        JsonObject key1 = new JsonObject().put("id", 123);
        JsonObject key2 = new JsonObject().put("id", 123);

        CompletableFuture<Integer> future1 = identityLoader.load(key1);
        CompletableFuture<Integer> future2 = identityLoader.load(key2);
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(loadCalls, equalTo(singletonList(singletonList(key1))));
        assertThat(future1.get(), equalTo(key1));
        assertThat(future2.get(), equalTo(key1));
    }

    // Helper methods

    @Test
    public void should_Clear_objects_with_complex_key() throws ExecutionException, InterruptedException {
        List<Collection<JsonObject>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = DataLoaderOptions.create().setCacheKeyFunction(getJsonObjectCacheMapFn());
        DataLoader<JsonObject, Integer> identityLoader = idLoader(options, loadCalls);

        JsonObject key1 = new JsonObject().put("id", 123);
        JsonObject key2 = new JsonObject().put("id", 123);

        CompletableFuture<Integer> future1 = identityLoader.load(key1);
        identityLoader.dispatch();

        await().until(future1::isDone);
        identityLoader.clear(key2); // clear equivalent object key

        CompletableFuture<Integer> future2 = identityLoader.load(key1);
        identityLoader.dispatch();

        await().until(future2::isDone);
        assertThat(loadCalls, equalTo(asList(singletonList(key1), singletonList(key1))));
        assertThat(future1.get(), equalTo(key1));
        assertThat(future2.get(), equalTo(key1));
    }

    @Test
    public void should_Accept_objects_with_different_order_of_keys() throws ExecutionException, InterruptedException {
        List<Collection<JsonObject>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = DataLoaderOptions.create().setCacheKeyFunction(getJsonObjectCacheMapFn());
        DataLoader<JsonObject, Integer> identityLoader = idLoader(options, loadCalls);

        JsonObject key1 = new JsonObject().put("a", 123).put("b", 321);
        JsonObject key2 = new JsonObject().put("b", 321).put("a", 123);

        // Fetches as expected

        CompletableFuture<Integer> future1 = identityLoader.load(key1);
        CompletableFuture<Integer> future2 = identityLoader.load(key2);
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(loadCalls, equalTo(singletonList(singletonList(key1))));
        assertThat(loadCalls.size(), equalTo(1));
        assertThat(future1.get(), equalTo(key1));
        assertThat(future2.get(), equalTo(key1));
    }

    @Test
    public void should_Allow_priming_the_cache_with_an_object_key() throws ExecutionException, InterruptedException {
        List<Collection<JsonObject>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = DataLoaderOptions.create().setCacheKeyFunction(getJsonObjectCacheMapFn());
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
    public void should_Accept_a_custom_cache_map_implementation() throws ExecutionException, InterruptedException {
        CustomCacheMap customMap = new CustomCacheMap();
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = DataLoaderOptions.create().setCacheMap(customMap);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls);

        // Fetches as expected

        CompletableFuture future1 = identityLoader.load("a");
        CompletableFuture future2 = identityLoader.load("b");
        PromisedValues composite = identityLoader.dispatch();

        await().until(composite::isDone);
        assertThat(future1.get(), equalTo("a"));
        assertThat(future2.get(), equalTo("b"));

        assertThat(loadCalls, equalTo(singletonList(asList("a", "b"))));
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "b").toArray());

        CompletableFuture future3 = identityLoader.load("c");
        CompletableFuture future2a = identityLoader.load("b");
        composite = identityLoader.dispatch();

        await().until(composite::isDone);
        assertThat(future3.get(), equalTo("c"));
        assertThat(future2a.get(), equalTo("b"));

        assertThat(loadCalls, equalTo(asList(asList("a", "b"), singletonList("c"))));
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "b", "c").toArray());

        // Supports clear

        identityLoader.clear("b");
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "c").toArray());

        CompletableFuture future2b = identityLoader.load("b");
        composite = identityLoader.dispatch();

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
    public void should_Batch_loads_occurring_within_futures() {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(DataLoaderOptions.create(), loadCalls);

        Supplier<Object> nullValue = () -> null;

        CompletableFuture.supplyAsync(nullValue).thenAccept(rh -> {
            identityLoader.load("a");
            CompletableFuture.supplyAsync(nullValue).thenAccept(rh2 -> {
                identityLoader.load("b");
                CompletableFuture.supplyAsync(nullValue).thenAccept(rh3 -> {
                    identityLoader.load("c");
                    CompletableFuture.supplyAsync(nullValue).thenAccept(
                            rh4 ->
                                    identityLoader.load("d"));
                });
            });
        });

        PromisedValues composite = identityLoader.dispatch();

        await().until(composite::isDone);
        assertThat(loadCalls, equalTo(
                singletonList(asList("a", "b", "c", "d"))));
    }

    public class CustomCacheMap implements CacheMap<String, Object> {

        public Map<String, Object> stash;

        public CustomCacheMap() {
            stash = new LinkedHashMap<>();
        }

        @Override
        public boolean containsKey(String key) {
            return stash.containsKey(key);
        }

        @Override
        public Object get(String key) {
            return stash.get(key);
        }

        @Override
        public CacheMap<String, Object> set(String key, Object value) {
            stash.put(key, value);
            return this;
        }

        @Override
        public CacheMap<String, Object> delete(String key) {
            stash.remove(key);
            return this;
        }

        @Override
        public CacheMap<String, Object> clear() {
            stash.clear();
            return this;
        }
    }
}

