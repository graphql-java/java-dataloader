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

package org.dataloader.nextgen;

import org.dataloader.*;
import org.dataloader.fixtures.User;
import org.dataloader.fixtures.UserManager;
import org.dataloader.impl.CompletableFutureKit;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import java.util.HashSet;
import java.util.Set;
import static java.util.concurrent.CompletableFuture.allOf;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import static org.awaitility.Awaitility.await;
import static org.dataloader.TestKit.listFrom;
import static org.dataloader.impl.CompletableFutureKit.cause;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import org.junit.After;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for {@link AutoDataLoader}.
 * <p>
 * The tests are a port of the existing tests in
 * the <a href="https://github.com/facebook/dataloader">facebook/dataloader</a> project.
 * <p>
 * Acknowledgments go to <a href="https://github.com/leebyron">Lee Byron</a> for providing excellent coverage.
 *
 * @author <a href="https://github.com/gkesler/">Greg Kesler</a>
 */
public class AutoDataLoaderTest {
    private volatile Dispatcher dispatcher;

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoDataLoaderTest.class);
    
    @Before
    public void setUp () throws Exception {
        LOGGER.info("==========================================================");
        LOGGER.info("| {}", testName.getMethodName());
        LOGGER.info("==========================================================");
        
        dispatcher = new Dispatcher();
    }
    
    @After
    public void tierDown () {
//        final AtomicBoolean closed = new AtomicBoolean(false);
//        CompletableFuture
//            .runAsync(dispatcher::close)
//            .thenAccept(v -> closed.set(true));
//        
//        await().untilAtomic(closed, is(true));
        dispatcher.close();
    }
    
    @Rule
    public TestName testName = new TestName();
    
    @Test
    public void should_Build_a_really_really_simple_data_loader() {
        AtomicBoolean success = new AtomicBoolean();
        DataLoader<Integer, Integer> identityLoader = new AutoDataLoader<>(keysAsValues(), dispatcher);

        CompletionStage<Integer> future1 = identityLoader.load(1);

        future1.thenAccept(value -> {
            assertThat(value, equalTo(1));
            success.set(true);
        });
//        identityLoader.dispatch();
        await().untilAtomic(success, is(true));
    }

    @Test
    public void should_Support_loading_multiple_keys_in_one_call() {
        AtomicBoolean success = new AtomicBoolean();
        DataLoader<Integer, Integer> identityLoader = new AutoDataLoader<>(keysAsValues(), dispatcher);

        CompletionStage<List<Integer>> futureAll = identityLoader.loadMany(asList(1, 2));
        futureAll.thenAccept(promisedValues -> {
            assertThat(promisedValues.size(), is(2));
            success.set(true);
        });
//        identityLoader.dispatch();
        await().untilAtomic(success, is(true));
        assertThat(futureAll.toCompletableFuture().join(), equalTo(asList(1, 2)));
    }

    @Test
    public void should_Resolve_to_empty_list_when_no_keys_supplied() {
        AtomicBoolean success = new AtomicBoolean();
        DataLoader<Integer, Integer> identityLoader = new AutoDataLoader<>(keysAsValues(), dispatcher);
        CompletableFuture<List<Integer>> futureEmpty = identityLoader.loadMany(emptyList());
        futureEmpty.thenAccept(promisedValues -> {
            assertThat(promisedValues.size(), is(0));
            success.set(true);
        });
//        identityLoader.dispatch();
        await().untilAtomic(success, is(true));
        assertThat(futureEmpty.join(), empty());
    }

    @Test
    public void should_Batch_multiple_requests() throws ExecutionException, InterruptedException {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = idLoader(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        CompletableFuture<Integer> future1 = identityLoader.load(1);
        CompletableFuture<Integer> future2 = identityLoader.load(2);
//        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo(1));
        assertThat(future2.get(), equalTo(2));
        assertThat(loadCalls, equalTo(singletonList(asList(1, 2))));
    }

    @Test
    public void should_Coalesce_identical_requests() throws ExecutionException, InterruptedException {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = idLoader(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        CompletableFuture<Integer> future1a = identityLoader.load(1);
        CompletableFuture<Integer> future1b = identityLoader.load(1);
        assertThat(future1a, equalTo(future1b));
//        identityLoader.dispatch();

        await().until(future1a::isDone);
        assertThat(future1a.get(), equalTo(1));
        assertThat(future1b.get(), equalTo(1));
        assertThat(loadCalls, equalTo(singletonList(singletonList(1))));
    }

    @Test
    public void should_Cache_repeated_requests() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
//        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(asList("A", "B"))));

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future3 = identityLoader.load("C");
//        identityLoader.dispatch();

        await().until(() -> future1a.isDone() && future3.isDone());
        assertThat(future1a.get(), equalTo("A"));
        assertThat(future3.get(), equalTo("C"));
        assertThat(loadCalls, equalTo(asList(asList("A", "B"), singletonList("C"))));

        CompletableFuture<String> future1b = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
        CompletableFuture<String> future3a = identityLoader.load("C");
//        identityLoader.dispatch();

        await().until(() -> future1b.isDone() && future2a.isDone() && future3a.isDone());
        assertThat(future1b.get(), equalTo("A"));
        assertThat(future2a.get(), equalTo("B"));
        assertThat(future3a.get(), equalTo("C"));
        assertThat(loadCalls, equalTo(asList(asList("A", "B"), singletonList("C"))));
    }

    @Test
    public void should_Not_redispatch_previous_load() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        CompletableFuture<String> future1 = identityLoader.load("A");
//        identityLoader.dispatch();

        await().until(() -> future1.isDone());
        CompletableFuture<String> future2 = identityLoader.load("B");
//        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(asList(singletonList("A"), singletonList("B"))));
    }

    @Test
    public void should_Cache_on_redispatch() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        CompletableFuture<String> future1 = identityLoader.load("A");
