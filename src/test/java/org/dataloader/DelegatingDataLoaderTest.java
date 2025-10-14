package org.dataloader;

import org.dataloader.fixtures.TestKit;
import org.dataloader.fixtures.parameterized.DelegatingDataLoaderFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * There are WAY more tests via the {@link DelegatingDataLoaderFactory}
 * parameterized tests.  All the basic {@link DataLoader} tests pass when wrapped in a {@link DelegatingDataLoader}
 */
public class DelegatingDataLoaderTest {

    @Test
    void canUnwrapDataLoaders() {
        DataLoader<Object, Object> rawLoader = TestKit.idLoader();
        DataLoader<Object, Object> delegateLoader = new DelegatingDataLoader<>(rawLoader);

        assertThat(DelegatingDataLoader.unwrap(rawLoader), is(rawLoader));
        assertThat(DelegatingDataLoader.unwrap(delegateLoader), is(rawLoader));
    }

    @Test
    @NullMarked
    void canCreateAClassOk() {
        DataLoader<String, String> rawLoader = TestKit.idLoader();
        DelegatingDataLoader<String, String> delegatingDataLoader = new DelegatingDataLoader<>(rawLoader) {
            private CompletableFuture<String> enhance(CompletableFuture<String> cf) {
                return cf.thenApply(v -> "|" + v + "|");
            }

            private CompletableFuture<List<String>> enhanceList(CompletableFuture<List<String>> cf) {
                return cf.thenApply(v -> v.stream().map(s -> "|" + s + "|").collect(Collectors.toList()));
            }

            @Override
            public CompletableFuture<String> load(String key, @Nullable Object keyContext) {
                return enhance(super.load(key, keyContext));
            }

            @Override
            public CompletableFuture<String> load(String key) {
                return enhance(super.load(key));
            }

            @Override
            public CompletableFuture<List<String>> loadMany(List<String> keys) {
                return enhanceList(super.loadMany(keys));
            }

            @Override
            public CompletableFuture<List<String>> loadMany(List<String> keys, List<Object> keyContexts) {
                return enhanceList(super.loadMany(keys, keyContexts));
            }
        };

        assertThat(delegatingDataLoader.getDelegate(), is(rawLoader));


        CompletableFuture<String> cfA = delegatingDataLoader.load("A");
        CompletableFuture<String> cfB = delegatingDataLoader.load("B");
        CompletableFuture<List<String>> cfCD = delegatingDataLoader.loadMany(List.of("C", "D"));

        CompletableFuture<List<String>> dispatch = delegatingDataLoader.dispatch();

        await().until(dispatch::isDone);

        assertThat(cfA.join(), equalTo("|A|"));
        assertThat(cfB.join(), equalTo("|B|"));
        assertThat(cfCD.join(), equalTo(List.of("|C|", "|D|")));

        assertThat(delegatingDataLoader.getIfPresent("A").isEmpty(), equalTo(false));
        assertThat(delegatingDataLoader.getIfPresent("X").isEmpty(), equalTo(true));

        assertThat(delegatingDataLoader.getIfCompleted("A").isEmpty(), equalTo(false));
        assertThat(delegatingDataLoader.getIfCompleted("X").isEmpty(), equalTo(true));
    }

    @Test
    void can_delegate_simple_properties() {
        DataLoaderOptions options = DataLoaderOptions.newOptions().build();
        BatchLoader<String, String> loadFunction = CompletableFuture::completedFuture;

        DataLoader<String, String> rawLoader = DataLoaderFactory.newDataLoader("name", loadFunction, options);
        DelegatingDataLoader<String, String> delegate = new DelegatingDataLoader<>(rawLoader);

        assertNotNull(delegate.getName());
        assertThat(delegate.getName(), equalTo("name"));
        assertThat(delegate.getOptions(), equalTo(options));
        assertThat(delegate.getBatchLoadFunction(), equalTo(loadFunction));
    }

    @NullMarked
    @Test
    void can_create_a_delegate_class_that_has_post_side_effects() {
        DataLoaderOptions options = DataLoaderOptions.newOptions().build();
        BatchLoader<String, String> loadFunction = CompletableFuture::completedFuture;
        DataLoader<String, String> rawLoader = DataLoaderFactory.newDataLoader("name", loadFunction, options);

        AtomicInteger loadCalled = new AtomicInteger(0);
        AtomicInteger loadManyCalled = new AtomicInteger(0);
        AtomicInteger loadManyMapCalled = new AtomicInteger(0);
        DelegatingDataLoader<String, String> delegate = new DelegatingDataLoader<>(rawLoader) {

            @Override
            public CompletableFuture<String> load(String key) {
                CompletableFuture<String> cf = super.load(key);
                loadCalled.incrementAndGet();
                return cf;
            }

            @Override
            public CompletableFuture<String> load(String key, @Nullable Object keyContext) {
                CompletableFuture<String> cf = super.load(key, keyContext);
                loadCalled.incrementAndGet();
                return cf;
            }

            @Override
            public CompletableFuture<List<String>> loadMany(List<String> keys, List<Object> keyContexts) {
                CompletableFuture<List<String>> cf = super.loadMany(keys, keyContexts);
                loadManyCalled.incrementAndGet();
                return cf;
            }

            @Override
            public CompletableFuture<List<String>> loadMany(List<String> keys) {
                CompletableFuture<List<String>> cf = super.loadMany(keys);
                loadManyCalled.incrementAndGet();
                return cf;
            }

            @Override
            public CompletableFuture<Map<String, String>> loadMany(Map<String, ?> keysAndContexts) {
                CompletableFuture<Map<String, String>> cf = super.loadMany(keysAndContexts);
                loadManyMapCalled.incrementAndGet();
                return cf;
            }
        };


        delegate.load("L1");
        delegate.load("L2", null);
        delegate.loadMany(List.of("LM1", "LM2"), List.of());
        delegate.loadMany(List.of("LM3"));
        delegate.loadMany(Map.of("LMM1", "kc1", "LMM2", "kc2"));

        assertNotNull(delegate.getDelegate());
        assertThat(loadCalled.get(), equalTo(2));
        assertThat(loadManyCalled.get(), equalTo(2));
        assertThat(loadManyMapCalled.get(), equalTo(1));
    }
}