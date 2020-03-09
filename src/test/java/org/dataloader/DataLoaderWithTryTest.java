package org.dataloader;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.dataloader.DataLoaderFactory.newMappedDataLoaderWithTry;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class DataLoaderWithTryTest {

    @Test
    public void should_handle_Trys_coming_back_from_batchLoader() throws Exception {

        List<List<String>> batchKeyCalls = new ArrayList<>();
        BatchLoader<String, Try<String>> batchLoader = keys -> {
            batchKeyCalls.add(keys);

            List<Try<String>> result = new ArrayList<>();
            for (String key : keys) {
                if ("bang".equalsIgnoreCase(key)) {
                    result.add(Try.failed(new RuntimeException(key)));
                } else {
                    result.add(Try.succeeded(key));
                }
            }
            return CompletableFuture.completedFuture(result);
        };

        DataLoader<String, String> dataLoader = DataLoaderFactory.newDataLoaderWithTry(batchLoader);

        commonTryAsserts(batchKeyCalls, dataLoader);
    }

    @Test
    public void should_handle_Trys_coming_back_from_mapped_batchLoader() throws Exception {

        List<List<String>> batchKeyCalls = new ArrayList<>();
        MappedBatchLoaderWithContext<String, Try<String>> batchLoader = (keys, environment) -> {
            batchKeyCalls.add(new ArrayList<>(keys));

            Map<String, Try<String>> result = new HashMap<>();
            for (String key : keys) {
                if ("bang".equalsIgnoreCase(key)) {
                    result.put(key, Try.failed(new RuntimeException(key)));
                } else {
                    result.put(key, Try.succeeded(key));
                }
            }
            return CompletableFuture.completedFuture(result);
        };

        DataLoader<String, String> dataLoader = newMappedDataLoaderWithTry(batchLoader);

        commonTryAsserts(batchKeyCalls, dataLoader);
    }

    private void commonTryAsserts(List<List<String>> batchKeyCalls, DataLoader<String, String> dataLoader) throws InterruptedException, java.util.concurrent.ExecutionException {
        CompletableFuture<String> a = dataLoader.load("A");
        CompletableFuture<String> b = dataLoader.load("B");
        CompletableFuture<String> bang = dataLoader.load("bang");

        dataLoader.dispatch();

        assertThat(a.get(), equalTo("A"));
        assertThat(b.get(), equalTo("B"));
        assertThat(bang.isCompletedExceptionally(), equalTo(true));
        bang.whenComplete((s, throwable) -> {
            assertThat(s, nullValue());
            assertThat(throwable, Matchers.instanceOf(RuntimeException.class));
            assertThat(throwable.getMessage(), equalTo("Bang"));
        });

        assertThat(batchKeyCalls, equalTo(singletonList(asList("A", "B", "bang"))));

        a = dataLoader.load("A");
        b = dataLoader.load("B");
        bang = dataLoader.load("bang");

        dataLoader.dispatch();

        assertThat(a.get(), equalTo("A"));
        assertThat(b.get(), equalTo("B"));
        assertThat(bang.isCompletedExceptionally(), equalTo(true));
        bang.whenComplete((s, throwable) -> {
            assertThat(s, nullValue());
            assertThat(throwable, Matchers.instanceOf(RuntimeException.class));
            assertThat(throwable.getMessage(), equalTo("Bang"));
        });

        // the failed value should have been cached as per Facebook DL behaviour
        assertThat(batchKeyCalls, equalTo(singletonList(asList("A", "B", "bang"))));
    }

}
