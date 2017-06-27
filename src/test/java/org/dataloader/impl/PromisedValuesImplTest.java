package org.dataloader.impl;

import org.dataloader.PromisedValues;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Arrays.asList;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class PromisedValuesImplTest {

    @Test
    public void will_compose_multiple_futures() throws Exception {

        CompletableFuture<Integer> f1 = CompletableFuture.supplyAsync(() -> 666);
        CompletableFuture<Integer> f2 = CompletableFuture.supplyAsync(() -> 999);

        PromisedValues<Integer> promisedValues = PromisedValues.allOf(f1, f2);

        assertThat(promisedValues.size(), equalTo(2));

        await().until(promisedValues::isDone, is(true));
        assertThat(promisedValues.toList(), equalTo(asList(666, 999)));
    }

    @Test
    public void will_allow_extra_composition_of_futures() throws Exception {

        CompletableFuture<Integer> f1 = CompletableFuture.supplyAsync(() -> 666);
        CompletableFuture<Integer> f2 = CompletableFuture.supplyAsync(() -> 999);

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
    }

    @Test
    public void empty_list_works() throws Exception {
        PromisedValues<Object> promisedValues = PromisedValues.allOf(Collections.emptyList());
        assertThat(promisedValues.succeeded(), equalTo(true));
        assertThat(promisedValues.failed(), equalTo(false));
        assertThat(promisedValues.cause(), nullValue());
    }
}