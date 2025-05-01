package org.dataloader;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.dataloader.DataLoaderFactory.newMappedDataLoader;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Tests related to context.  DataLoaderTest is getting to big and needs refactoring
 */
public class DataLoaderBatchLoaderEnvironmentTest {

    private BatchLoaderWithContext<String, String> contextBatchLoader() {
        return (keys, environment) -> {
            AtomicInteger index = new AtomicInteger(0);
            List<String> list = keys.stream().map(k -> {
                int i = index.getAndIncrement();
                Object context = environment.getContext();
                Object keyContextM = environment.getKeyContexts().get(k);
                Object keyContextL = environment.getKeyContextsList().get(i);
                return k + "-" + context + "-m:" + keyContextM + "-l:" + keyContextL;
            }).collect(Collectors.toList());
            return CompletableFuture.completedFuture(list);
        };
    }


    @Test
    public void context_is_passed_to_batch_loader_function() {
        BatchLoaderWithContext<String, String> batchLoader = (keys, environment) -> {
            List<String> list = keys.stream().map(k -> k + "-" + environment.getContext()).collect(Collectors.toList());
            return CompletableFuture.completedFuture(list);
        };
        DataLoaderOptions options = DataLoaderOptions.newOptions()
                .withBatchLoaderContextProvider(() -> "ctx");
        DataLoader<String, String> loader = newDataLoader(batchLoader, options);

        loader.load("A");
        loader.load("B");
        loader.loadMany(asList("C", "D"));
        Map<String, ?> keysAndContexts = new LinkedHashMap<>();
        keysAndContexts.put("E", null);
        keysAndContexts.put("F", null);
        loader.loadMany(keysAndContexts);

        List<String> results = loader.dispatchAndJoin();

        assertThat(results, equalTo(asList("A-ctx", "B-ctx", "C-ctx", "D-ctx", "E-ctx", "F-ctx")));
    }

    @Test
    public void key_contexts_are_passed_to_batch_loader_function() {
        BatchLoaderWithContext<String, String> batchLoader = contextBatchLoader();
        DataLoaderOptions options = DataLoaderOptions.newOptions()
                .withBatchLoaderContextProvider(() -> "ctx");
        DataLoader<String, String> loader = newDataLoader(batchLoader, options);

        loader.load("A", "aCtx");
        loader.load("B", "bCtx");
        loader.loadMany(asList("C", "D"), asList("cCtx", "dCtx"));
        Map<String, String> keysAndContexts = new LinkedHashMap<>();
        keysAndContexts.put("E", "eCtx");
        keysAndContexts.put("F", "fCtx");
        loader.loadMany(keysAndContexts);

        List<String> results = loader.dispatchAndJoin();

        assertThat(results, equalTo(asList("A-ctx-m:aCtx-l:aCtx", "B-ctx-m:bCtx-l:bCtx", "C-ctx-m:cCtx-l:cCtx", "D-ctx-m:dCtx-l:dCtx", "E-ctx-m:eCtx-l:eCtx", "F-ctx-m:fCtx-l:fCtx")));
    }

    @Test
    public void key_contexts_are_passed_to_batch_loader_function_when_batching_disabled() {
        BatchLoaderWithContext<String, String> batchLoader = contextBatchLoader();
        DataLoaderOptions options = DataLoaderOptions.newOptions()
                .withBatchingEnabled(false)
                .withBatchLoaderContextProvider(() -> "ctx");
        DataLoader<String, String> loader = newDataLoader(batchLoader, options);

        CompletableFuture<String> aLoad = loader.load("A", "aCtx");
        CompletableFuture<String> bLoad = loader.load("B", "bCtx");
        CompletableFuture<List<String>> cAndDLoad = loader.loadMany(asList("C", "D"), asList("cCtx", "dCtx"));
        Map<String, String> keysAndContexts = new LinkedHashMap<>();
        keysAndContexts.put("E", "eCtx");
        keysAndContexts.put("F", "fCtx");
        CompletableFuture<Map<String, String>> eAndFLoad = loader.loadMany(keysAndContexts);

        List<String> results = new ArrayList<>(asList(aLoad.join(), bLoad.join()));
        results.addAll(cAndDLoad.join());
        results.addAll(eAndFLoad.join().values());

        assertThat(results, equalTo(asList("A-ctx-m:aCtx-l:aCtx", "B-ctx-m:bCtx-l:bCtx", "C-ctx-m:cCtx-l:cCtx", "D-ctx-m:dCtx-l:dCtx", "E-ctx-m:eCtx-l:eCtx", "F-ctx-m:fCtx-l:fCtx")));
    }

