package org.dataloader;

import org.dataloader.registries.DispatchPredicate;
import org.dataloader.stats.SimpleStatisticsCollector;
import org.dataloader.stats.Statistics;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.asList;
import static org.dataloader.DataLoaderFactory.newDataLoader;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * Tests related to dispatching predicates.
 */
public class DataLoaderPredicateTest {

    @Test
    public void the_predicate_will_prevent_loading() {
        BatchLoader<String, String> batchLoader = CompletableFuture::completedFuture;
        DataLoader<String, String> loader = newDataLoader(batchLoader,
                DataLoaderOptions.newOptions().setStatisticsCollector(SimpleStatisticsCollector::new)
                        .dispatchPredicate(DispatchPredicate.dispatchNever())
        );

        loader.load("A");
        loader.load("B");
        loader.loadMany(asList("C", "D"));

        Statistics stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(4L));
        assertThat(stats.getBatchInvokeCount(), equalTo(0L));
        assertThat(stats.getBatchLoadCount(), equalTo(0L));
        assertThat(stats.getCacheHitCount(), equalTo(0L));

        DispatchResult<String> dispatchResult = loader.dispatchWithCounts();
        assertThat(dispatchResult.wasDispatched(), equalTo(false));
        assertThat(dispatchResult.getKeysCount(), equalTo(4));

        stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(4L));
        assertThat(stats.getBatchInvokeCount(), equalTo(0L));
        assertThat(stats.getBatchLoadCount(), equalTo(0L));


        loader.load("A");
        loader.load("B");

        dispatchResult = loader.dispatchWithCounts();
        assertThat(dispatchResult.wasDispatched(), equalTo(false));
        assertThat(dispatchResult.getKeysCount(), equalTo(4));

        stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(6L));
        assertThat(stats.getBatchInvokeCount(), equalTo(0L));
        assertThat(stats.getBatchLoadCount(), equalTo(0L));
    }

    @Test
    public void the_predicate_will_allow_loading_by_default() {
        BatchLoader<String, String> batchLoader = CompletableFuture::completedFuture;
        DataLoader<String, String> loader = newDataLoader(batchLoader,
                DataLoaderOptions.newOptions().setStatisticsCollector(SimpleStatisticsCollector::new)
                        .dispatchPredicate(DispatchPredicate.dispatchAlways())
        );

        loader.load("A");
        loader.load("B");
        loader.loadMany(asList("C", "D"));


        DispatchResult<String> dispatchResult = loader.dispatchWithCounts();
        assertThat(dispatchResult.wasDispatched(), equalTo(true));
        assertThat(dispatchResult.getKeysCount(), equalTo(4));

        Statistics stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(4L));
        assertThat(stats.getBatchInvokeCount(), equalTo(1L));
        assertThat(stats.getBatchLoadCount(), equalTo(4L));


        loader.load("E");
        loader.load("F");

        dispatchResult = loader.dispatchWithCounts();
        assertThat(dispatchResult.wasDispatched(), equalTo(true));
        assertThat(dispatchResult.getKeysCount(), equalTo(2));

        stats = loader.getStatistics();
        assertThat(stats.getLoadCount(), equalTo(6L));
        assertThat(stats.getBatchInvokeCount(), equalTo(2L));
        assertThat(stats.getBatchLoadCount(), equalTo(6L));
    }

    @Test
    public void dataloader_options_have_a_default_which_is_always_on() {
        BatchLoader<String, String> batchLoader = CompletableFuture::completedFuture;
        DataLoaderOptions dataLoaderOptions = DataLoaderOptions.newOptions();

        DispatchPredicate defaultPredicate = dataLoaderOptions.getDispatchPredicate();
        assertThat(defaultPredicate, notNullValue());
        assertThat(defaultPredicate.test(null, null), equalTo(true));


        DataLoader<String, String> loader = newDataLoader(batchLoader, dataLoaderOptions);

        loader.load("A");
        loader.load("B");
        loader.loadMany(asList("C", "D"));

        DispatchResult<String> dispatchResult = loader.dispatchWithCounts();
        assertThat(dispatchResult.wasDispatched(), equalTo(true));
        assertThat(dispatchResult.getKeysCount(), equalTo(4));

    }
}
