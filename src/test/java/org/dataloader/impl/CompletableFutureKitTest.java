package org.dataloader.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompletableFutureKitTest {

    @Test
    void failedFuture() {
        CompletableFuture<Object> cf = CompletableFutureKit.failedFuture(new RuntimeException("BANG"));
        assertThat(cf.isCompletedExceptionally(), equalTo(true));
        CompletionException completionException = assertThrows(CompletionException.class, cf::join);
        assertThat(completionException.getCause().getMessage(), equalTo("BANG"));
    }

    @Test
    void success() {
        CompletableFuture<Object> cf = CompletableFutureKit.success("BANG");
        assertThat(cf.isCompletedExceptionally(), equalTo(false));
        assertThat(cf.join(), equalTo("BANG"));
    }

    @Test
    void cause() {
        CompletableFuture<Object> cf = CompletableFutureKit.failedFuture(new RuntimeException("BANG"));
        assertThat(CompletableFutureKit.cause(cf), instanceOf(RuntimeException.class));
    }

    @Test
    void succeeded() {
        CompletableFuture<Object> cf = CompletableFutureKit.success("BANG");
        assertThat(CompletableFutureKit.succeeded(cf), equalTo(true));
        assertThat(CompletableFutureKit.failed(cf), equalTo(false));
    }

    @Test
    void failed() {
        CompletableFuture<Object> cf = CompletableFutureKit.failedFuture(new RuntimeException("BANG"));
        assertThat(CompletableFutureKit.failed(cf), equalTo(true));
        assertThat(CompletableFutureKit.succeeded(cf), equalTo(false));
    }

    @Test
    void allOf() {
        CompletableFuture<List<String>> list = CompletableFutureKit.allOf(List.of(
                CompletableFutureKit.success("1"),
                CompletableFutureKit.success("2"),
                CompletableFutureKit.success("3")
        ));

        assertThat(list.join(), equalTo(List.of("1", "2", "3")));
    }

    @Test
    void flatMapAllOf() {

        CompletableFuture<List<String>> list1 = CompletableFutureKit.allOf(List.of(
                CompletableFutureKit.success("1"),
                CompletableFutureKit.success("2"),
                CompletableFutureKit.success("3")
        ));
        CompletableFuture<List<String>> list2 = CompletableFutureKit.allOf(List.of(
                CompletableFutureKit.success("4"),
                CompletableFutureKit.success("5"),
                CompletableFutureKit.success("6")
        ));

        CompletableFuture<List<String>> list = CompletableFutureKit.allOfFlatMap(List.of(list1, list2));
        assertThat(list.join(), equalTo(List.of("1", "2", "3", "4", "5", "6")));

    }

    @Test
    void run() {
        AtomicBoolean ran = new AtomicBoolean(false);
        Runnable runnable = () -> ran.set(true);

        CompletableFuture<?> runCF = CompletableFutureKit.run(runnable);
        runCF.join();
        assertThat(ran.get(), equalTo(true));

        runnable = () -> {
            throw new RuntimeException("BANG");
        };

        runCF = CompletableFutureKit.run(runnable);

        CompletionException completionException = assertThrows(CompletionException.class, runCF::join);
        assertThat(completionException.getCause().getMessage(), equalTo("BANG"));


    }
}