    @Test
    public void missing_key_contexts_are_passed_to_batch_loader_function() {
        BatchLoaderWithContext<String, String> batchLoader = contextBatchLoader();
        DataLoaderOptions options = DataLoaderOptions.newOptions()
                .withBatchLoaderContextProvider(() -> "ctx");
        DataLoader<String, String> loader = newDataLoader(batchLoader, options);

        loader.load("A", "aCtx");
        loader.load("B");
        loader.loadMany(asList("C", "D"), singletonList("cCtx"));

        Map<String, String> keysAndContexts = new LinkedHashMap<>();
        keysAndContexts.put("E", "eCtx");
        keysAndContexts.put("F", null);
        loader.loadMany(keysAndContexts);

        List<String> results = loader.dispatchAndJoin();

        assertThat(results, equalTo(asList("A-ctx-m:aCtx-l:aCtx", "B-ctx-m:null-l:null", "C-ctx-m:cCtx-l:cCtx", "D-ctx-m:null-l:null", "E-ctx-m:eCtx-l:eCtx", "F-ctx-m:null-l:null")));
    }

    @Test
    public void context_is_passed_to_map_batch_loader_function() {
        MappedBatchLoaderWithContext<String, String> mapBatchLoader = (keys, environment) -> {
            Map<String, String> map = new HashMap<>();
            keys.forEach(k -> {
                Object context = environment.getContext();
                Object keyContext = environment.getKeyContexts().get(k);
                map.put(k, k + "-" + context + "-" + keyContext);
            });
            return CompletableFuture.completedFuture(map);
        };
        DataLoaderOptions options = DataLoaderOptions.newOptions()
                .withBatchLoaderContextProvider(() -> "ctx");
        DataLoader<String, String> loader = newMappedDataLoader(mapBatchLoader, options);

        loader.load("A", "aCtx");
        loader.load("B");
        loader.loadMany(asList("C", "D"), singletonList("cCtx"));

        Map<String, String> keysAndContexts = new LinkedHashMap<>();
        keysAndContexts.put("E", "eCtx");
        keysAndContexts.put("F", null);
        loader.loadMany(keysAndContexts);

        List<String> results = loader.dispatchAndJoin();

        assertThat(results, equalTo(asList("A-ctx-aCtx", "B-ctx-null", "C-ctx-cCtx", "D-ctx-null", "E-ctx-eCtx", "F-ctx-null")));
    }

    @Test
    public void null_is_passed_as_context_if_you_do_nothing() {
        BatchLoaderWithContext<String, String> batchLoader = (keys, environment) -> {
            List<String> list = keys.stream().map(k -> k + "-" + environment.getContext()).collect(Collectors.toList());
            return CompletableFuture.completedFuture(list);
        };
        DataLoader<String, String> loader = newDataLoader(batchLoader);

        loader.load("A");
        loader.load("B");
        loader.loadMany(asList("C", "D"));

        Map<String, String> keysAndContexts = new LinkedHashMap<>();
        keysAndContexts.put("E", null);
        keysAndContexts.put("F", null);
        loader.loadMany(keysAndContexts);

        List<String> results = loader.dispatchAndJoin();

        assertThat(results, equalTo(asList("A-null", "B-null", "C-null", "D-null", "E-null", "F-null")));
    }

    @Test
    public void null_is_passed_as_context_to_map_loader_if_you_do_nothing() {
        MappedBatchLoaderWithContext<String, String> mapBatchLoader = (keys, environment) -> {
            Map<String, String> map = new HashMap<>();
            keys.forEach(k -> map.put(k, k + "-" + environment.getContext()));
            return CompletableFuture.completedFuture(map);
        };
        DataLoader<String, String> loader = newMappedDataLoader(mapBatchLoader);

        loader.load("A");
        loader.load("B");
        loader.loadMany(asList("C", "D"));

        Map<String, String> keysAndContexts = new LinkedHashMap<>();
        keysAndContexts.put("E", null);
        keysAndContexts.put("F", null);
        loader.loadMany(keysAndContexts);

        List<String> results = loader.dispatchAndJoin();

        assertThat(results, equalTo(asList("A-null", "B-null", "C-null", "D-null", "E-null", "F-null")));
    }

    @Test
    public void mmap_semantics_apply_to_batch_loader_context() {
        BatchLoaderWithContext<String, String> batchLoader = contextBatchLoader();
        DataLoaderOptions options = DataLoaderOptions.newOptions()
                .withBatchLoaderContextProvider(() -> "ctx")
                .withCachingEnabled(false);
        DataLoader<String, String> loader = newDataLoader(batchLoader, options);

        loader.load("A", "aCtx");
        loader.load("B", "bCtx");
        loader.load("A", "overridesCtx");

        List<String> results = loader.dispatchAndJoin();

        assertThat(results, equalTo(asList("A-ctx-m:overridesCtx-l:aCtx", "B-ctx-m:bCtx-l:bCtx", "A-ctx-m:overridesCtx-l:overridesCtx")));
    }

}
