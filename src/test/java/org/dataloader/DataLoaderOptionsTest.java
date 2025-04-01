package org.dataloader;

import org.dataloader.impl.DefaultCacheMap;
import org.dataloader.impl.NoOpValueCache;
import org.dataloader.instrumentation.DataLoaderInstrumentation;
import org.dataloader.scheduler.BatchLoaderScheduler;
import org.dataloader.stats.NoOpStatisticsCollector;
import org.dataloader.stats.StatisticsCollector;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

@SuppressWarnings("OptionalGetWithoutIsPresent")
class DataLoaderOptionsTest {

    DataLoaderOptions optionsDefault = new DataLoaderOptions();

    @Test
    void canCreateDefaultOptions() {

        assertThat(optionsDefault.batchingEnabled(), equalTo(true));
        assertThat(optionsDefault.cachingEnabled(), equalTo(true));
        assertThat(optionsDefault.cachingExceptionsEnabled(), equalTo(true));
        assertThat(optionsDefault.maxBatchSize(), equalTo(-1));
        assertThat(optionsDefault.getBatchLoaderScheduler(), equalTo(null));

        DataLoaderOptions builtOptions = DataLoaderOptions.newOptionsBuilder().build();
        assertThat(builtOptions, equalTo(optionsDefault));
        assertThat(builtOptions == optionsDefault, equalTo(false));

        DataLoaderOptions transformedOptions = optionsDefault.transform(builder -> {
        });
        assertThat(transformedOptions, equalTo(optionsDefault));
        assertThat(transformedOptions == optionsDefault, equalTo(false));
    }

    @Test
    void canCopyOk() {
        DataLoaderOptions optionsNext = new DataLoaderOptions(optionsDefault);
        assertThat(optionsNext, equalTo(optionsDefault));
        assertThat(optionsNext == optionsDefault, equalTo(false));

        optionsNext = DataLoaderOptions.newDataLoaderOptions(optionsDefault).build();
        assertThat(optionsNext, equalTo(optionsDefault));
        assertThat(optionsNext == optionsDefault, equalTo(false));
    }

    BatchLoaderScheduler testBatchLoaderScheduler = new BatchLoaderScheduler() {
        @Override
        public <K, V> CompletionStage<List<V>> scheduleBatchLoader(ScheduledBatchLoaderCall<V> scheduledCall, List<K> keys, BatchLoaderEnvironment environment) {
            return null;
        }

        @Override
        public <K, V> CompletionStage<Map<K, V>> scheduleMappedBatchLoader(ScheduledMappedBatchLoaderCall<K, V> scheduledCall, List<K> keys, BatchLoaderEnvironment environment) {
            return null;
        }

        @Override
        public <K> void scheduleBatchPublisher(ScheduledBatchPublisherCall scheduledCall, List<K> keys, BatchLoaderEnvironment environment) {

        }
    };

    BatchLoaderContextProvider testBatchLoaderContextProvider = () -> null;

    CacheMap<Object, Object> testCacheMap = new DefaultCacheMap<>();

    ValueCache<Object, Object> testValueCache = new NoOpValueCache<>();

    CacheKey<Object> testCacheKey = new CacheKey<Object>() {
        @Override
        public Object getKey(Object input) {
            return null;
        }
    };

    ValueCacheOptions testValueCacheOptions = ValueCacheOptions.newOptions();

    NoOpStatisticsCollector noOpStatisticsCollector = new NoOpStatisticsCollector();
    Supplier<StatisticsCollector> testStatisticsCollectorSupplier = () -> noOpStatisticsCollector;

