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

        DataLoaderOptions builtOptions = DataLoaderOptions.newDefaultOptions();
        assertThat(builtOptions, equalTo(optionsDefault));
        assertThat(builtOptions == optionsDefault, equalTo(false));

        DataLoaderOptions transformedOptions = optionsDefault.transform(builder -> {
        });
        assertThat(transformedOptions, equalTo(optionsDefault));
        assertThat(transformedOptions == optionsDefault, equalTo(false));
    }

    @Test
    void canCopyOk() {
        DataLoaderOptions optionsNext = DataLoaderOptions.newOptions(optionsDefault).build();
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
        assertThat(optionsDefault.transform(b -> b.setBatchingEnabled(false)).batchingEnabled(),
                equalTo(false));
        assertThat(optionsDefault.transform(b -> b.setBatchLoaderScheduler(testBatchLoaderScheduler)).getBatchLoaderScheduler(),
                equalTo(testBatchLoaderScheduler));
        assertThat(optionsDefault.transform(b -> b.setBatchLoaderContextProvider(testBatchLoaderContextProvider)).getBatchLoaderContextProvider(),
                equalTo(testBatchLoaderContextProvider));
        assertThat(optionsDefault.transform(b -> b.setCacheMap(testCacheMap)).cacheMap().get(),
                equalTo(testCacheMap));
        assertThat(optionsDefault.transform(b -> b.setCachingEnabled(false)).cachingEnabled(),
                equalTo(false));
        assertThat(optionsDefault.transform(b -> b.setValueCacheOptions(testValueCacheOptions)).getValueCacheOptions(),
                equalTo(testValueCacheOptions));
        assertThat(optionsDefault.transform(b -> b.setCacheKeyFunction(testCacheKey)).cacheKeyFunction().get(),
                equalTo(testCacheKey));
        assertThat(optionsDefault.transform(b -> b.setValueCache(testValueCache)).valueCache().get(),
                equalTo(testValueCache));
        assertThat(optionsDefault.transform(b -> b.setMaxBatchSize(10)).maxBatchSize(),
                equalTo(10));
        assertThat(optionsDefault.transform(b -> b.setStatisticsCollector(testStatisticsCollectorSupplier)).getStatisticsCollector(),
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

        DataLoaderOptions.Builder builder = DataLoaderOptions.newOptions();
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
        DataLoaderInstrumentation instrumentation2 = new DataLoaderInstrumentation() {
        };
        BatchLoaderContextProvider contextProvider1 = () -> null;

        DataLoaderOptions startingOptions = DataLoaderOptions.newOptions().setBatchingEnabled(false)
                .setCachingEnabled(false)
                .setInstrumentation(instrumentation1)
                .setBatchLoaderContextProvider(contextProvider1)
                .build();

        assertThat(startingOptions.batchingEnabled(), equalTo(false));
        assertThat(startingOptions.cachingEnabled(), equalTo(false));
        assertThat(startingOptions.getInstrumentation(), equalTo(instrumentation1));
        assertThat(startingOptions.getBatchLoaderContextProvider(), equalTo(contextProvider1));

        DataLoaderOptions newOptions = startingOptions.transform(builder ->
                builder.setBatchingEnabled(true).setInstrumentation(instrumentation2));


        // immutable
        assertThat(newOptions, CoreMatchers.not(startingOptions));
        assertThat(startingOptions.batchingEnabled(), equalTo(false));
        assertThat(startingOptions.cachingEnabled(), equalTo(false));
        assertThat(startingOptions.getInstrumentation(), equalTo(instrumentation1));
        assertThat(startingOptions.getBatchLoaderContextProvider(), equalTo(contextProvider1));

        // stayed the same
        assertThat(newOptions.cachingEnabled(), equalTo(false));
        assertThat(newOptions.getBatchLoaderContextProvider(), equalTo(contextProvider1));

        // was changed
        assertThat(newOptions.batchingEnabled(), equalTo(true));
        assertThat(newOptions.getInstrumentation(), equalTo(instrumentation2));

    }
}