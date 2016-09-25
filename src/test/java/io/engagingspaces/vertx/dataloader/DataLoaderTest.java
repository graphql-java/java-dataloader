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

package io.engagingspaces.vertx.dataloader;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
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
@RunWith(VertxUnitRunner.class)
public class DataLoaderTest {

    @Rule
    public RunTestOnContext rule = new RunTestOnContext();

    DataLoader<Integer, Integer> identityLoader;

    @Before
    public void setUp() {
        identityLoader = idLoader(new DataLoaderOptions(), new ArrayList<>());
    }

    @Test
    public void should_Build_a_really_really_simple_data_loader() {
        AtomicBoolean success = new AtomicBoolean();
        DataLoader<Integer, Integer> identityLoader = new DataLoader<>(keys ->
            CompositeFuture.all(keys.stream()
                    .map(Future::succeededFuture)
                    .collect(Collectors.toCollection(ArrayList::new))));

        Future<Integer> future1 = identityLoader.load(1);
        future1.setHandler(rh -> {
            assertThat(rh.result(), equalTo(1));
            success.set(rh.succeeded());
        });
        identityLoader.dispatch();
        await().untilAtomic(success, is(true));
    }

    @Test
    public void should_Support_loading_multiple_keys_in_one_call() {
        AtomicBoolean success = new AtomicBoolean();
        DataLoader<Integer, Integer> identityLoader = new DataLoader<>(keys ->
                CompositeFuture.all(keys.stream()
                        .map(Future::succeededFuture)
                        .collect(Collectors.toCollection(ArrayList::new))));

        CompositeFuture futureAll = identityLoader.loadMany(Arrays.asList(1, 2));
        futureAll.setHandler(rh -> {
            assertThat(rh.result().size(), is(2));
            success.set(rh.succeeded());
        });
        identityLoader.dispatch();
        await().untilAtomic(success, is(true));
        assertThat(futureAll.list(), equalTo(Arrays.asList(1, 2)));
    }

    @Test
    public void should_Resolve_to_empty_list_when_no_keys_supplied() {
        AtomicBoolean success = new AtomicBoolean();
        CompositeFuture futureEmpty = identityLoader.loadMany(Collections.emptyList());
        futureEmpty.setHandler(rh -> {
            assertThat(rh.result().size(), is(0));
            success.set(rh.succeeded());
        });
        identityLoader.dispatch();
        await().untilAtomic(success, is(true));
        assertThat(futureEmpty.list(), empty());
    }

    @Test
    public void should_Batch_multiple_requests() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        Future<Integer> future1 = identityLoader.load(1);
        Future<Integer> future2 = identityLoader.load(2);
        identityLoader.dispatch();

