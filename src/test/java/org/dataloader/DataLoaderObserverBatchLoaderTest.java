package org.dataloader;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Arrays.asList;
import static org.awaitility.Awaitility.await;
import static org.dataloader.DataLoaderFactory.mkDataLoader;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class DataLoaderObserverBatchLoaderTest {

    @Test
    public void should_Build_a_really_really_simple_data_loader() {
        AtomicBoolean success = new AtomicBoolean();
        DataLoader<Integer, Integer> identityLoader = mkDataLoader(keysAsValues(), DataLoaderOptions.newOptions());

        CompletionStage<Integer> future1 = identityLoader.load(1);

        future1.thenAccept(value -> {
            assertThat(value, equalTo(1));
            success.set(true);
        });
        identityLoader.dispatch();
        await().untilAtomic(success, is(true));
    }

    @Test
    public void should_Support_loading_multiple_keys_in_one_call() {
        AtomicBoolean success = new AtomicBoolean();
        DataLoader<Integer, Integer> identityLoader = mkDataLoader(keysAsValues(), DataLoaderOptions.newOptions());

        CompletionStage<List<Integer>> futureAll = identityLoader.loadMany(asList(1, 2));
        futureAll.thenAccept(promisedValues -> {
            assertThat(promisedValues.size(), is(2));
            success.set(true);
        });
        identityLoader.dispatch();
        await().untilAtomic(success, is(true));
        assertThat(futureAll.toCompletableFuture().join(), equalTo(asList(1, 2)));
    }

    @Test
    public void simple_dataloader() {
        DataLoader<String, String> loader = mkDataLoader(keysAsValues(), DataLoaderOptions.newOptions());

        loader.load("A");
        loader.load("B");
        loader.loadMany(asList("C", "D"));

        List<String> results = loader.dispatchAndJoin();

        assertThat(results.size(), equalTo(4));
        assertThat(results, equalTo(asList("A", "B", "C", "D")));
    }

    @Test
    public void should_observer_batch_multiple_requests() throws ExecutionException, InterruptedException {
        DataLoader<Integer, Integer> identityLoader = mkDataLoader(keysAsValues(), new DataLoaderOptions());

        CompletableFuture<Integer> future1 = identityLoader.load(1);
        CompletableFuture<Integer> future2 = identityLoader.load(2);
        identityLoader.dispatch();

        await().until(() -> future1.isDone() && future2.isDone());
        assertThat(future1.get(), equalTo(1));
        assertThat(future2.get(), equalTo(2));
    }

    // A simple wrapper class intended as a proof external libraries can leverage this.
    private static class Publisher<V> {
        private final BatchObserver<V> delegate;
        private Publisher(BatchObserver<V> delegate) { this.delegate = delegate; }
        void onNext(V value) { delegate.onNext(value); }
        void onCompleted() { delegate.onCompleted(); }
        void onError(Throwable e) { delegate.onError(e); }
        // Mock 'subscribe' methods to simulate what would happen in the real thing.
        void subscribe(List<V> values) {
            values.forEach(this::onNext);
            this.onCompleted();
        }
        void subscribe(List<V> values, Throwable e) {
            values.forEach(this::onNext);
            this.onError(e);
        }
    }

    private static <K> ObserverBatchLoader<K, K> keysAsValues() {
        return (keys, observer) -> {
            Publisher<K> publisher = new Publisher<>(observer);
            publisher.subscribe(keys);
        };
    }

    private static <K, V> ObserverBatchLoader<K, V> keysWithValuesAndException(List<V> values, Throwable e) {
        return (keys, observer) -> {
            Publisher<V> publisher = new Publisher<>(observer);
            publisher.subscribe(values, e);
        };
    }
}
