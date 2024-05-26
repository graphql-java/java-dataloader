package org.dataloader.fixtures.parameterized;

import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.Try;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.dataloader.DataLoaderFactory.newMappedPublisherDataLoader;
import static org.dataloader.DataLoaderFactory.newMappedPublisherDataLoaderWithTry;

public class MappedPublisherDataLoaderFactory implements TestDataLoaderFactory, TestReactiveDataLoaderFactory {

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

    @Override
    public <K> DataLoader<K, K> idLoaderBlowsUpsAfterN(int N, DataLoaderOptions options, List<Collection<K>> loadCalls) {
        return newMappedPublisherDataLoader((keys, subscriber) -> {
            loadCalls.add(new ArrayList<>(keys));

            List<K> nKeys = keys.stream().limit(N).collect(toList());
            Flux<Map.Entry<K, K>> subFlux = Flux.fromIterable(nKeys).map(k -> Map.entry(k, k));
            subFlux.concatWith(Flux.error(new IllegalStateException("Error")))
                    .subscribe(subscriber);
        }, options);
    }

    @Override
    public DataLoader<String, String> onlyReturnsNValues(int N, DataLoaderOptions options, ArrayList<Object> loadCalls) {
        return newMappedPublisherDataLoader((keys, subscriber) -> {
            loadCalls.add(new ArrayList<>(keys));

            List<String> nKeys = keys.stream().limit(N).collect(toList());
            Flux.fromIterable(nKeys).map(k -> Map.entry(k, k))
                    .subscribe(subscriber);
        }, options);
    }

    @Override
    public DataLoader<String, String> idLoaderReturnsTooMany(int howManyMore, DataLoaderOptions options, ArrayList<Object> loadCalls) {
        return newMappedPublisherDataLoader((keys, subscriber) -> {
            loadCalls.add(new ArrayList<>(keys));

            List<String> l = new ArrayList<>(keys);
            for (int i = 0; i < howManyMore; i++) {
                l.add("extra-" + i);
            }

            Flux.fromIterable(l).map(k -> Map.entry(k, k))
                    .subscribe(subscriber);
        }, options);
    }
}
