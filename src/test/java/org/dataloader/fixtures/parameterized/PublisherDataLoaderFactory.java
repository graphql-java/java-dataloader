package org.dataloader.fixtures.parameterized;

import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.Try;
import org.dataloader.fixtures.TestKit;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.dataloader.DataLoaderFactory.newPublisherDataLoader;
import static org.dataloader.DataLoaderFactory.newPublisherDataLoaderWithTry;

public class PublisherDataLoaderFactory implements TestDataLoaderFactory, TestReactiveDataLoaderFactory {

    @Override
    public <K> DataLoader<K, K> idLoader(
            DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return newPublisherDataLoader((keys, subscriber) -> {
            loadCalls.add(new ArrayList<>(keys));
            Flux.fromIterable(keys).subscribe(subscriber);
        }, options);
    }

    @Override
    public <K> DataLoader<K, K> idLoaderDelayed(DataLoaderOptions options, List<Collection<K>> loadCalls, Duration delay) {
        return newPublisherDataLoader((keys, subscriber) -> {
            CompletableFuture.runAsync(() -> {
                TestKit.snooze(delay.toMillis());
                loadCalls.add(new ArrayList<>(keys));
                Flux.fromIterable(keys).subscribe(subscriber);
            });
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

    @Override
    public <K> DataLoader<K, K> idLoaderBlowsUpsAfterN(int N, DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return newPublisherDataLoader((keys, subscriber) -> {
            loadCalls.add(new ArrayList<>(keys));

            List<K> nKeys = keys.subList(0, N);
            Flux<K> subFlux = Flux.fromIterable(nKeys);
            subFlux.concatWith(Flux.error(new IllegalStateException("Error")))
                    .subscribe(subscriber);
        }, options);
    }

    @Override
    public DataLoader<String, String> onlyReturnsNValues(int N, DataLoaderOptions options, ArrayList<Object> loadCalls) {
        return newPublisherDataLoader((keys, subscriber) -> {
            loadCalls.add(new ArrayList<>(keys));

            List<String> nKeys = keys.subList(0, N);
            Flux.fromIterable(nKeys)
                    .subscribe(subscriber);
        }, options);
    }

    @Override
    public DataLoader<String, String> idLoaderReturnsTooMany(int howManyMore, DataLoaderOptions options, ArrayList<Object> loadCalls) {
        return newPublisherDataLoader((keys, subscriber) -> {
            loadCalls.add(new ArrayList<>(keys));

            List<String> l = new ArrayList<>(keys);
            for (int i = 0; i < howManyMore; i++) {
                l.add("extra-" + i);
            }

            Flux.fromIterable(l)
                    .subscribe(subscriber);
        }, options);
    }
}
