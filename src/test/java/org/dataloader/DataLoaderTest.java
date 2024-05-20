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

import org.dataloader.fixtures.CustomCacheMap;
import org.dataloader.fixtures.JsonObject;
import org.dataloader.fixtures.TestKit;
import org.dataloader.fixtures.User;
import org.dataloader.fixtures.UserManager;
import org.dataloader.impl.CompletableFutureKit;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.awaitility.Awaitility.await;
import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.dataloader.DataLoaderFactory.newMappedDataLoader;
import static org.dataloader.DataLoaderFactory.newMappedPublisherDataLoader;
import static org.dataloader.DataLoaderFactory.newMappedPublisherDataLoaderWithTry;
import static org.dataloader.DataLoaderFactory.newPublisherDataLoader;
import static org.dataloader.DataLoaderFactory.newPublisherDataLoaderWithTry;
import static org.dataloader.DataLoaderOptions.newOptions;
import static org.dataloader.fixtures.TestKit.futureError;
import static org.dataloader.fixtures.TestKit.listFrom;
import static org.dataloader.impl.CompletableFutureKit.cause;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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

    @Test
    public void should_Build_a_really_really_simple_data_loader() {
        AtomicBoolean success = new AtomicBoolean();
        DataLoader<Integer, Integer> identityLoader = newDataLoader(CompletableFuture::completedFuture);

        CompletionStage<Integer> future1 = identityLoader.load(1);

        future1.thenAccept(value -> {
            assertThat(value, equalTo(1));
            success.set(true);
        });
        identityLoader.dispatch();
        await().untilAtomic(success, is(true));
    }

    @Test
    public void basic_map_batch_loading() {
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
        DataLoader<String, String> loader = DataLoaderFactory.newMappedDataLoader(evensOnlyMappedBatchLoader);

        loader.load("A");
        loader.load("B");
        loader.loadMany(asList("C", "D"));

        List<String> results = loader.dispatchAndJoin();

        assertThat(results.size(), equalTo(4));
        assertThat(results, equalTo(asList("A", null, "C", null)));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Support_loading_multiple_keys_in_one_call(TestDataLoaderFactory factory) {
        AtomicBoolean success = new AtomicBoolean();
        DataLoader<Integer, Integer> identityLoader = factory.idLoader(new DataLoaderOptions(), new ArrayList<>());

        CompletionStage<List<Integer>> futureAll = identityLoader.loadMany(asList(1, 2));
        futureAll.thenAccept(promisedValues -> {
            assertThat(promisedValues.size(), is(2));
            success.set(true);
        });
        identityLoader.dispatch();
        await().untilAtomic(success, is(true));
        assertThat(futureAll.toCompletableFuture().join(), equalTo(asList(1, 2)));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Resolve_to_empty_list_when_no_keys_supplied(TestDataLoaderFactory factory) {
        AtomicBoolean success = new AtomicBoolean();
        DataLoader<Integer, Integer> identityLoader = factory.idLoader(new DataLoaderOptions(), new ArrayList<>());
        CompletableFuture<List<Integer>> futureEmpty = identityLoader.loadMany(emptyList());
        futureEmpty.thenAccept(promisedValues -> {
            assertThat(promisedValues.size(), is(0));
            success.set(true);
        });
        identityLoader.dispatch();
        await().untilAtomic(success, is(true));
        assertThat(futureEmpty.join(), empty());
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Return_zero_entries_dispatched_when_no_keys_supplied(TestDataLoaderFactory factory) {
        AtomicBoolean success = new AtomicBoolean();
        DataLoader<Integer, Integer> identityLoader = factory.idLoader(new DataLoaderOptions(), new ArrayList<>());
        CompletableFuture<List<Integer>> futureEmpty = identityLoader.loadMany(emptyList());
        futureEmpty.thenAccept(promisedValues -> {
            assertThat(promisedValues.size(), is(0));
            success.set(true);
        });
        DispatchResult<Integer> dispatchResult = identityLoader.dispatchWithCounts();
        await().untilAtomic(success, is(true));
        assertThat(dispatchResult.getKeysCount(), equalTo(0));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Batch_multiple_requests(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = factory.idLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Integer> future1 = identityLoader.load(1);
        CompletableFuture<Integer> future2 = identityLoader.load(2);
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo(1));
        assertThat(future2.get(), equalTo(2));
        assertThat(loadCalls, equalTo(singletonList(asList(1, 2))));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Return_number_of_batched_entries(TestDataLoaderFactory factory) {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = factory.idLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Integer> future1 = identityLoader.load(1);
        CompletableFuture<Integer> future2 = identityLoader.load(2);
        DispatchResult<?> dispatchResult = identityLoader.dispatchWithCounts();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(dispatchResult.getKeysCount(), equalTo(2)); // its two because it's the number dispatched (by key) not the load calls
        assertThat(dispatchResult.getPromisedResults().isDone(), equalTo(true));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Coalesce_identical_requests(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = factory.idLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Integer> future1a = identityLoader.load(1);
        CompletableFuture<Integer> future1b = identityLoader.load(1);
        assertThat(future1a, equalTo(future1b));
        identityLoader.dispatch();

        await().until(future1a::isDone);
        assertThat(future1a.get(), equalTo(1));
        assertThat(future1b.get(), equalTo(1));
        assertThat(loadCalls, equalTo(singletonList(singletonList(1))));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Cache_repeated_requests(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = factory.idLoader(new DataLoaderOptions(), loadCalls);

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

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Not_redispatch_previous_load(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = factory.idLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<String> future1 = identityLoader.load("A");
        identityLoader.dispatch();

        CompletableFuture<String> future2 = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(asList(singletonList("A"), singletonList("B"))));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Cache_on_redispatch(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = factory.idLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<String> future1 = identityLoader.load("A");
        identityLoader.dispatch();

        CompletableFuture<List<String>> future2 = identityLoader.loadMany(asList("A", "B"));
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo(asList("A", "B")));
        assertThat(loadCalls, equalTo(asList(singletonList("A"), singletonList("B"))));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Clear_single_value_in_loader(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = factory.idLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(asList("A", "B"))));

        // fluency
        DataLoader<String, String> dl = identityLoader.clear("A");
        assertThat(dl, equalTo(identityLoader));

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1a.isDone() && future2a.isDone());
        assertThat(future1a.get(), equalTo("A"));
        assertThat(future2a.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(asList(asList("A", "B"), singletonList("A"))));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Clear_all_values_in_loader(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = factory.idLoader(new DataLoaderOptions(), loadCalls);

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(asList("A", "B"))));

        DataLoader<String, String> dlFluent = identityLoader.clearAll();
        assertThat(dlFluent, equalTo(identityLoader)); // fluency

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1a.isDone() && future2a.isDone());
        assertThat(future1a.get(), equalTo("A"));
        assertThat(future2a.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(asList(asList("A", "B"), asList("A", "B"))));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Allow_priming_the_cache(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = factory.idLoader(new DataLoaderOptions(), loadCalls);

        DataLoader<String, String> dlFluency = identityLoader.prime("A", "A");
        assertThat(dlFluency, equalTo(identityLoader));

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(singletonList("B"))));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Not_prime_keys_that_already_exist(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = factory.idLoader(new DataLoaderOptions(), loadCalls);

        identityLoader.prime("A", "X");

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        CompletableFuture<List<String>> composite = identityLoader.dispatch();

        await().until(composite::isDone);
        assertThat(future1.get(), equalTo("X"));
        assertThat(future2.get(), equalTo("B"));

        identityLoader.prime("A", "Y");
        identityLoader.prime("B", "Y");

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
        CompletableFuture<List<String>> composite2 = identityLoader.dispatch();

        await().until(composite2::isDone);
        assertThat(future1a.get(), equalTo("X"));
        assertThat(future2a.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(singletonList("B"))));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Allow_to_forcefully_prime_the_cache(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = factory.idLoader(new DataLoaderOptions(), loadCalls);

        identityLoader.prime("A", "X");

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        CompletableFuture<List<String>> composite = identityLoader.dispatch();

        await().until(composite::isDone);
        assertThat(future1.get(), equalTo("X"));
        assertThat(future2.get(), equalTo("B"));

        identityLoader.clear("A").prime("A", "Y");
        identityLoader.clear("B").prime("B", "Y");

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
        CompletableFuture<List<String>> composite2 = identityLoader.dispatch();

        await().until(composite2::isDone);
        assertThat(future1a.get(), equalTo("Y"));
        assertThat(future2a.get(), equalTo("Y"));
        assertThat(loadCalls, equalTo(singletonList(singletonList("B"))));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Allow_priming_the_cache_with_a_future(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = factory.idLoader(new DataLoaderOptions(), loadCalls);

        DataLoader<String, String> dlFluency = identityLoader.prime("A", CompletableFuture.completedFuture("A"));
        assertThat(dlFluency, equalTo(identityLoader));

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(singletonList("B"))));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_not_Cache_failed_fetches_on_complete_failure(TestDataLoaderFactory factory) {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> errorLoader = factory.idLoaderBlowsUps(new DataLoaderOptions(), loadCalls);

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

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Resolve_to_error_to_indicate_failure(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Object> evenLoader = factory.idLoaderOddEvenExceptions(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Object> future1 = evenLoader.load(1);
        evenLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(cause(future1), instanceOf(IllegalStateException.class));

        CompletableFuture<Object> future2 = evenLoader.load(2);
        evenLoader.dispatch();

        await().until(future2::isDone);
        assertThat(future2.get(), equalTo(2));
        assertThat(loadCalls, equalTo(asList(singletonList(1), singletonList(2))));
    }

    // Accept any kind of key.

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Represent_failures_and_successes_simultaneously(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        AtomicBoolean success = new AtomicBoolean();
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Object> evenLoader = factory.idLoaderOddEvenExceptions(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Object> future1 = evenLoader.load(1);
        CompletableFuture<Object> future2 = evenLoader.load(2);
        CompletableFuture<Object> future3 = evenLoader.load(3);
        CompletableFuture<Object> future4 = evenLoader.load(4);
        CompletableFuture<List<Object>> result = evenLoader.dispatch();
        result.thenAccept(promisedValues -> success.set(true));

        await().untilAtomic(success, is(true));

        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(cause(future1), instanceOf(IllegalStateException.class));
        assertThat(future2.get(), equalTo(2));
        assertThat(future3.isCompletedExceptionally(), is(true));
        assertThat(future4.get(), equalTo(4));

        assertThat(loadCalls, equalTo(singletonList(asList(1, 2, 3, 4))));
    }

    // Accepts options

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Cache_failed_fetches(TestDataLoaderFactory factory) {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Object> errorLoader = factory.idLoaderAllExceptions(new DataLoaderOptions(), loadCalls);

        CompletableFuture<Object> future1 = errorLoader.load(1);
        errorLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(cause(future1), instanceOf(IllegalStateException.class));

        CompletableFuture<Object> future2 = errorLoader.load(1);
        errorLoader.dispatch();

        await().until(future2::isDone);
        assertThat(future2.isCompletedExceptionally(), is(true));
        assertThat(cause(future2), instanceOf(IllegalStateException.class));

        assertThat(loadCalls, equalTo(singletonList(singletonList(1))));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_NOT_Cache_failed_fetches_if_told_not_too(TestDataLoaderFactory factory) {
        DataLoaderOptions options = DataLoaderOptions.newOptions().setCachingExceptionsEnabled(false);
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Object> errorLoader = factory.idLoaderAllExceptions(options, loadCalls);

        CompletableFuture<Object> future1 = errorLoader.load(1);
        errorLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(cause(future1), instanceOf(IllegalStateException.class));

        CompletableFuture<Object> future2 = errorLoader.load(1);
        errorLoader.dispatch();

        await().until(future2::isDone);
        assertThat(future2.isCompletedExceptionally(), is(true));
        assertThat(cause(future2), instanceOf(IllegalStateException.class));

        assertThat(loadCalls, equalTo(asList(singletonList(1), singletonList(1))));
    }


    // Accepts object key in custom cacheKey function

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Handle_priming_the_cache_with_an_error(TestDataLoaderFactory factory) {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = factory.idLoader(new DataLoaderOptions(), loadCalls);

        identityLoader.prime(1, new IllegalStateException("Error"));

        CompletableFuture<Integer> future1 = identityLoader.load(1);
        identityLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(cause(future1), instanceOf(IllegalStateException.class));
        assertThat(loadCalls, equalTo(emptyList()));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Clear_values_from_cache_after_errors(TestDataLoaderFactory factory) {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> errorLoader = factory.idLoaderBlowsUps(new DataLoaderOptions(), loadCalls);

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

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Propagate_error_to_all_loads(TestDataLoaderFactory factory) {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> errorLoader = factory.idLoaderBlowsUps(new DataLoaderOptions(), loadCalls);

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

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Accept_objects_as_keys(TestDataLoaderFactory factory) {
        List<Collection<Object>> loadCalls = new ArrayList<>();
        DataLoader<Object, Object> identityLoader = factory.idLoader(new DataLoaderOptions(), loadCalls);

        Object keyA = new Object();
        Object keyB = new Object();

        // Fetches as expected

        identityLoader.load(keyA);
        identityLoader.load(keyB);

        identityLoader.dispatch().thenAccept(promisedValues -> {
            assertThat(promisedValues.get(0), equalTo(keyA));
            assertThat(promisedValues.get(1), equalTo(keyB));
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

        identityLoader.dispatch().thenAccept(promisedValues -> {
            assertThat(promisedValues.get(0), equalTo(keyA));
            assertThat(identityLoader.getCacheKey(keyB), equalTo(keyB));
        });

        assertThat(loadCalls.size(), equalTo(2));
        assertThat(loadCalls.get(1).size(), equalTo(1));
        assertThat(loadCalls.get(1).toArray()[0], equalTo(keyA));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Disable_caching(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader =
                factory.idLoader(newOptions().setCachingEnabled(false), loadCalls);

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

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_work_with_duplicate_keys_when_caching_disabled(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader =
                factory.idLoader(newOptions().setCachingEnabled(false), loadCalls);

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        CompletableFuture<String> future3 = identityLoader.load("A");
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone() && future3.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(future3.get(), equalTo("A"));
        if (factory instanceof ListDataLoaderFactory || factory instanceof PublisherDataLoaderFactory) {
            assertThat(loadCalls, equalTo(singletonList(asList("A", "B", "A"))));
        } else {
            assertThat(loadCalls, equalTo(singletonList(asList("A", "B"))));
        }
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_work_with_duplicate_keys_when_caching_enabled(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader =
                factory.idLoader(newOptions().setCachingEnabled(true), loadCalls);

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

    // It is resilient to job queue ordering

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Accept_objects_with_a_complex_key(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<JsonObject>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setCacheKeyFunction(getJsonObjectCacheMapFn());
        DataLoader<JsonObject, JsonObject> identityLoader = factory.idLoader(options, loadCalls);

        JsonObject key1 = new JsonObject().put("id", 123);
        JsonObject key2 = new JsonObject().put("id", 123);

        CompletableFuture<JsonObject> future1 = identityLoader.load(key1);
        CompletableFuture<JsonObject> future2 = identityLoader.load(key2);
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(loadCalls, equalTo(singletonList(singletonList(key1))));
        assertThat(future1.get(), equalTo(key1));
        assertThat(future2.get(), equalTo(key1));
    }

    // Helper methods

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Clear_objects_with_complex_key(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<JsonObject>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setCacheKeyFunction(getJsonObjectCacheMapFn());
        DataLoader<JsonObject, JsonObject> identityLoader = factory.idLoader(options, loadCalls);

        JsonObject key1 = new JsonObject().put("id", 123);
        JsonObject key2 = new JsonObject().put("id", 123);

        CompletableFuture<JsonObject> future1 = identityLoader.load(key1);
        identityLoader.dispatch();

        await().until(future1::isDone);
        identityLoader.clear(key2); // clear equivalent object key

        CompletableFuture<JsonObject> future2 = identityLoader.load(key1);
        identityLoader.dispatch();

        await().until(future2::isDone);
        assertThat(loadCalls, equalTo(asList(singletonList(key1), singletonList(key1))));
        assertThat(future1.get(), equalTo(key1));
        assertThat(future2.get(), equalTo(key1));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Accept_objects_with_different_order_of_keys(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<JsonObject>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setCacheKeyFunction(getJsonObjectCacheMapFn());
        DataLoader<JsonObject, JsonObject> identityLoader = factory.idLoader(options, loadCalls);

        JsonObject key1 = new JsonObject().put("a", 123).put("b", 321);
        JsonObject key2 = new JsonObject().put("b", 321).put("a", 123);

        // Fetches as expected

        CompletableFuture<JsonObject> future1 = identityLoader.load(key1);
        CompletableFuture<JsonObject> future2 = identityLoader.load(key2);
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(loadCalls, equalTo(singletonList(singletonList(key1))));
        assertThat(loadCalls.size(), equalTo(1));
        assertThat(future1.get(), equalTo(key1));
        assertThat(future2.get(), equalTo(key2));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Allow_priming_the_cache_with_an_object_key(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        List<Collection<JsonObject>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setCacheKeyFunction(getJsonObjectCacheMapFn());
        DataLoader<JsonObject, JsonObject> identityLoader = factory.idLoader(options, loadCalls);

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

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Accept_a_custom_cache_map_implementation(TestDataLoaderFactory factory) throws ExecutionException, InterruptedException {
        CustomCacheMap customMap = new CustomCacheMap();
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setCacheMap(customMap);
        DataLoader<String, String> identityLoader = factory.idLoader(options, loadCalls);

        // Fetches as expected

        CompletableFuture<String> future1 = identityLoader.load("a");
        CompletableFuture<String> future2 = identityLoader.load("b");
        CompletableFuture<List<String>> composite = identityLoader.dispatch();

        await().until(composite::isDone);
        assertThat(future1.get(), equalTo("a"));
        assertThat(future2.get(), equalTo("b"));

        assertThat(loadCalls, equalTo(singletonList(asList("a", "b"))));
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "b").toArray());

        CompletableFuture<String> future3 = identityLoader.load("c");
        CompletableFuture<String> future2a = identityLoader.load("b");
        composite = identityLoader.dispatch();

        await().until(composite::isDone);
        assertThat(future3.get(), equalTo("c"));
        assertThat(future2a.get(), equalTo("b"));

        assertThat(loadCalls, equalTo(asList(asList("a", "b"), singletonList("c"))));
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "b", "c").toArray());

        // Supports clear

        identityLoader.clear("b");
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "c").toArray());

        CompletableFuture<String> future2b = identityLoader.load("b");
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

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_degrade_gracefully_if_cache_get_throws(TestDataLoaderFactory factory) {
        CacheMap<String, Object> cache = new ThrowingCacheMap();
        DataLoaderOptions options = newOptions().setCachingEnabled(true).setCacheMap(cache);
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = factory.idLoader(options, loadCalls);

        assertThat(identityLoader.getIfPresent("a"), equalTo(Optional.empty()));

        CompletableFuture<String> future = identityLoader.load("a");
        identityLoader.dispatch();
        assertThat(future.join(), equalTo("a"));
    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void batching_disabled_should_dispatch_immediately(TestDataLoaderFactory factory) {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setBatchingEnabled(false);
        DataLoader<String, String> identityLoader = factory.idLoader(options, loadCalls);

        CompletableFuture<String> fa = identityLoader.load("A");
        CompletableFuture<String> fb = identityLoader.load("B");

        // caching is on still
        CompletableFuture<String> fa1 = identityLoader.load("A");
        CompletableFuture<String> fb1 = identityLoader.load("B");

        List<String> values = CompletableFutureKit.allOf(asList(fa, fb, fa1, fb1)).join();

        assertThat(fa.join(), equalTo("A"));
        assertThat(fb.join(), equalTo("B"));
        assertThat(fa1.join(), equalTo("A"));
        assertThat(fb1.join(), equalTo("B"));

        assertThat(values, equalTo(asList("A", "B", "A", "B")));

        assertThat(loadCalls, equalTo(asList(
                singletonList("A"),
                singletonList("B"))));

    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void batching_disabled_and_caching_disabled_should_dispatch_immediately_and_forget(TestDataLoaderFactory factory) {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoaderOptions options = newOptions().setBatchingEnabled(false).setCachingEnabled(false);
        DataLoader<String, String> identityLoader = factory.idLoader(options, loadCalls);

        CompletableFuture<String> fa = identityLoader.load("A");
        CompletableFuture<String> fb = identityLoader.load("B");

        // caching is off
        CompletableFuture<String> fa1 = identityLoader.load("A");
        CompletableFuture<String> fb1 = identityLoader.load("B");

        List<String> values = CompletableFutureKit.allOf(asList(fa, fb, fa1, fb1)).join();

        assertThat(fa.join(), equalTo("A"));
        assertThat(fb.join(), equalTo("B"));
        assertThat(fa1.join(), equalTo("A"));
        assertThat(fb1.join(), equalTo("B"));

        assertThat(values, equalTo(asList("A", "B", "A", "B")));

        assertThat(loadCalls, equalTo(asList(
                singletonList("A"),
                singletonList("B"),
                singletonList("A"),
                singletonList("B")
        )));

    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void batches_multiple_requests_with_max_batch_size(TestDataLoaderFactory factory) {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = factory.idLoader(newOptions().setMaxBatchSize(2), loadCalls);

        CompletableFuture<Integer> f1 = identityLoader.load(1);
        CompletableFuture<Integer> f2 = identityLoader.load(2);
        CompletableFuture<Integer> f3 = identityLoader.load(3);

        identityLoader.dispatch();

        CompletableFuture.allOf(f1, f2, f3).join();

        assertThat(f1.join(), equalTo(1));
        assertThat(f2.join(), equalTo(2));
        assertThat(f3.join(), equalTo(3));

        assertThat(loadCalls, equalTo(asList(asList(1, 2), singletonList(3))));

    }

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void can_split_max_batch_sizes_correctly(TestDataLoaderFactory factory) {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = factory.idLoader(newOptions().setMaxBatchSize(5), loadCalls);

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

    @ParameterizedTest
    @MethodSource("dataLoaderFactories")
    public void should_Batch_loads_occurring_within_futures(TestDataLoaderFactory factory) {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = factory.idLoader(newOptions(), loadCalls);

        Supplier<Object> nullValue = () -> null;

        AtomicBoolean v4Called = new AtomicBoolean();

        CompletableFuture.supplyAsync(nullValue).thenAccept(v1 -> {
            identityLoader.load("a");
            CompletableFuture.supplyAsync(nullValue).thenAccept(v2 -> {
                identityLoader.load("b");
                CompletableFuture.supplyAsync(nullValue).thenAccept(v3 -> {
                    identityLoader.load("c");
                    CompletableFuture.supplyAsync(nullValue).thenAccept(
                            v4 -> {
                                identityLoader.load("d");
                                v4Called.set(true);
                            });
                });
            });
        });

        await().untilTrue(v4Called);

        identityLoader.dispatchAndJoin();

        assertThat(loadCalls, equalTo(
                singletonList(asList("a", "b", "c", "d"))));
    }

    @Test
    public void can_call_a_loader_from_a_loader() throws Exception {
        List<Collection<String>> deepLoadCalls = new ArrayList<>();
        DataLoader<String, String> deepLoader = newDataLoader(keys -> {
            deepLoadCalls.add(keys);
            return CompletableFuture.completedFuture(keys);
        });

        List<Collection<String>> aLoadCalls = new ArrayList<>();
        DataLoader<String, String> aLoader = newDataLoader(keys -> {
            aLoadCalls.add(keys);
            return deepLoader.loadMany(keys);
        });

        List<Collection<String>> bLoadCalls = new ArrayList<>();
        DataLoader<String, String> bLoader = newDataLoader(keys -> {
            bLoadCalls.add(keys);
            return deepLoader.loadMany(keys);
        });

        CompletableFuture<String> a1 = aLoader.load("A1");
        CompletableFuture<String> a2 = aLoader.load("A2");
        CompletableFuture<String> b1 = bLoader.load("B1");
        CompletableFuture<String> b2 = bLoader.load("B2");

        CompletableFuture.allOf(
                aLoader.dispatch(),
                deepLoader.dispatch(),
                bLoader.dispatch(),
                deepLoader.dispatch()
        ).join();

        assertThat(a1.get(), equalTo("A1"));
        assertThat(a2.get(), equalTo("A2"));
        assertThat(b1.get(), equalTo("B1"));
        assertThat(b2.get(), equalTo("B2"));

        assertThat(aLoadCalls, equalTo(
                singletonList(asList("A1", "A2"))));

        assertThat(bLoadCalls, equalTo(
                singletonList(asList("B1", "B2"))));

        assertThat(deepLoadCalls, equalTo(
                asList(asList("A1", "A2"), asList("B1", "B2"))));
    }

    @Test
    public void should_allow_composition_of_data_loader_calls() {
        UserManager userManager = new UserManager();

        BatchLoader<Long, User> userBatchLoader = userIds -> CompletableFuture
                .supplyAsync(() -> userIds
                        .stream()
                        .map(userManager::loadUserById)
                        .collect(Collectors.toList()));
        DataLoader<Long, User> userLoader = newDataLoader(userBatchLoader);

        AtomicBoolean gandalfCalled = new AtomicBoolean(false);
        AtomicBoolean sarumanCalled = new AtomicBoolean(false);

        userLoader.load(1L)
                .thenAccept(user -> userLoader.load(user.getInvitedByID())
                        .thenAccept(invitedBy -> {
                            gandalfCalled.set(true);
                            assertThat(invitedBy.getName(), equalTo("Manwë"));
                        }));

        userLoader.load(2L)
                .thenAccept(user -> userLoader.load(user.getInvitedByID())
                        .thenAccept(invitedBy -> {
                            sarumanCalled.set(true);
                            assertThat(invitedBy.getName(), equalTo("Aulë"));
                        }));

        List<User> allResults = userLoader.dispatchAndJoin();

        await().untilTrue(gandalfCalled);
        await().untilTrue(sarumanCalled);

        assertThat(allResults.size(), equalTo(4));
    }


    private static CacheKey<JsonObject> getJsonObjectCacheMapFn() {
        return key -> key.stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .sorted()
                .collect(Collectors.joining());
    }

    private static Stream<Arguments> dataLoaderFactories() {
        return Stream.of(
                Arguments.of(Named.of("List DataLoader", new ListDataLoaderFactory())),
                Arguments.of(Named.of("Mapped DataLoader", new MappedDataLoaderFactory())),
                Arguments.of(Named.of("Publisher DataLoader", new PublisherDataLoaderFactory())),
                Arguments.of(Named.of("Mapped Publisher DataLoader", new MappedPublisherDataLoaderFactory()))
        );
    }

    public interface TestDataLoaderFactory {
        <K> DataLoader<K, K> idLoader(DataLoaderOptions options, List<Collection<K>> loadCalls);
        <K> DataLoader<K, K> idLoaderBlowsUps(DataLoaderOptions options, List<Collection<K>> loadCalls);
        <K> DataLoader<K, Object> idLoaderAllExceptions(DataLoaderOptions options, List<Collection<K>> loadCalls);
        DataLoader<Integer, Object> idLoaderOddEvenExceptions(DataLoaderOptions options, List<Collection<Integer>> loadCalls);
    }

    private static class ListDataLoaderFactory implements TestDataLoaderFactory {
        @Override
        public <K> DataLoader<K, K> idLoader(DataLoaderOptions options, List<Collection<K>> loadCalls) {
            return newDataLoader(keys -> {
                loadCalls.add(new ArrayList<>(keys));
                return CompletableFuture.completedFuture(keys);
            }, options);
        }

        @Override
        public <K> DataLoader<K, K> idLoaderBlowsUps(
                DataLoaderOptions options, List<Collection<K>> loadCalls) {
            return newDataLoader(keys -> {
                loadCalls.add(new ArrayList<>(keys));
                return TestKit.futureError();
            }, options);
        }

        @Override
        public <K> DataLoader<K, Object> idLoaderAllExceptions(DataLoaderOptions options, List<Collection<K>> loadCalls) {
            return newDataLoader(keys -> {
                loadCalls.add(new ArrayList<>(keys));

                List<Object> errors = keys.stream().map(k -> new IllegalStateException("Error")).collect(Collectors.toList());
                return CompletableFuture.completedFuture(errors);
            }, options);
        }

        @Override
        public DataLoader<Integer, Object> idLoaderOddEvenExceptions(DataLoaderOptions options, List<Collection<Integer>> loadCalls) {
            return newDataLoader(keys -> {
                loadCalls.add(new ArrayList<>(keys));

                List<Object> errors = new ArrayList<>();
                for (Integer key : keys) {
                    if (key % 2 == 0) {
                        errors.add(key);
                    } else {
                        errors.add(new IllegalStateException("Error"));
                    }
                }
                return CompletableFuture.completedFuture(errors);
            }, options);
        }
    }

    private static class MappedDataLoaderFactory implements TestDataLoaderFactory {

        @Override
        public <K> DataLoader<K, K> idLoader(
                DataLoaderOptions options, List<Collection<K>> loadCalls) {
            return newMappedDataLoader((keys) -> {
                loadCalls.add(new ArrayList<>(keys));
                Map<K, K> map = new HashMap<>();
                keys.forEach(k -> map.put(k, k));
                return CompletableFuture.completedFuture(map);
            }, options);
        }

        @Override
        public <K> DataLoader<K, K> idLoaderBlowsUps(DataLoaderOptions options, List<Collection<K>> loadCalls) {
            return newMappedDataLoader((keys) -> {
                loadCalls.add(new ArrayList<>(keys));
                return futureError();
            }, options);
        }

        @Override
        public <K> DataLoader<K, Object> idLoaderAllExceptions(
                DataLoaderOptions options, List<Collection<K>> loadCalls) {
            return newMappedDataLoader(keys -> {
                loadCalls.add(new ArrayList<>(keys));
                Map<K, Object> errorByKey = new HashMap<>();
                keys.forEach(k -> errorByKey.put(k, new IllegalStateException("Error")));
                return CompletableFuture.completedFuture(errorByKey);
            }, options);
        }

        @Override
        public DataLoader<Integer, Object> idLoaderOddEvenExceptions(
                DataLoaderOptions options, List<Collection<Integer>> loadCalls) {
            return newMappedDataLoader(keys -> {
                loadCalls.add(new ArrayList<>(keys));

                Map<Integer, Object> errorByKey = new HashMap<>();
                for (Integer key : keys) {
                    if (key % 2 == 0) {
                        errorByKey.put(key, key);
                    } else {
                        errorByKey.put(key, new IllegalStateException("Error"));
                    }
                }
                return CompletableFuture.completedFuture(errorByKey);
            }, options);
        }
    }

    private static class PublisherDataLoaderFactory implements TestDataLoaderFactory {

        @Override
        public <K> DataLoader<K, K> idLoader(
            DataLoaderOptions options, List<Collection<K>> loadCalls) {
            return newPublisherDataLoader((keys, subscriber) -> {
                loadCalls.add(new ArrayList<>(keys));
                Flux.fromIterable(keys).subscribe(subscriber);
            }, options);
        }

        @Override
        public <K> DataLoader<K, K> idLoaderBlowsUps(DataLoaderOptions options, List<Collection<K>> loadCalls) {
            return newPublisherDataLoader((keys, subscriber) -> {
                loadCalls.add(new ArrayList<>(keys));
                Flux.<K>error(new IllegalStateException("Error")).subscribe(subscriber);
            }, options);
        }

        @Override
        public <K> DataLoader<K, Object> idLoaderAllExceptions(
            DataLoaderOptions options, List<Collection<K>> loadCalls) {
            return newPublisherDataLoaderWithTry((keys, subscriber) -> {
                loadCalls.add(new ArrayList<>(keys));
                Stream<Try<Object>> failures = keys.stream().map(k -> Try.failed(new IllegalStateException("Error")));
                Flux.fromStream(failures).subscribe(subscriber);
            }, options);
        }

        @Override
        public DataLoader<Integer, Object> idLoaderOddEvenExceptions(
            DataLoaderOptions options, List<Collection<Integer>> loadCalls) {
            return newPublisherDataLoaderWithTry((keys, subscriber) -> {
                loadCalls.add(new ArrayList<>(keys));

                List<Try<Object>> errors = new ArrayList<>();
                for (Integer key : keys) {
                    if (key % 2 == 0) {
                        errors.add(Try.succeeded(key));
                    } else {
                        errors.add(Try.failed(new IllegalStateException("Error")));
                    }
                }
                Flux.fromIterable(errors).subscribe(subscriber);
            }, options);
        }
    }

    private static class MappedPublisherDataLoaderFactory implements TestDataLoaderFactory {

        @Override
        public <K> DataLoader<K, K> idLoader(
            DataLoaderOptions options, List<Collection<K>> loadCalls) {
            return newMappedPublisherDataLoader((keys, subscriber) -> {
                loadCalls.add(new ArrayList<>(keys));
                Map<K, K> map = new HashMap<>();
                keys.forEach(k -> map.put(k, k));
                Flux.fromIterable(map.entrySet()).subscribe(subscriber);
            }, options);
        }

        @Override
        public <K> DataLoader<K, K> idLoaderBlowsUps(DataLoaderOptions options, List<Collection<K>> loadCalls) {
            return newMappedPublisherDataLoader((keys, subscriber) -> {
                loadCalls.add(new ArrayList<>(keys));
                Flux.<Map.Entry<K, K>>error(new IllegalStateException("Error")).subscribe(subscriber);
            }, options);
        }

        @Override
        public <K> DataLoader<K, Object> idLoaderAllExceptions(
            DataLoaderOptions options, List<Collection<K>> loadCalls) {
            return newMappedPublisherDataLoaderWithTry((keys, subscriber) -> {
                loadCalls.add(new ArrayList<>(keys));
                Stream<Map.Entry<K, Try<Object>>> failures = keys.stream().map(k -> Map.entry(k, Try.failed(new IllegalStateException("Error"))));
                Flux.fromStream(failures).subscribe(subscriber);
            }, options);
        }

        @Override
        public DataLoader<Integer, Object> idLoaderOddEvenExceptions(
            DataLoaderOptions options, List<Collection<Integer>> loadCalls) {
            return newMappedPublisherDataLoaderWithTry((keys, subscriber) -> {
                loadCalls.add(new ArrayList<>(keys));

                Map<Integer, Try<Object>> errorByKey = new HashMap<>();
                for (Integer key : keys) {
                    if (key % 2 == 0) {
                        errorByKey.put(key, Try.succeeded(key));
                    } else {
                        errorByKey.put(key, Try.failed(new IllegalStateException("Error")));
                    }
                }
                Flux.fromIterable(errorByKey.entrySet()).subscribe(subscriber);
            }, options);
        }
    }

    private static class ThrowingCacheMap extends CustomCacheMap {
        @Override
        public CompletableFuture<Object> get(String key) {
            throw new RuntimeException("Cache implementation failed.");
        }
    }
}

