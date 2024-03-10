package org.dataloader.impl;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Arrays.asList;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class PromisedValuesImplTest {

    @Test
    public void will_compose_multiple_futures() throws Exception {

        CompletableFuture<Integer> f1 = supplyAsync(() -> 666);
        CompletableFuture<Integer> f2 = supplyAsync(() -> 999);

        PromisedValues<Integer> promisedValues = PromisedValues.allOf(f1, f2);

        assertThat(promisedValues.size(), equalTo(2));

        await().until(promisedValues::isDone, is(true));
        assertThat(promisedValues.toList(), equalTo(asList(666, 999)));
    }

    @Test
    public void will_allow_extra_composition_of_futures() throws Exception {

        CompletableFuture<Integer> f1 = supplyAsync(() -> 666);
        CompletableFuture<Integer> f2 = supplyAsync(() -> 999);

        PromisedValues<Integer> promisedValues = PromisedValues.allOf(f1, f2);

        AtomicBoolean acceptCalled = new AtomicBoolean(false);
        promisedValues.toCompletableFuture().thenAccept(list -> {
            acceptCalled.set(true);
            assertThat(list, equalTo(asList(666, 999)));
        });

        await().untilTrue(acceptCalled);

        assertThat(promisedValues.isDone(), equalTo(true));
        assertThat(promisedValues.succeeded(), equalTo(true));
        assertThat(promisedValues.failed(), equalTo(false));
        assertThat(promisedValues.cause(), nullValue());


        assertThat(promisedValues.size(), equalTo(2));
        assertThat(promisedValues.toList(), equalTo(asList(666, 999)));
        assertThat(promisedValues.join(), equalTo(asList(666, 999)));

    }

    @Test
    public void empty_list_works() throws Exception {
        PromisedValues<Object> promisedValues = PromisedValues.allOf(Collections.emptyList());

        assertThat(promisedValues.isDone(), equalTo(true));
        assertThat(promisedValues.succeeded(), equalTo(true));
        assertThat(promisedValues.failed(), equalTo(false));
        assertThat(promisedValues.cause(), nullValue());

        assertThat(promisedValues.size(), equalTo(0));
        assertThat(promisedValues.toList(), equalTo(Collections.emptyList()));
        assertThat(promisedValues.join(), equalTo(Collections.emptyList()));
    }


    @Test
    public void can_compose_multiple_promised_values() throws Exception {

        CompletableFuture<String> a1 = supplyAsync(() -> "A1");
        CompletableFuture<String> a2 = supplyAsync(() -> "A2");

        PromisedValues<String> promisedValues1 = PromisedValues.allOf(a1, a2);

        CompletableFuture<String> b1 = supplyAsync(() -> "B1");
        CompletableFuture<String> b2 = supplyAsync(() -> "B2");

        PromisedValues<String> promisedValues2 = PromisedValues.allOf(b1, b2);

        PromisedValues<String> combinePromisedValues = PromisedValues.allPromisedValues(promisedValues1, promisedValues2);

        List<String> joinedResult = combinePromisedValues.toCompletableFuture().join();

        assertThat(joinedResult.size(), equalTo(4));
        assertThat(joinedResult, equalTo(asList("A1", "A2", "B1", "B2")));

        assertThat(combinePromisedValues.size(), equalTo(4));
        assertThat(combinePromisedValues.succeeded(), equalTo(true));
        assertThat(combinePromisedValues.failed(), equalTo(false));
        assertThat(combinePromisedValues.isDone(), equalTo(true));

        assertThat(combinePromisedValues.get(0), equalTo("A1"));
        assertThat(combinePromisedValues.get(1), equalTo("A2"));
        assertThat(combinePromisedValues.get(2), equalTo("B1"));
        assertThat(combinePromisedValues.get(3), equalTo("B2"));

        assertThat(combinePromisedValues.toList(), equalTo(asList("A1", "A2", "B1", "B2")));

    }

    @Test
    public void can_compose_multiple_promised_values_and_fail_as_one() throws Exception {

        CompletableFuture<String> a1 = supplyAsync(() -> "A1");
        CompletableFuture<String> a2 = supplyAsync(() -> "A2");

        PromisedValues<String> promisedValues1 = PromisedValues.allOf(a1, a2);

        CompletableFuture<String> b1 = supplyAsync(() -> {
            throw new IllegalStateException("Bang");
        });
        CompletableFuture<String> b2 = supplyAsync(() -> "B2");

        PromisedValues<String> promisedValues2 = PromisedValues.allOf(b1, b2);

        PromisedValues<String> combinePromisedValues = PromisedValues.allPromisedValues(promisedValues1, promisedValues2);

        AtomicBoolean acceptCalled = new AtomicBoolean();
        combinePromisedValues.thenAccept(pv -> {

            acceptCalled.set(true);

            assertThat(pv.succeeded(), equalTo(false));
            assertThat(pv.failed(), equalTo(true));
            assertThat(pv.isDone(), equalTo(true));

            assertThat(pv.size(), equalTo(4));

            assertThat(pv.get(0), equalTo("A1"));
            assertThat(pv.succeeded(0), equalTo(true));
            assertThat(pv.cause(0), nullValue());

            assertThat(pv.get(1), equalTo("A2"));
            assertThat(pv.succeeded(1), equalTo(true));
            assertThat(pv.cause(1), nullValue());

            // the one that went bad
            assertThat(pv.get(2), nullValue());
            assertThat(pv.succeeded(2), equalTo(false));
            assertThat(pv.cause(2), instanceOf(IllegalStateException.class));

            assertThat(pv.get(3), equalTo("B2"));
            assertThat(pv.succeeded(3), equalTo(true));
            assertThat(pv.cause(3), nullValue());

            assertThat(pv.toList(), equalTo(asList("A1", "A2", null, "B2")));

        }).join();

        await().untilTrue(acceptCalled);

    }

    @Test
    public void exceptions_are_captured_and_reported() throws Exception {
        CompletableFuture<Integer> f1 = supplyAsync(() -> 1);
        CompletableFuture<Integer> f2 = supplyAsync(() -> {
            throw new IllegalStateException("bang");
        });

        PromisedValues<Integer> promisedValues = PromisedValues.allOf(f1, f2);
        List<Integer> result = promisedValues.toCompletableFuture().join();

        assertThat(promisedValues.isDone(), equalTo(true));
        assertThat(promisedValues.succeeded(), equalTo(false));
        assertThat(promisedValues.cause(), instanceOf(IllegalStateException.class));

        assertThat(promisedValues.toList(), equalTo(asList(1, null)));
        assertThat(result, equalTo(asList(1, null)));
    }

    @Test
    public void type_generics_compile_as_expected() throws Exception {

        PromisedValues<String> pvList = PromisedValues.allOf(Collections.singletonList(new CompletableFuture<>()));
        PromisedValues<String> pvList2 = PromisedValues.allOf(Collections.<CompletionStage<String>>singletonList(new CompletableFuture<>()));

        assertThat(pvList, notNullValue());
        assertThat(pvList2, notNullValue());

        PromisedValues<String> pv2args = PromisedValues.allOf(new CompletableFuture<>(), new CompletableFuture<>());
        PromisedValues<String> pv3args = PromisedValues.allOf(new CompletableFuture<>(), new CompletableFuture<>(), new CompletableFuture<>());
        PromisedValues<String> pv4args = PromisedValues.allOf(new CompletableFuture<>(), new CompletableFuture<>(), new CompletableFuture<>(), new CompletableFuture<>());

        assertThat(pv2args, notNullValue());
        assertThat(pv3args, notNullValue());
        assertThat(pv4args, notNullValue());

        PromisedValues<String> pvListOfPVs = PromisedValues.allPromisedValues(Arrays.asList(pv2args, pv3args));

        assertThat(pvListOfPVs, notNullValue());

        PromisedValues<String> pv2ArgsOfPVs = PromisedValues.allPromisedValues(pv2args, pv3args);
        PromisedValues<String> pv3ArgsOfPVs = PromisedValues.allPromisedValues(pv2args, pv3args, pv4args);
        PromisedValues<String> pv4ArgsOfPVs = PromisedValues.allPromisedValues(pv2args, pv3args, pv4args, pv2args);

        assertThat(pv2ArgsOfPVs, notNullValue());
        assertThat(pv3ArgsOfPVs, notNullValue());
        assertThat(pv4ArgsOfPVs, notNullValue());
    }
}