    @Test
    void canBuildOk() {
        assertThat(optionsDefault.setBatchingEnabled(false).batchingEnabled(),
                equalTo(false));
        assertThat(optionsDefault.setBatchLoaderScheduler(testBatchLoaderScheduler).getBatchLoaderScheduler(),
                equalTo(testBatchLoaderScheduler));
        assertThat(optionsDefault.setBatchLoaderContextProvider(testBatchLoaderContextProvider).getBatchLoaderContextProvider(),
                equalTo(testBatchLoaderContextProvider));
        assertThat(optionsDefault.setCacheMap(testCacheMap).cacheMap().get(),
                equalTo(testCacheMap));
        assertThat(optionsDefault.setCachingEnabled(false).cachingEnabled(),
                equalTo(false));
        assertThat(optionsDefault.setValueCacheOptions(testValueCacheOptions).getValueCacheOptions(),
                equalTo(testValueCacheOptions));
        assertThat(optionsDefault.setCacheKeyFunction(testCacheKey).cacheKeyFunction().get(),
                equalTo(testCacheKey));
        assertThat(optionsDefault.setValueCache(testValueCache).valueCache().get(),
                equalTo(testValueCache));
        assertThat(optionsDefault.setMaxBatchSize(10).maxBatchSize(),
                equalTo(10));
        assertThat(optionsDefault.setStatisticsCollector(testStatisticsCollectorSupplier).getStatisticsCollector(),
                equalTo(testStatisticsCollectorSupplier.get()));

        DataLoaderOptions builtOptions = optionsDefault.transform(builder -> {
            builder.setBatchingEnabled(false);
            builder.setCachingExceptionsEnabled(false);
            builder.setCachingEnabled(false);
            builder.setBatchLoaderScheduler(testBatchLoaderScheduler);
            builder.setBatchLoaderContextProvider(testBatchLoaderContextProvider);
            builder.setCacheMap(testCacheMap);
            builder.setValueCache(testValueCache);
            builder.setCacheKeyFunction(testCacheKey);
            builder.setValueCacheOptions(testValueCacheOptions);
            builder.setMaxBatchSize(10);
            builder.setStatisticsCollector(testStatisticsCollectorSupplier);
        });

        assertThat(builtOptions.batchingEnabled(),
                equalTo(false));
        assertThat(builtOptions.getBatchLoaderScheduler(),
                equalTo(testBatchLoaderScheduler));
        assertThat(builtOptions.getBatchLoaderContextProvider(),
                equalTo(testBatchLoaderContextProvider));
        assertThat(builtOptions.cacheMap().get(),
                equalTo(testCacheMap));
        assertThat(builtOptions.cachingEnabled(),
                equalTo(false));
        assertThat(builtOptions.getValueCacheOptions(),
                equalTo(testValueCacheOptions));
        assertThat(builtOptions.cacheKeyFunction().get(),
                equalTo(testCacheKey));
        assertThat(builtOptions.valueCache().get(),
                equalTo(testValueCache));
        assertThat(builtOptions.maxBatchSize(),
                equalTo(10));
        assertThat(builtOptions.getStatisticsCollector(),
                equalTo(testStatisticsCollectorSupplier.get()));

    }

    @Test
    void canBuildViaBuilderOk() {

        DataLoaderOptions.Builder builder = DataLoaderOptions.newOptionsBuilder();
        builder.setBatchingEnabled(false);
        builder.setCachingExceptionsEnabled(false);
        builder.setCachingEnabled(false);
        builder.setBatchLoaderScheduler(testBatchLoaderScheduler);
        builder.setBatchLoaderContextProvider(testBatchLoaderContextProvider);
        builder.setCacheMap(testCacheMap);
        builder.setValueCache(testValueCache);
        builder.setCacheKeyFunction(testCacheKey);
        builder.setValueCacheOptions(testValueCacheOptions);
        builder.setMaxBatchSize(10);
        builder.setStatisticsCollector(testStatisticsCollectorSupplier);

        DataLoaderOptions builtOptions = builder.build();

        assertThat(builtOptions.batchingEnabled(),
                equalTo(false));
        assertThat(builtOptions.getBatchLoaderScheduler(),
                equalTo(testBatchLoaderScheduler));
        assertThat(builtOptions.getBatchLoaderContextProvider(),
                equalTo(testBatchLoaderContextProvider));
        assertThat(builtOptions.cacheMap().get(),
                equalTo(testCacheMap));
        assertThat(builtOptions.cachingEnabled(),
                equalTo(false));
        assertThat(builtOptions.getValueCacheOptions(),
                equalTo(testValueCacheOptions));
        assertThat(builtOptions.cacheKeyFunction().get(),
                equalTo(testCacheKey));
        assertThat(builtOptions.valueCache().get(),
                equalTo(testValueCache));
        assertThat(builtOptions.maxBatchSize(),
                equalTo(10));
        assertThat(builtOptions.getStatisticsCollector(),
                equalTo(testStatisticsCollectorSupplier.get()));
    }

    @Test
    void canCopyExistingOptionValuesOnTransform() {

        DataLoaderInstrumentation instrumentation1 = new DataLoaderInstrumentation() {
        };
        BatchLoaderContextProvider contextProvider1 = () -> null;

        DataLoaderOptions startingOptions = DataLoaderOptions.newOptionsBuilder().setBatchingEnabled(false)
                .setCachingEnabled(false)
                .setInstrumentation(instrumentation1)
                .setBatchLoaderContextProvider(contextProvider1)
                .build();

        assertThat(startingOptions.batchingEnabled(), equalTo(false));
        assertThat(startingOptions.cachingEnabled(), equalTo(false));
        assertThat(startingOptions.getInstrumentation(), equalTo(instrumentation1));
        assertThat(startingOptions.getBatchLoaderContextProvider(), equalTo(contextProvider1));

        DataLoaderOptions newOptions = startingOptions.transform(builder -> builder.setBatchingEnabled(true));


        // immutable
        assertThat(newOptions, CoreMatchers.not(startingOptions));
        assertThat(startingOptions.batchingEnabled(), equalTo(false));
        assertThat(startingOptions.cachingEnabled(), equalTo(false));
        assertThat(startingOptions.getInstrumentation(), equalTo(instrumentation1));
        assertThat(startingOptions.getBatchLoaderContextProvider(), equalTo(contextProvider1));

        // copied values
        assertThat(newOptions.batchingEnabled(), equalTo(true));
        assertThat(newOptions.cachingEnabled(), equalTo(false));
        assertThat(newOptions.getInstrumentation(), equalTo(instrumentation1));
        assertThat(newOptions.getBatchLoaderContextProvider(), equalTo(contextProvider1));
    }
}