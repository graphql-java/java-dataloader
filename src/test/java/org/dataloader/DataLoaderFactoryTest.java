package org.dataloader;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DataLoaderFactoryTest {

    @Test
    void can_create_via_builder() {
        BatchLoaderWithContext<String, String> loader = (keys, environment) -> CompletableFuture.completedFuture(keys);
        DataLoaderOptions options = DataLoaderOptions.newOptions().setBatchingEnabled(true).build();

        DataLoader<String, String> dl = DataLoaderFactory.<String, String>builder()
                .name("x").batchLoader(loader).options(options).build();

        assertNotNull(dl.getName());
        assertThat(dl.getName(), equalTo("x"));
        assertThat(dl.getBatchLoadFunction(), equalTo(loader));
        assertThat(dl.getOptions(), equalTo(options));

        BatchLoaderWithContext<String, Try<String>> loaderTry = (keys, environment)
                -> CompletableFuture.completedFuture(keys.stream().map(Try::succeeded).collect(Collectors.toList()));

        DataLoader<String, Try<String>> dlTry = DataLoaderFactory.<String, Try<String>>builder()
                .name("try").batchLoader(loaderTry).options(options).build();

        assertNotNull(dlTry.getName());
        assertThat(dlTry.getName(), equalTo("try"));
        assertThat(dlTry.getBatchLoadFunction(), equalTo(loaderTry));
        assertThat(dlTry.getOptions(), equalTo(options));

        MappedBatchLoader<String, Try<String>> mappedLoaderTry = (keys)
                -> CompletableFuture.completedFuture(
                keys.stream().collect(Collectors.toMap(k -> k, Try::succeeded))
        );

        DataLoader<String, Try<String>> dlTry2 = DataLoaderFactory.<String, Try<String>>builder()
                .name("try2").mappedBatchLoader(mappedLoaderTry).options(options).build();

        assertNotNull(dlTry2.getName());
        assertThat(dlTry2.getName(), equalTo("try2"));
        assertThat(dlTry2.getBatchLoadFunction(), equalTo(mappedLoaderTry));
        assertThat(dlTry2.getOptions(), equalTo(options));
    }
}