        await().until(() -> future1.isComplete() && future2.isComplete());
        assertThat(future1.result(), equalTo(1));
        assertThat(future2.result(), equalTo(2));
        assertThat(loadCalls, equalTo(Collections.singletonList(Arrays.asList(1, 2))));
    }

    @Test
    public void should_Coalesce_identical_requests() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        Future<Integer> future1a = identityLoader.load(1);
        Future<Integer> future1b = identityLoader.load(1);
        assertThat(future1a, equalTo(future1b));
        identityLoader.dispatch();

        await().until(future1a::isComplete);
        assertThat(future1a.result(), equalTo(1));
        assertThat(future1b.result(), equalTo(1));
        assertThat(loadCalls, equalTo(Collections.singletonList(Collections.singletonList(1))));
    }

    @Test
    public void should_Cache_repeated_requests() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        Future<String> future1 = identityLoader.load("A");
        Future<String> future2 = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1.isComplete() && future2.isComplete());
        assertThat(future1.result(), equalTo("A"));
        assertThat(future2.result(), equalTo("B"));
        assertThat(loadCalls, equalTo(Collections.singletonList(Arrays.asList("A", "B"))));

        Future<String> future1a = identityLoader.load("A");
        Future<String> future3 = identityLoader.load("C");
        identityLoader.dispatch();

        await().until(() -> future1a.isComplete() && future3.isComplete());
        assertThat(future1a.result(), equalTo("A"));
        assertThat(future3.result(), equalTo("C"));
        assertThat(loadCalls, equalTo(Arrays.asList(Arrays.asList("A", "B"), Collections.singletonList("C"))));

        Future<String> future1b = identityLoader.load("A");
        Future<String> future2a = identityLoader.load("B");
        Future<String> future3a = identityLoader.load("C");
        identityLoader.dispatch();

        await().until(() -> future1b.isComplete() && future2a.isComplete() && future3a.isComplete());
        assertThat(future1b.result(), equalTo("A"));
        assertThat(future2a.result(), equalTo("B"));
        assertThat(future3a.result(), equalTo("C"));
        assertThat(loadCalls, equalTo(Arrays.asList(Arrays.asList("A", "B"), Collections.singletonList("C"))));
    }

    @Test
    public void should_Clear_single_value_in_loader() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        Future<String> future1 = identityLoader.load("A");
        Future<String> future2 = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1.isComplete() && future2.isComplete());
        assertThat(future1.result(), equalTo("A"));
        assertThat(future2.result(), equalTo("B"));
        assertThat(loadCalls, equalTo(Collections.singletonList(Arrays.asList("A", "B"))));

        identityLoader.clear("A");

        Future<String> future1a = identityLoader.load("A");
        Future<String> future2a = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1a.isComplete() && future2a.isComplete());
        assertThat(future1a.result(), equalTo("A"));
        assertThat(future2a.result(), equalTo("B"));
        assertThat(loadCalls, equalTo(Arrays.asList(Arrays.asList("A", "B"), Collections.singletonList("A"))));
    }

    @Test
    public void should_Clear_all_values_in_loader() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        Future<String> future1 = identityLoader.load("A");
        Future<String> future2 = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1.isComplete() && future2.isComplete());
        assertThat(future1.result(), equalTo("A"));
        assertThat(future2.result(), equalTo("B"));
        assertThat(loadCalls, equalTo(Collections.singletonList(Arrays.asList("A", "B"))));

        identityLoader.clearAll();

        Future<String> future1a = identityLoader.load("A");
        Future<String> future2a = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1a.isComplete() && future2a.isComplete());
        assertThat(future1a.result(), equalTo("A"));
        assertThat(future2a.result(), equalTo("B"));
        assertThat(loadCalls, equalTo(Arrays.asList(Arrays.asList("A", "B"), Arrays.asList("A", "B"))));
    }

    @Test
    public void should_Allow_priming_the_cache() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        identityLoader.prime("A", "A");

        Future<String> future1 = identityLoader.load("A");
        Future<String> future2 = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1.isComplete() && future2.isComplete());
        assertThat(future1.result(), equalTo("A"));
        assertThat(future2.result(), equalTo("B"));
        assertThat(loadCalls, equalTo(Collections.singletonList(Collections.singletonList("B"))));
    }

    @Test
    public void should_Not_prime_keys_that_already_exist() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        identityLoader.prime("A", "X");

        Future<String> future1 = identityLoader.load("A");
        Future<String> future2 = identityLoader.load("B");
        CompositeFuture composite = identityLoader.dispatch();

        await().until((Callable<Boolean>) composite::succeeded);
        assertThat(future1.result(), equalTo("X"));
        assertThat(future2.result(), equalTo("B"));

        identityLoader.prime("A", "Y");
        identityLoader.prime("B", "Y");

        Future<String> future1a = identityLoader.load("A");
        Future<String> future2a = identityLoader.load("B");
        CompositeFuture composite2 = identityLoader.dispatch();

        await().until((Callable<Boolean>) composite2::succeeded);
        assertThat(future1a.result(), equalTo("X"));
        assertThat(future2a.result(), equalTo("B"));
        assertThat(loadCalls, equalTo(Collections.singletonList(Collections.singletonList("B"))));
    }

    @Test
    public void should_Allow_to_forcefully_prime_the_cache() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        identityLoader.prime("A", "X");

        Future<String> future1 = identityLoader.load("A");
        Future<String> future2 = identityLoader.load("B");
        CompositeFuture composite = identityLoader.dispatch();

        await().until((Callable<Boolean>) composite::succeeded);
        assertThat(future1.result(), equalTo("X"));
        assertThat(future2.result(), equalTo("B"));

        identityLoader.clear("A").prime("A", "Y");
        identityLoader.clear("B").prime("B", "Y");

        Future<String> future1a = identityLoader.load("A");
        Future<String> future2a = identityLoader.load("B");
        CompositeFuture composite2 = identityLoader.dispatch();

        await().until((Callable<Boolean>) composite2::succeeded);
        assertThat(future1a.result(), equalTo("Y"));
        assertThat(future2a.result(), equalTo("Y"));
        assertThat(loadCalls, equalTo(Collections.singletonList(Collections.singletonList("B"))));
    }

    @Test
    public void should_Resolve_to_error_to_indicate_failure() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> evenLoader = idLoaderWithErrors(new DataLoaderOptions(), loadCalls);

        Future<Integer> future1 = evenLoader.load(1);
        evenLoader.dispatch();

        await().until(future1::isComplete);
        assertThat(future1.failed(), is(true));
        assertThat(future1.cause(), instanceOf(IllegalStateException.class));

        Future<Integer> future2 = evenLoader.load(2);
        evenLoader.dispatch();

        await().until(future2::isComplete);
        assertThat(future2.result(), equalTo(2));
        assertThat(loadCalls, equalTo(Arrays.asList(Collections.singletonList(1), Collections.singletonList(2))));
    }

    @Test
    public void should_Represent_failures_and_successes_simultaneously() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> evenLoader = idLoaderWithErrors(new DataLoaderOptions(), loadCalls);

        Future<Integer> future1 = evenLoader.load(1);
        Future<Integer> future2 = evenLoader.load(2);
        evenLoader.dispatch();

        await().until(future1::isComplete);
        assertThat(future1.failed(), is(true));
        assertThat(future1.cause(), instanceOf(IllegalStateException.class));

        await().until(future2::isComplete);
        assertThat(future2.result(), equalTo(2));
        assertThat(loadCalls, equalTo(Collections.singletonList(Arrays.asList(1, 2))));
    }

    @Test
    public void should_Cache_failed_fetches() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> errorLoader = idLoaderAllErrors(new DataLoaderOptions(), loadCalls);

        Future<Integer> future1 = errorLoader.load(1);
        errorLoader.dispatch();

        await().until(future1::isComplete);
        assertThat(future1.failed(), is(true));
        assertThat(future1.cause(), instanceOf(IllegalStateException.class));

        Future<Integer> future2 = errorLoader.load(1);
        errorLoader.dispatch();

        await().until(future2::isComplete);
        assertThat(future2.failed(), is(true));
        assertThat(future2.cause(), instanceOf(IllegalStateException.class));
        assertThat(loadCalls, equalTo(Collections.singletonList(Collections.singletonList(1))));
    }

    @Test
    public void should_Handle_priming_the_cache_with_an_error() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        identityLoader.prime(1, new IllegalStateException("Error"));

        Future<Integer> future1 = identityLoader.load(1);
        identityLoader.dispatch();

        await().until(future1::isComplete);
        assertThat(future1.failed(), is(true));
        assertThat(future1.cause(), instanceOf(IllegalStateException.class));
        assertThat(loadCalls, equalTo(Collections.emptyList()));
    }

    @Test
    public void should_Clear_values_from_cache_after_errors() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> errorLoader = idLoaderAllErrors(new DataLoaderOptions(), loadCalls);

        Future<Integer> future1 = errorLoader.load(1);
        future1.setHandler(rh -> {
            if (rh.failed()) {
                // Presumably determine if this error is transient, and only clear the cache in that case.
                errorLoader.clear(1);
            }
        });
        errorLoader.dispatch();

        await().until(future1::isComplete);
        assertThat(future1.failed(), is(true));
        assertThat(future1.cause(), instanceOf(IllegalStateException.class));

        Future<Integer> future2 = errorLoader.load(1);
        future2.setHandler(rh -> {
            if (rh.failed()) {
                // Again, only do this if you can determine the error is transient.
                errorLoader.clear(1);
            }
        });
        errorLoader.dispatch();

        await().until(future2::isComplete);
        assertThat(future2.failed(), is(true));
        assertThat(future2.cause(), instanceOf(IllegalStateException.class));
        assertThat(loadCalls, equalTo(Arrays.asList(Collections.singletonList(1), Collections.singletonList(1))));
    }

    @Test
    public void should_Propagate_error_to_all_loads() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> errorLoader = idLoaderAllErrors(new DataLoaderOptions(), loadCalls);

        Future<Integer> future1 = errorLoader.load(1);
        Future<Integer> future2 = errorLoader.load(2);
        errorLoader.dispatch();

        await().until(future1::isComplete);
        assertThat(future1.failed(), is(true));
        Throwable cause = future1.cause();
        assertThat(cause, instanceOf(IllegalStateException.class));
        assertThat(cause.getMessage(), equalTo("Error"));

        await().until(future2::isComplete);
        cause = future2.cause();
        assertThat(cause.getMessage(), equalTo(cause.getMessage()));
        assertThat(loadCalls, equalTo(Collections.singletonList(Arrays.asList(1, 2))));
    }

    // Accept any kind of key.

    @Test
    public void should_Accept_objects_as_keys() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<Object, Object> identityLoader = idLoader(new DataLoaderOptions(), loadCalls);

        Object keyA = new Object();
        Object keyB = new Object();

        // Fetches as expected

        identityLoader.load(keyA);
        identityLoader.load(keyB);

        identityLoader.dispatch().setHandler(rh -> {
            assertThat(rh.succeeded(), is(true));
            assertThat(rh.result().result(0), equalTo(keyA));
            assertThat(rh.result().result(1), equalTo(keyB));
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

        identityLoader.dispatch().setHandler(rh -> {
            assertThat(rh.succeeded(), is(true));
            assertThat(rh.result().result(0), equalTo(keyA));
            assertThat(identityLoader.getCacheKey(keyB), equalTo(keyB));
        });

        assertThat(loadCalls.size(), equalTo(2));
        assertThat(loadCalls.get(1).size(), equalTo(1));
        assertThat(loadCalls.get(1).toArray()[0], equalTo(keyA));
    }

    // Accepts options

    @Test
    public void should_Disable_caching() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader =
                idLoader(DataLoaderOptions.create().setCachingEnabled(false), loadCalls);

        Future<String> future1 = identityLoader.load("A");
        Future<String> future2 = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1.isComplete() && future2.isComplete());
        assertThat(future1.result(), equalTo("A"));
        assertThat(future2.result(), equalTo("B"));
        assertThat(loadCalls, equalTo(Collections.singletonList(Arrays.asList("A", "B"))));

        Future<String> future1a = identityLoader.load("A");
        Future<String> future3 = identityLoader.load("C");
        identityLoader.dispatch();

        await().until(() -> future1a.isComplete() && future3.isComplete());
        assertThat(future1a.result(), equalTo("A"));
        assertThat(future3.result(), equalTo("C"));
        assertThat(loadCalls, equalTo(Arrays.asList(Arrays.asList("A", "B"), Arrays.asList("A", "C"))));

        Future<String> future1b = identityLoader.load("A");
        Future<String> future2a = identityLoader.load("B");
        Future<String> future3a = identityLoader.load("C");
        identityLoader.dispatch();

        await().until(() -> future1b.isComplete() && future2a.isComplete() && future3a.isComplete());
        assertThat(future1b.result(), equalTo("A"));
        assertThat(future2a.result(), equalTo("B"));
        assertThat(future3a.result(), equalTo("C"));
        assertThat(loadCalls, equalTo(Arrays.asList(Arrays.asList("A", "B"),
                Arrays.asList("A", "C"), Arrays.asList("A", "B", "C"))));
    }

    // Accepts object key in custom cacheKey function

    @Test
    public void should_Accept_objects_with_a_complex_key() {
        ArrayList<Collection> loadCalls = new ArrayList<>();
        DataLoaderOptions options = DataLoaderOptions.create().setCacheKeyFunction(getJsonObjectCacheMapFn());
        DataLoader<JsonObject, Integer> identityLoader = idLoader(options, loadCalls);

        JsonObject key1 = new JsonObject().put("id", 123);
        JsonObject key2 = new JsonObject().put("id", 123);

        Future<Integer> future1 = identityLoader.load(key1);
        Future<Integer> future2 = identityLoader.load(key2);
        identityLoader.dispatch();

        await().until(() -> future1.isComplete() && future2.isComplete());
        assertThat(loadCalls, equalTo(Collections.singletonList(Collections.singletonList(key1))));
        assertThat(future1.result(), equalTo(key1));
        assertThat(future2.result(), equalTo(key1));
    }

    private static CacheKey<JsonObject> getJsonObjectCacheMapFn() {
        return key -> key.stream()
                .sorted()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining());
    }

    @SuppressWarnings("unchecked")
    private static <K, V> DataLoader<K, V> idLoader(DataLoaderOptions options, List<Collection> loadCalls) {
        return new DataLoader<>(keys -> {
            loadCalls.add(new ArrayList(keys));
            List<Future> futures = keys.stream().map(Future::succeededFuture).collect(Collectors.toList());
            return CompositeFuture.all(futures);
        }, options);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> DataLoader<K, V> idLoaderAllErrors(
            DataLoaderOptions options, List<Collection> loadCalls) {
        return new DataLoader<>(keys -> {
            loadCalls.add(new ArrayList(keys));
            List<Future> futures = keys.stream()
                    .map(key -> Future.failedFuture(new IllegalStateException("Error")))
                    .collect(Collectors.toList());
            return CompositeFuture.all(futures);
        }, options);
    }

    @SuppressWarnings("unchecked")
    private static DataLoader<Integer, Integer> idLoaderWithErrors(
            DataLoaderOptions options, List<Collection> loadCalls) {
        return new DataLoader<>(keys -> {
            loadCalls.add(new ArrayList(keys));
            List<Future> futures = keys.stream()
                    .map(key -> key % 2 == 0 ? Future.succeededFuture(key) :
                            Future.failedFuture(new IllegalStateException("Error")))
                    .collect(Collectors.toList());
            return CompositeFuture.all(futures);
        }, options);
    }
}
