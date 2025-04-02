package org.dataloader;

import org.dataloader.fixtures.TestKit;
import org.dataloader.fixtures.parameterized.DelegatingDataLoaderFactory;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
    void canCreateAClassOk() {
        DataLoader<String, String> rawLoader = TestKit.idLoader();
        DelegatingDataLoader<String, String> delegatingDataLoader = new DelegatingDataLoader<>(rawLoader) {
            @Override
            public CompletableFuture<String> load(@NonNull String key, @Nullable Object keyContext) {
                CompletableFuture<String> cf = super.load(key, keyContext);
                return cf.thenApply(v -> "|" + v + "|");
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
}