//        identityLoader.dispatch();

        await().until(() -> future1.isDone());
        CompletableFuture<List<String>> future2 = identityLoader.loadMany(asList("A", "B"));
//        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo(asList("A", "B")));
        assertThat(loadCalls, equalTo(asList(singletonList("A"), singletonList("B"))));
    }

    @Test
    public void should_Clear_single_value_in_loader() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
//        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(asList("A", "B"))));

        identityLoader.clear("A");

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
//        identityLoader.dispatch();

        await().until(() -> future1a.isDone() && future2a.isDone());
        assertThat(future1a.get(), equalTo("A"));
        assertThat(future2a.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(asList(asList("A", "B"), singletonList("A"))));
    }

    @Test
    public void should_Clear_all_values_in_loader() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
//        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(asList("A", "B"))));

        identityLoader.clearAll();

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
//        identityLoader.dispatch();

        await().until(() -> future1a.isDone() && future2a.isDone());
        assertThat(future1a.get(), equalTo("A"));
        assertThat(future2a.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(asList(asList("A", "B"), asList("A", "B"))));
    }

    @Test
    public void should_Allow_priming_the_cache() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        identityLoader.prime("A", "A");

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
//        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(singletonList("B"))));
    }

    @Test
    public void should_Not_prime_keys_that_already_exist() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        AutoDataLoader<String, String> identityLoader = idLoader(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        identityLoader.prime("A", "X");

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
//        CompletableFuture<List<String>> composite = identityLoader.dispatch();

//        await().until(composite::isDone);
        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("X"));
        assertThat(future2.get(), equalTo("B"));

        identityLoader.prime("A", "Y");
        identityLoader.prime("B", "Y");

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
//        CompletableFuture<List<String>> composite2 = identityLoader.dispatch();

//        await().until(composite2::isDone);
        await().until(() -> future1a.isDone() && future2a.isDone());
        assertThat(future1a.get(), equalTo("X"));
        assertThat(future2a.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(singletonList("B"))));
    }

    @Test
    public void should_Allow_to_forcefully_prime_the_cache() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        AutoDataLoader<String, String> identityLoader = idLoader(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        identityLoader.prime("A", "X");

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
//        CompletableFuture<List<String>> composite = identityLoader.dispatch();

//        await().until(composite::isDone);
        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("X"));
        assertThat(future2.get(), equalTo("B"));

        identityLoader.clear("A").prime("A", "Y");
        identityLoader.clear("B").prime("B", "Y");

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
//        CompletableFuture<List<String>> composite2 = identityLoader.dispatch();

