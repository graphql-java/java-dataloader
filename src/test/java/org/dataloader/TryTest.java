package org.dataloader;

import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

@SuppressWarnings("ConstantConditions")
public class TryTest {

    interface RunThatCanThrow {
        void run() throws Throwable;
    }

    private void expectThrowable(RunThatCanThrow runnable, Class<? extends Throwable> throwableClass) {
        try {
            runnable.run();
        } catch (Throwable e) {
            if (throwableClass.isInstance(e)) {
                return;
            }
        }
        Assert.fail("Expected throwable :  " + throwableClass.getName());
    }

    private void assertFailure(Try<String> sTry, String expectedString) {
        assertThat(sTry.isSuccess(), equalTo(false));
        assertThat(sTry.isFailure(), equalTo(true));
        assertThat(sTry.getThrowable() instanceof RuntimeException, equalTo(true));
        assertThat(sTry.getThrowable().getMessage(), equalTo(expectedString));

        expectThrowable(sTry::get, UnsupportedOperationException.class);
    }

    private void assertSuccess(Try<String> sTry, String expectedStr) {
        assertThat(sTry.isSuccess(), equalTo(true));
        assertThat(sTry.isFailure(), equalTo(false));
        assertThat(sTry.get(), equalTo(expectedStr));

        expectThrowable(sTry::getThrowable, UnsupportedOperationException.class);
    }

    @Test
    public void tryFailed() throws Exception {
        Try<String> sTry = Try.failed(new RuntimeException("Goodbye Cruel World"));

        assertFailure(sTry, "Goodbye Cruel World");
    }

    @Test
    public void trySucceeded() throws Exception {
        Try<String> sTry = Try.succeeded("Hello World");

        assertSuccess(sTry, "Hello World");
    }

    @Test
    public void tryCallable() throws Exception {
        Try<String> sTry = Try.tryCall(() -> "Hello World");

        assertSuccess(sTry, "Hello World");

        sTry = Try.tryCall(() -> {
            throw new RuntimeException("Goodbye Cruel World");
        });

        assertFailure(sTry, "Goodbye Cruel World");
    }

    @Test
    public void triedStage() throws Exception {
        CompletionStage<Try<String>> sTry = Try.tryStage(CompletableFuture.completedFuture("Hello World"));

        sTry.thenAccept(stageTry -> assertSuccess(stageTry, "Hello World"));
        sTry.toCompletableFuture().join();

        CompletableFuture<String> failure = new CompletableFuture<>();
        failure.completeExceptionally(new RuntimeException("Goodbye Cruel World"));
        sTry = Try.tryStage(failure);

        sTry.thenAccept(stageTry -> assertFailure(stageTry, "Goodbye Cruel World"));
        sTry.toCompletableFuture().join();
    }

    @Test
    public void map() throws Exception {
        Try<Integer> iTry = Try.succeeded(666);

        Try<String> sTry = iTry.map(Object::toString);
        assertSuccess(sTry, "666");

        iTry = Try.failed(new RuntimeException("Goodbye Cruel World"));

        sTry = iTry.map(Object::toString);
        assertFailure(sTry, "Goodbye Cruel World");
    }

    @Test
    public void flatMap() throws Exception {
        Function<Integer, Try<String>> intToStringFunc = i -> Try.succeeded(i.toString());

        Try<Integer> iTry = Try.succeeded(666);

        Try<String> sTry = iTry.flatMap(intToStringFunc);
        assertSuccess(sTry, "666");


        iTry = Try.failed(new RuntimeException("Goodbye Cruel World"));
        sTry = iTry.flatMap(intToStringFunc);

        assertFailure(sTry, "Goodbye Cruel World");

    }

    @Test
    public void toOptional() throws Exception {
        Try<Integer> iTry = Try.succeeded(666);
        Optional<Integer> optional = iTry.toOptional();
        assertThat(optional.isPresent(), equalTo(true));
        assertThat(optional.get(), equalTo(666));

        iTry = Try.failed(new RuntimeException("Goodbye Cruel World"));
        optional = iTry.toOptional();
        assertThat(optional.isPresent(), equalTo(false));
    }

    @Test
    public void orElse() throws Exception {
        Try<String> sTry = Try.tryCall(() -> "Hello World");

        String result = sTry.orElse("other");
        assertThat(result, equalTo("Hello World"));

        sTry = Try.failed(new RuntimeException("Goodbye Cruel World"));
        result = sTry.orElse("other");
        assertThat(result, equalTo("other"));
    }

    @Test
    public void orElseGet() throws Exception {
        Try<String> sTry = Try.tryCall(() -> "Hello World");

        String result = sTry.orElseGet(() -> "other");
        assertThat(result, equalTo("Hello World"));

        sTry = Try.failed(new RuntimeException("Goodbye Cruel World"));
        result = sTry.orElseGet(() -> "other");
        assertThat(result, equalTo("other"));
    }

    @Test
    public void reThrow() throws Exception {
        Try<String> sTry = Try.failed(new RuntimeException("Goodbye Cruel World"));
        expectThrowable(sTry::reThrow, RuntimeException.class);


        sTry = Try.tryCall(() -> "Hello World");
        expectThrowable(sTry::reThrow, UnsupportedOperationException.class);
    }

    @Test
    public void forEach() throws Exception {
        AtomicReference<String> sRef = new AtomicReference<>();
        Try<String> sTry = Try.tryCall(() -> "Hello World");
        sTry.forEach(sRef::set);

        assertThat(sRef.get(), equalTo("Hello World"));

        sRef.set(null);
        sTry = Try.failed(new RuntimeException("Goodbye Cruel World"));
        sTry.forEach(sRef::set);
        assertThat(sRef.get(), nullValue());
    }

    @Test
    public void recover() throws Exception {

        Try<String> sTry = Try.failed(new RuntimeException("Goodbye Cruel World"));
        sTry = sTry.recover(t -> "Hello World");

        assertSuccess(sTry, "Hello World");

        sTry = Try.succeeded("Hello Again");
        sTry = sTry.recover(t -> "Hello World");

        assertSuccess(sTry, "Hello Again");
    }
}