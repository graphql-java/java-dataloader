package org.dataloader;

import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

/**
 * Tests for IfPresent and IfCompleted functionality.
 */
public class DataLoaderIfPresentTest {

    private <T> BatchLoader<T, T> keysAsValues() {
        return CompletableFuture::completedFuture;
    }

    @Test
    public void should_detect_if_present_cf() {
        DataLoader<Integer, Integer> dataLoader = newDataLoader(keysAsValues());

        Optional<CompletableFuture<Integer>> cachedPromise = dataLoader.getIfPresent(1);
        assertThat(cachedPromise.isPresent(), equalTo(false));

        CompletionStage<Integer> future1 = dataLoader.load(1);

        cachedPromise = dataLoader.getIfPresent(1);
        assertThat(cachedPromise.isPresent(), equalTo(true));

        assertThat(cachedPromise.get(), sameInstance(future1));

        // but its not done!
        assertThat(cachedPromise.get().isDone(), equalTo(false));
        //
        // and hence it cant be loaded as complete
        cachedPromise = dataLoader.getIfCompleted(1);
        assertThat(cachedPromise.isPresent(), equalTo(false));
    }

    @Test
    public void should_not_be_present_if_cleared() {
        DataLoader<Integer, Integer> dataLoader = newDataLoader(keysAsValues());

        dataLoader.load(1);

        Optional<CompletableFuture<Integer>> cachedPromise = dataLoader.getIfPresent(1);
        assertThat(cachedPromise.isPresent(), equalTo(true));

        dataLoader.clear(1);

        cachedPromise = dataLoader.getIfPresent(1);
        assertThat(cachedPromise.isPresent(), equalTo(false));

        // and hence is not completed as well
        cachedPromise = dataLoader.getIfCompleted(1);
        assertThat(cachedPromise.isPresent(), equalTo(false));
    }

    @Test
    public void should_allow_completed_cfs_to_be_found() {
        DataLoader<Integer, Integer> dataLoader = newDataLoader(keysAsValues());

        dataLoader.load(1);

        Optional<CompletableFuture<Integer>> cachedPromise = dataLoader.getIfPresent(1);
        assertThat(cachedPromise.isPresent(), equalTo(true));

        cachedPromise = dataLoader.getIfCompleted(1);
        assertThat(cachedPromise.isPresent(), equalTo(false));

        dataLoader.dispatch();

        cachedPromise = dataLoader.getIfPresent(1);
        assertThat(cachedPromise.isPresent(), equalTo(true));

        cachedPromise = dataLoader.getIfCompleted(1);
        assertThat(cachedPromise.isPresent(), equalTo(true));
        assertThat(cachedPromise.get().isDone(), equalTo(true));
    }

    @Test
    public void should_work_with_primed_caches() {
        DataLoader<Integer, Integer> dataLoader = newDataLoader(keysAsValues());
        dataLoader.prime(1, 666).prime(2, 999);

        Optional<CompletableFuture<Integer>> cachedPromise = dataLoader.getIfPresent(1);
        assertThat(cachedPromise.isPresent(), equalTo(true));

        cachedPromise = dataLoader.getIfCompleted(1);
        assertThat(cachedPromise.isPresent(), equalTo(true));
        assertThat(cachedPromise.get().isDone(), equalTo(true));

        // and its value is what we expect
        assertThat(cachedPromise.get().join(), equalTo(666));
    }

}