//        await().until(composite2::isDone);
        await().until(() -> future1a.isDone() && future2a.isDone());
        assertThat(future1a.get(), equalTo("Y"));
        assertThat(future2a.get(), equalTo("Y"));
        assertThat(loadCalls, equalTo(singletonList(singletonList("B"))));
    }

    @Test
    public void should_not_Cache_failed_fetches_on_complete_failure() {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> errorLoader = idLoaderBlowsUps(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        CompletableFuture<Integer> future1 = errorLoader.load(1);
//        errorLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(cause(future1), instanceOf(IllegalStateException.class));

        CompletableFuture<Integer> future2 = errorLoader.load(1);
//        errorLoader.dispatch();

        await().until(future2::isDone);
        assertThat(future2.isCompletedExceptionally(), is(true));
        assertThat(cause(future2), instanceOf(IllegalStateException.class));
        assertThat(loadCalls, equalTo(asList(singletonList(1), singletonList(1))));
    }

    @Test
    public void should_Resolve_to_error_to_indicate_failure() throws ExecutionException, InterruptedException {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Object> evenLoader = idLoaderOddEvenExceptions(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        CompletableFuture<Object> future1 = evenLoader.load(1);
//        evenLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(cause(future1), instanceOf(IllegalStateException.class));

        CompletableFuture<Object> future2 = evenLoader.load(2);
//        evenLoader.dispatch();

        await().until(future2::isDone);
        assertThat(future2.get(), equalTo(2));
        assertThat(loadCalls, equalTo(asList(singletonList(1), singletonList(2))));
    }

    // Accept any kind of key.

    @Test
    public void should_Represent_failures_and_successes_simultaneously() throws ExecutionException, InterruptedException {
        AtomicBoolean success = new AtomicBoolean();
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        AutoDataLoader<Integer, Object> evenLoader = idLoaderOddEvenExceptions(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        CompletableFuture<Object> future1 = evenLoader.load(1);
        CompletableFuture<Object> future2 = evenLoader.load(2);
        CompletableFuture<Object> future3 = evenLoader.load(3);
        CompletableFuture<Object> future4 = evenLoader.load(4);
        
        await().until(allOf(future1, future2, future3, future4)::isDone);
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

    @Test
    public void should_Cache_failed_fetches() {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Object> errorLoader = idLoaderAllExceptions(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        CompletableFuture<Object> future1 = errorLoader.load(1);
//        errorLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(cause(future1), instanceOf(IllegalStateException.class));

        CompletableFuture<Object> future2 = errorLoader.load(1);
//        errorLoader.dispatch();

        await().until(future2::isDone);
        assertThat(future2.isCompletedExceptionally(), is(true));
        assertThat(cause(future2), instanceOf(IllegalStateException.class));

        assertThat(loadCalls, equalTo(singletonList(singletonList(1))));
    }

    @Test
    public void should_NOT_Cache_failed_fetches_if_told_not_too() {
        AutoDataLoaderOptions options = AutoDataLoaderOptions.newOptions().setCachingExceptionsEnabled(false);
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Object> errorLoader = idLoaderAllExceptions(options, loadCalls, dispatcher);

        CompletableFuture<Object> future1 = errorLoader.load(1);
//        errorLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(cause(future1), instanceOf(IllegalStateException.class));

        CompletableFuture<Object> future2 = errorLoader.load(1);
//        errorLoader.dispatch();

        await().until(future2::isDone);
        assertThat(future2.isCompletedExceptionally(), is(true));
        assertThat(cause(future2), instanceOf(IllegalStateException.class));

        assertThat(loadCalls, equalTo(asList(singletonList(1), singletonList(1))));
    }


    // Accepts object key in custom cacheKey function

    @Test
    public void should_Handle_priming_the_cache_with_an_error() {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = idLoader(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        identityLoader.prime(1, new IllegalStateException("Error"));

        CompletableFuture<Integer> future1 = identityLoader.load(1);
//        identityLoader.dispatch();

        await().until(future1::isDone);
        assertThat(future1.isCompletedExceptionally(), is(true));
        assertThat(cause(future1), instanceOf(IllegalStateException.class));
        assertThat(loadCalls, equalTo(emptyList()));
    }

    @Test
    public void should_Clear_values_from_cache_after_errors() {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> errorLoader = idLoaderBlowsUps(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        CompletableFuture<Integer> future1 = errorLoader.load(1);
        future1.handle((value, t) -> {
            if (t != null) {
                // Presumably determine if this error is transient, and only clear the cache in that case.
                errorLoader.clear(1);
            }
            return null;
        });
//        errorLoader.dispatch();

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
//        errorLoader.dispatch();

        await().until(future2::isDone);
        assertThat(future2.isCompletedExceptionally(), is(true));
        assertThat(cause(future2), instanceOf(IllegalStateException.class));
        assertThat(loadCalls, equalTo(asList(singletonList(1), singletonList(1))));
    }

    @Test
    public void should_Propagate_error_to_all_loads() {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> errorLoader = idLoaderBlowsUps(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        CompletableFuture<Integer> future1 = errorLoader.load(1);
        CompletableFuture<Integer> future2 = errorLoader.load(2);
//        errorLoader.dispatch();

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
    public void should_Accept_objects_as_keys() {
        List<Collection<Object>> loadCalls = new ArrayList<>();
        AutoDataLoader<Object, Object> identityLoader = idLoader(new AutoDataLoaderOptions(), loadCalls, dispatcher);

        Object keyA = new Object();
        Object keyB = new Object();

        // Fetches as expected

        CompletableFuture<Object> a = identityLoader.load(keyA);
        CompletableFuture<Object> b = identityLoader.load(keyB);

//        await().until(identityLoader.dispatch()::isDone);
        await().until(() -> a.isDone() && b.isDone());
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

        CompletableFuture<Object> nextA = identityLoader.load(keyA);
        CompletableFuture<Object> nextB = identityLoader.load(keyB);

//        await().until(identityLoader.dispatch()::isDone);
        await().until(allOf(nextA, nextA)::isDone);
        identityLoader.dispatch().thenAccept(promisedValues -> {
            assertThat(promisedValues.get(0), equalTo(keyA));
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
                idLoader(newOptions().setCachingEnabled(false), loadCalls, dispatcher);

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
//        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(loadCalls, equalTo(singletonList(asList("A", "B"))));

        CompletableFuture<String> future1a = identityLoader.load("A");
        CompletableFuture<String> future3 = identityLoader.load("C");
//        identityLoader.dispatch();

        await().until(() -> future1a.isDone() && future3.isDone());
        assertThat(future1a.get(), equalTo("A"));
        assertThat(future3.get(), equalTo("C"));
        assertThat(loadCalls, equalTo(asList(asList("A", "B"), asList("A", "C"))));

        CompletableFuture<String> future1b = identityLoader.load("A");
        CompletableFuture<String> future2a = identityLoader.load("B");
        CompletableFuture<String> future3a = identityLoader.load("C");
//        identityLoader.dispatch();

        await().until(() -> future1b.isDone() && future2a.isDone() && future3a.isDone());
        assertThat(future1b.get(), equalTo("A"));
        assertThat(future2a.get(), equalTo("B"));
        assertThat(future3a.get(), equalTo("C"));
        assertThat(loadCalls, equalTo(asList(asList("A", "B"),
                asList("A", "C"), asList("A", "B", "C"))));
    }

    @Test
    public void should_work_with_duplicate_keys_when_caching_disabled() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader =
                idLoader(newOptions().setCachingEnabled(false), loadCalls, dispatcher);

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        CompletableFuture<String> future3 = identityLoader.load("A");
//        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone() && future3.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(future3.get(), equalTo("A"));
        assertThat(loadCalls, equalTo(singletonList(asList("A", "B", "A"))));
    }

    @Test
    public void should_work_with_duplicate_keys_when_caching_enabled() throws ExecutionException, InterruptedException {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader =
                idLoader(newOptions().setCachingEnabled(true), loadCalls, dispatcher);

        CompletableFuture<String> future1 = identityLoader.load("A");
        CompletableFuture<String> future2 = identityLoader.load("B");
        CompletableFuture<String> future3 = identityLoader.load("A");
//        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone() && future3.isDone());
        assertThat(future1.get(), equalTo("A"));
        assertThat(future2.get(), equalTo("B"));
        assertThat(future3.get(), equalTo("A"));
        assertThat(loadCalls, equalTo(singletonList(asList("A", "B"))));
    }

    // It is resilient to job queue ordering

    @Test
    public void should_Accept_objects_with_a_complex_key() throws ExecutionException, InterruptedException {
        List<Collection<JsonObject>> loadCalls = new ArrayList<>();
        AutoDataLoaderOptions options = newOptions().setCacheKeyFunction(getJsonObjectCacheMapFn());
        DataLoader<JsonObject, Integer> identityLoader = idLoader(options, loadCalls, dispatcher);

        JsonObject key1 = new JsonObject().put("id", 123);
        JsonObject key2 = new JsonObject().put("id", 123);

        CompletableFuture<Integer> future1 = identityLoader.load(key1);
        CompletableFuture<Integer> future2 = identityLoader.load(key2);
//        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(loadCalls, equalTo(singletonList(singletonList(key1))));
        assertThat(future1.get(), equalTo(key1));
        assertThat(future2.get(), equalTo(key1));
    }

    // Helper methods

    @Test
    public void should_Clear_objects_with_complex_key() throws ExecutionException, InterruptedException {
        List<Collection<JsonObject>> loadCalls = new ArrayList<>();
        AutoDataLoaderOptions options = newOptions().setCacheKeyFunction(getJsonObjectCacheMapFn());
        DataLoader<JsonObject, Integer> identityLoader = idLoader(options, loadCalls, dispatcher);

        JsonObject key1 = new JsonObject().put("id", 123);
        JsonObject key2 = new JsonObject().put("id", 123);

        CompletableFuture<Integer> future1 = identityLoader.load(key1);
//        identityLoader.dispatch();

        await().until(future1::isDone);
        identityLoader.clear(key2); // clear equivalent object key

        CompletableFuture<Integer> future2 = identityLoader.load(key1);
//        identityLoader.dispatch();

        await().until(future2::isDone);
        assertThat(loadCalls, equalTo(asList(singletonList(key1), singletonList(key1))));
        assertThat(future1.get(), equalTo(key1));
        assertThat(future2.get(), equalTo(key1));
    }

    @Test
    public void should_Accept_objects_with_different_order_of_keys() throws ExecutionException, InterruptedException {
        List<Collection<JsonObject>> loadCalls = new ArrayList<>();
        AutoDataLoaderOptions options = newOptions().setCacheKeyFunction(getJsonObjectCacheMapFn());
        DataLoader<JsonObject, Integer> identityLoader = idLoader(options, loadCalls, dispatcher);

        JsonObject key1 = new JsonObject().put("a", 123).put("b", 321);
        JsonObject key2 = new JsonObject().put("b", 321).put("a", 123);

        // Fetches as expected

        CompletableFuture<Integer> future1 = identityLoader.load(key1);
        CompletableFuture<Integer> future2 = identityLoader.load(key2);
//        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(loadCalls, equalTo(singletonList(singletonList(key1))));
        assertThat(loadCalls.size(), equalTo(1));
        assertThat(future1.get(), equalTo(key1));
        assertThat(future2.get(), equalTo(key1));
    }

    @Test
    public void should_Allow_priming_the_cache_with_an_object_key() throws ExecutionException, InterruptedException {
        List<Collection<JsonObject>> loadCalls = new ArrayList<>();
        AutoDataLoaderOptions options = newOptions().setCacheKeyFunction(getJsonObjectCacheMapFn());
        DataLoader<JsonObject, JsonObject> identityLoader = idLoader(options, loadCalls, dispatcher);

        JsonObject key1 = new JsonObject().put("id", 123);
        JsonObject key2 = new JsonObject().put("id", 123);

        identityLoader.prime(key1, key1);

        CompletableFuture<JsonObject> future1 = identityLoader.load(key1);
        CompletableFuture<JsonObject> future2 = identityLoader.load(key2);
//        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(loadCalls, equalTo(emptyList()));
        assertThat(future1.get(), equalTo(key1));
        assertThat(future2.get(), equalTo(key1));
    }

    @Test
    public void should_Accept_a_custom_cache_map_implementation() throws ExecutionException, InterruptedException {
        CustomCacheMap customMap = new CustomCacheMap();
        List<Collection<String>> loadCalls = new ArrayList<>();
        AutoDataLoaderOptions options = newOptions().setCacheMap(customMap);
        AutoDataLoader<String, String> identityLoader = idLoader(options, loadCalls, dispatcher);

        // Fetches as expected

        CompletableFuture future1 = identityLoader.load("a");
        CompletableFuture future2 = identityLoader.load("b");
//        CompletableFuture<List<String>> composite = identityLoader.dispatch();

//        await().until(composite::isDone);
        await().until(allOf(future1, future2)::isDone);
        assertThat(future1.get(), equalTo("a"));
        assertThat(future2.get(), equalTo("b"));

        assertThat(loadCalls, equalTo(singletonList(asList("a", "b"))));
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "b").toArray());

        CompletableFuture future3 = identityLoader.load("c");
        CompletableFuture future2a = identityLoader.load("b");
//        composite = identityLoader.dispatch();

        await().until(() -> future3.isDone() && future2a.isDone());
        assertThat(future3.get(), equalTo("c"));
        assertThat(future2a.get(), equalTo("b"));

        assertThat(loadCalls, equalTo(asList(asList("a", "b"), singletonList("c"))));
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "b", "c").toArray());

        // Supports clear

        identityLoader.clear("b");
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "c").toArray());

        CompletableFuture future2b = identityLoader.load("b");
//        composite = identityLoader.dispatch();

//        await().until(composite::isDone);
        await().until(future2b::isDone);
        assertThat(future2b.get(), equalTo("b"));
        assertThat(loadCalls, equalTo(asList(asList("a", "b"),
                singletonList("c"), singletonList("b"))));
        assertArrayEquals(customMap.stash.keySet().toArray(), asList("a", "c", "b").toArray());

        // Supports clear all

        identityLoader.clearAll();
        assertArrayEquals(customMap.stash.keySet().toArray(), emptyList().toArray());
    }

    @Test
    public void batching_disabled_should_dispatch_immediately() throws Exception {
        List<Collection<String>> loadCalls = new ArrayList<>();
        AutoDataLoaderOptions options = newOptions().setBatchingEnabled(false);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls, dispatcher);

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

    @Test
    public void batching_disabled_and_caching_disabled_should_dispatch_immediately_and_forget() throws Exception {
        List<Collection<String>> loadCalls = new ArrayList<>();
        AutoDataLoaderOptions options = newOptions().setBatchingEnabled(false).setCachingEnabled(false);
        DataLoader<String, String> identityLoader = idLoader(options, loadCalls, dispatcher);

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

    @Test
    public void batches_multiple_requests_with_max_batch_size() throws Exception {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        DataLoader<Integer, Integer> identityLoader = idLoader(newOptions().setMaxBatchSize(2), loadCalls, dispatcher);

        CompletableFuture<Integer> f1 = identityLoader.load(1);
        CompletableFuture<Integer> f2 = identityLoader.load(2);
        CompletableFuture<Integer> f3 = identityLoader.load(3);

//        identityLoader.dispatch();

        await().until(allOf(f1, f2, f3)::isDone);

        assertThat(f1.join(), equalTo(1));
        assertThat(f2.join(), equalTo(2));
        assertThat(f3.join(), equalTo(3));

        assertThat(loadCalls, equalTo(asList(asList(1, 2), singletonList(3))));

    }

    @Test
    public void can_split_max_batch_sizes_correctly() throws Exception {
        List<Collection<Integer>> loadCalls = new ArrayList<>();
        AutoDataLoader<Integer, Integer> identityLoader = idLoader(newOptions().setMaxBatchSize(5), loadCalls, dispatcher);

        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            futures.add(identityLoader.load(i));
        }
        List<Collection<Integer>> expectedCalls = new ArrayList<>();
        expectedCalls.add(listFrom(0, 5));
        expectedCalls.add(listFrom(5, 10));
        expectedCalls.add(listFrom(10, 15));
        expectedCalls.add(listFrom(15, 20));
        expectedCalls.add(listFrom(20, 21));

        await().until(allOf(futures.toArray(new CompletableFuture[futures.size()]))::isDone);
        List<Integer> result = identityLoader.dispatch().join();

        assertThat(result, equalTo(listFrom(0, 21)));
        assertThat(loadCalls, equalTo(expectedCalls));
    }

    @Test
    @Ignore
    public void can_split_max_batch_sizes_correctly_10_times () throws Exception {
        for (int i = 0; i < 10; i++) {
            can_split_max_batch_sizes_correctly();
        }
    }
    
    @Test
    public void should_Batch_loads_occurring_within_futures() {
        List<Collection<String>> loadCalls = new ArrayList<>();
        DataLoader<String, String> identityLoader = idLoader(newOptions(), loadCalls, dispatcher);

        Supplier<Object> nullValue = () -> null;

        final AtomicBoolean v4Called = new AtomicBoolean();

        CompletableFuture.supplyAsync(nullValue).thenAccept(v1 -> {
            LOGGER.debug("before load({}) called", "a");
            identityLoader.load("a");
            LOGGER.debug("after load({}) called", "a");
            CompletableFuture.supplyAsync(nullValue).thenAccept(v2 -> {
                LOGGER.debug("before load({}) called", "b");
                identityLoader.load("b");
                LOGGER.debug("after load({}) called", "b");
                CompletableFuture.supplyAsync(nullValue).thenAccept(v3 -> {
                    LOGGER.debug("before load({}) called", "c");
                    identityLoader.load("c");
                    LOGGER.debug("after load({}) called", "c");
                    CompletableFuture.supplyAsync(nullValue).thenAccept(
                            v4 -> {
                                LOGGER.debug("before load({}) called", "d");
                                identityLoader.load("d");                                
                                LOGGER.debug("after load({}) called", "d");
                                v4Called.set(true);
                                LOGGER.debug("v4Called = true");
                            });
                });
            });
        });

        LOGGER.debug("awaiting for v4Called == true ...");
        await().untilTrue(v4Called);
        LOGGER.debug("woke up!");

//        identityLoader.dispatchAndJoin();

        assertThat(flatList(loadCalls), equalTo(asList("a", "b", "c", "d")));
//        assertThat(loadCalls, equalTo(
//                singletonList(asList("a", "b", "c", "d"))));
    }
    
    @Test
    public void can_call_a_loader_from_a_loader() throws Exception {
        Set<String> deepLoadCalls = new HashSet<>();
        DataLoader<String, String> deepLoader = new AutoDataLoader(keys -> {
            deepLoadCalls.addAll(keys);
            return CompletableFuture.completedFuture(keys);
        }, dispatcher);

        List<Collection<String>> aLoadCalls = new ArrayList<>();
        DataLoader<String, String> aLoader = new AutoDataLoader<>(keys -> {
            aLoadCalls.add(keys);
            return deepLoader.loadMany(keys);
        }, dispatcher);

        List<Collection<String>> bLoadCalls = new ArrayList<>();
        DataLoader<String, String> bLoader = new AutoDataLoader<>(keys -> {
            bLoadCalls.add(keys);
            return deepLoader.loadMany(keys);
        }, dispatcher);

        CompletableFuture<String> a1 = aLoader.load("A1");
        CompletableFuture<String> a2 = aLoader.load("A2");
        CompletableFuture<String> b1 = bLoader.load("B1");
        CompletableFuture<String> b2 = bLoader.load("B2");
//
//        CompletableFuture.allOf(
//                aLoader.dispatch(),
//                deepLoader.dispatch(),
//                bLoader.dispatch(),
//                deepLoader.dispatch()
//        ).join();
        await().until(allOf(a1, a2, b1, b2)::isDone);

        assertThat(a1.get(), equalTo("A1"));
        assertThat(a2.get(), equalTo("A2"));
        assertThat(b1.get(), equalTo("B1"));
        assertThat(b2.get(), equalTo("B2"));

        assertThat(aLoadCalls, equalTo(
                singletonList(asList("A1", "A2"))));

        assertThat(bLoadCalls, equalTo(
                singletonList(asList("B1", "B2"))));

        assertThat(deepLoadCalls, equalTo(new HashSet<>(asList("A1", "A2", "B1", "B2"))));
    }

    @Test
    @Ignore
    public void should_allow_composition_of_data_loader_calls() throws Exception {
        UserManager userManager = new UserManager();

        BatchLoader<Long, User> userBatchLoader = userIds -> CompletableFuture
                .supplyAsync(() -> userIds
                        .stream()
                        .map(userManager::loadUserById)
                        .collect(Collectors.toList()));
        AutoDataLoader<Long, User> userLoader = new AutoDataLoader<>(userBatchLoader, dispatcher);

        AtomicBoolean gandalfCalled = new AtomicBoolean(false);
        AtomicBoolean sarumanCalled = new AtomicBoolean(false);

        CompletableFuture<?> gandalf = userLoader.load(1L)
                .thenAccept(user -> userLoader.load(user.getInvitedByID())
                        .thenAccept(invitedBy -> {
                            gandalfCalled.set(true);
                            assertThat(invitedBy.getName(), equalTo("Manwë"));
                        }));

        CompletableFuture<?> saruman = userLoader.load(2L)
                .thenAccept(user -> userLoader.load(user.getInvitedByID())
                        .thenAccept(invitedBy -> {
                            sarumanCalled.set(true);
                            assertThat(invitedBy.getName(), equalTo("Aulë"));
                        }));

        await().untilTrue(gandalfCalled);
        await().untilTrue(sarumanCalled);
        
        List<User> allResults = userLoader.dispatch().join();
        assertThat(allResults.size(), equalTo(4));
    }


    private static CacheKey<JsonObject> getJsonObjectCacheMapFn() {
        return key -> key.stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .sorted()
                .collect(Collectors.joining());
    }

    private static <K, V> AutoDataLoader<K, V> idLoader(AutoDataLoaderOptions options, List<Collection<K>> loadCalls, Dispatcher dispatcher) {
        return new AutoDataLoader<>(keys -> {
            loadCalls.add(new ArrayList<>(keys));
            @SuppressWarnings("unchecked")
            List<V> values = keys.stream()
                    .map(k -> (V) k)
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(values);
        }, options.setDispatcher(dispatcher));
    }

    private static <K, V> AutoDataLoader<K, V> idLoaderBlowsUps(
            AutoDataLoaderOptions options, List<Collection<K>> loadCalls, Dispatcher dispatcher) {
        return new AutoDataLoader<>(keys -> {
            loadCalls.add(new ArrayList<>(keys));
            return TestKit.futureError();
        }, options.setDispatcher(dispatcher));
    }

    private static <K> AutoDataLoader<K, Object> idLoaderAllExceptions(
            AutoDataLoaderOptions options, List<Collection<K>> loadCalls, Dispatcher dispatcher) {
        return new AutoDataLoader<>(keys -> {
            loadCalls.add(new ArrayList<>(keys));

            List<Object> errors = keys.stream().map(k -> new IllegalStateException("Error")).collect(Collectors.toList());
            return CompletableFuture.completedFuture(errors);
        }, options.setDispatcher(dispatcher));
    }

    private static AutoDataLoader<Integer, Object> idLoaderOddEvenExceptions(
            AutoDataLoaderOptions options, List<Collection<Integer>> loadCalls, Dispatcher dispatcher) {
        return new AutoDataLoader<>(keys -> {
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
        }, options.setDispatcher(dispatcher));
    }


    private <T> BatchLoader<T, T> keysAsValues() {
        return CompletableFuture::completedFuture;
    }

    private static AutoDataLoaderOptions newOptions () {
        return new AutoDataLoaderOptions();
    }
    
    private static <V> List<V> flatList (List<Collection<V>> list) {
        return list
            .stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }
}

