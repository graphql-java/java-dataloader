package org.dataloader;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class DataLoaderOptionsTest {
    @Test
    public void should_create_a_default_data_loader_options() {
        DataLoaderOptions options = new DataLoaderOptions(createDefaultDataLoaderOptions());
        assertThat(options.batchingEnabled(), equalTo(true));
        assertThat(options.cachingEnabled(), equalTo(true));
        assertThat(options.cachingExceptionsEnabled(), equalTo(true));
        assertThat(options.maxBatchSize(), equalTo(-1));
        assertThat(options.minBatchSize(), equalTo(0));
        assertThat(options.maxWaitInMillis(), equalTo(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void should_fail_if_min_batch_size_is_greater_than_max() {
        DataLoaderOptions options = createDefaultDataLoaderOptions();
        options.setMaxBatchSize(5).setMinBatchSize(6);
    }

    @Test(expected = IllegalArgumentException.class)
    public void should_fail_if_max_batch_size_is_less_than_min() {
        DataLoaderOptions options = createDefaultDataLoaderOptions();
        options.setMinBatchSize(6).setMaxBatchSize(5);
    }

    private DataLoaderOptions createDefaultDataLoaderOptions() {
        return DataLoaderOptions.newOptions();
    }
}
