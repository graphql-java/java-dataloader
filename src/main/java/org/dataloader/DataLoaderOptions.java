/*
 * Copyright (c) 2016 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.dataloader;

import org.dataloader.annotations.PublicApi;
import org.dataloader.instrumentation.DataLoaderInstrumentation;
import org.dataloader.instrumentation.DataLoaderInstrumentationHelper;
import org.dataloader.scheduler.BatchLoaderScheduler;
import org.dataloader.stats.NoOpStatisticsCollector;
import org.dataloader.stats.StatisticsCollector;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.dataloader.impl.Assertions.nonNull;

/**
 * Configuration options for {@link DataLoader} instances.  This is an immutable class so each time
 * you change a value it returns a new object.
 *
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 */
@PublicApi
public class DataLoaderOptions {

    private static final BatchLoaderContextProvider NULL_PROVIDER = () -> null;
    private static final Supplier<StatisticsCollector> NOOP_COLLECTOR = NoOpStatisticsCollector::new;
    private static final ValueCacheOptions DEFAULT_VALUE_CACHE_OPTIONS = ValueCacheOptions.newOptions();

    private final boolean batchingEnabled;
    private final boolean cachingEnabled;
    private final boolean cachingExceptionsEnabled;
    private final CacheKey<?> cacheKeyFunction;
    private final CacheMap<?, ?> cacheMap;
    private final ValueCache<?, ?> valueCache;
    private final int maxBatchSize;
    private final Supplier<StatisticsCollector> statisticsCollector;
    private final BatchLoaderContextProvider environmentProvider;
    private final ValueCacheOptions valueCacheOptions;
    private final BatchLoaderScheduler batchLoaderScheduler;
    private final DataLoaderInstrumentation instrumentation;

    /**
     * Creates a new data loader options with default settings.
     */
    public DataLoaderOptions() {
        batchingEnabled = true;
        cachingEnabled = true;
        cachingExceptionsEnabled = true;
        cacheKeyFunction = null;
        cacheMap = null;
        valueCache = null;
        maxBatchSize = -1;
        statisticsCollector = NOOP_COLLECTOR;
        environmentProvider = NULL_PROVIDER;
        valueCacheOptions = DEFAULT_VALUE_CACHE_OPTIONS;
        batchLoaderScheduler = null;
        instrumentation = DataLoaderInstrumentationHelper.NOOP_INSTRUMENTATION;
    }

    private DataLoaderOptions(Builder builder) {
        this.batchingEnabled = builder.batchingEnabled;
        this.cachingEnabled = builder.cachingEnabled;
        this.cachingExceptionsEnabled = builder.cachingExceptionsEnabled;
        this.cacheKeyFunction = builder.cacheKeyFunction;
        this.cacheMap = builder.cacheMap;
        this.valueCache = builder.valueCache;
        this.maxBatchSize = builder.maxBatchSize;
        this.statisticsCollector = builder.statisticsCollector;
        this.environmentProvider = builder.environmentProvider;
        this.valueCacheOptions = builder.valueCacheOptions;
        this.batchLoaderScheduler = builder.batchLoaderScheduler;
        this.instrumentation = builder.instrumentation;
    }

    /**
     * Clones the provided data loader options.
     *
     * @param other the other options instance
     */
    public DataLoaderOptions(DataLoaderOptions other) {
        nonNull(other);
        this.batchingEnabled = other.batchingEnabled;
        this.cachingEnabled = other.cachingEnabled;
        this.cachingExceptionsEnabled = other.cachingExceptionsEnabled;
        this.cacheKeyFunction = other.cacheKeyFunction;
        this.cacheMap = other.cacheMap;
        this.valueCache = other.valueCache;
        this.maxBatchSize = other.maxBatchSize;
        this.statisticsCollector = other.statisticsCollector;
        this.environmentProvider = other.environmentProvider;
        this.valueCacheOptions = other.valueCacheOptions;
        this.batchLoaderScheduler = other.batchLoaderScheduler;
        this.instrumentation = other.instrumentation;
    }

    /**
     * @return a new default data loader options that you can then customize
     */
    public static DataLoaderOptions newOptions() {
        return new DataLoaderOptions();
    }

    /**
     * @return a new default data loader options {@link Builder} that you can then customize
     */
    public static DataLoaderOptions.Builder newOptionsBuilder() {
        return new DataLoaderOptions.Builder();
    }

    /**
     * @param otherOptions the options to copy
     * @return a new default data loader options {@link Builder} from the specified one that you can then customize
     */
    public static DataLoaderOptions.Builder newDataLoaderOptions(DataLoaderOptions otherOptions) {
        return new DataLoaderOptions.Builder(otherOptions);
    }

    /**
     * Will transform the current options in to a builder ands allow you to build a new set of options
     *
     * @param builderConsumer the consumer of a builder that has this objects starting values
     * @return a new {@link DataLoaderOptions} object
     */
    public DataLoaderOptions transform(Consumer<Builder> builderConsumer) {
        Builder builder = newOptionsBuilder();
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        DataLoaderOptions that = (DataLoaderOptions) o;
        return batchingEnabled == that.batchingEnabled
                && cachingEnabled == that.cachingEnabled
                && cachingExceptionsEnabled == that.cachingExceptionsEnabled
                && maxBatchSize == that.maxBatchSize
                && Objects.equals(cacheKeyFunction, that.cacheKeyFunction) &&
                Objects.equals(cacheMap, that.cacheMap) &&
                Objects.equals(valueCache, that.valueCache) &&
                Objects.equals(statisticsCollector, that.statisticsCollector) &&
                Objects.equals(environmentProvider, that.environmentProvider) &&
                Objects.equals(valueCacheOptions, that.valueCacheOptions) &&
                Objects.equals(batchLoaderScheduler, that.batchLoaderScheduler);
    }


    /**
     * Option that determines whether to use batching (the default), or not.
     *
     * @return {@code true} when batching is enabled, {@code false} otherwise
     */
    public boolean batchingEnabled() {
        return batchingEnabled;
    }

    /**
     * Sets the option that determines whether batch loading is enabled.
     *
     * @param batchingEnabled {@code true} to enable batch loading, {@code false} otherwise
     * @return a new data loader options instance for fluent coding
     */
    public DataLoaderOptions setBatchingEnabled(boolean batchingEnabled) {
        return builder().setBatchingEnabled(batchingEnabled).build();
    }

    /**
     * Option that determines whether to use caching of futures (the default), or not.
     *
     * @return {@code true} when caching is enabled, {@code false} otherwise
     */
    public boolean cachingEnabled() {
        return cachingEnabled;
    }

    /**
     * Sets the option that determines whether caching is enabled.
     *
     * @param cachingEnabled {@code true} to enable caching, {@code false} otherwise
     * @return a new data loader options instance for fluent coding
     */
    public DataLoaderOptions setCachingEnabled(boolean cachingEnabled) {
        return builder().setCachingEnabled(cachingEnabled).build();
    }

    /**
     * Option that determines whether to cache exceptional values (the default), or not.
     * <p>
     * For short-lived caches (that is request caches) it makes sense to cache exceptions since
     * it's likely the key is still poisoned.  However, if you have long-lived caches, then it may make
     * sense to set this to false since the downstream system may have recovered from its failure
     * mode.
     *
     * @return {@code true} when exceptional values are cached is enabled, {@code false} otherwise
     */
    public boolean cachingExceptionsEnabled() {
        return cachingExceptionsEnabled;
    }

    /**
     * Sets the option that determines whether exceptional values are cache enabled.
     *
     * @param cachingExceptionsEnabled {@code true} to enable caching exceptional values, {@code false} otherwise
     * @return a new data loader options instance for fluent coding
     */
    public DataLoaderOptions setCachingExceptionsEnabled(boolean cachingExceptionsEnabled) {
        return builder().setCachingExceptionsEnabled(cachingExceptionsEnabled).build();
    }

    /**
     * Gets an (optional) function to invoke for creation of the cache key, if caching is enabled.
     * <p>
     * If missing the cache key defaults to the {@code key} type parameter of the data loader of type {@code K}.
     *
     * @return an optional with the function, or empty optional
     */
    public Optional<CacheKey> cacheKeyFunction() {
        return Optional.ofNullable(cacheKeyFunction);
    }

    /**
     * Sets the function to use for creating the cache key, if caching is enabled.
     *
     * @param cacheKeyFunction the cache key function to use
     * @return a new data loader options instance for fluent coding
     */
    public DataLoaderOptions setCacheKeyFunction(CacheKey<?> cacheKeyFunction) {
        return builder().setCacheKeyFunction(cacheKeyFunction).build();
    }

    /**
     * Gets the (optional) cache map implementation that is used for caching, if caching is enabled.
     * <p>
     * If missing a standard {@link java.util.LinkedHashMap} will be used as the cache implementation.
     *
     * @return an optional with the cache map instance, or empty
     */
    public Optional<CacheMap<?, ?>> cacheMap() {
        return Optional.ofNullable(cacheMap);
    }

    /**
     * Sets the cache map implementation to use for caching, if caching is enabled.
     *
     * @param cacheMap the cache map instance
     * @return a new data loader options instance for fluent coding
     */
    public DataLoaderOptions setCacheMap(CacheMap<?, ?> cacheMap) {
        return builder().setCacheMap(cacheMap).build();
    }

    /**
     * Gets the maximum number of keys that will be presented to the {@link BatchLoader} function
     * before they are split into multiple class
     *
     * @return the maximum batch size or -1 if there is no limit
     */
    public int maxBatchSize() {
        return maxBatchSize;
    }

    /**
     * Sets the maximum number of keys that will be presented to the {@link BatchLoader} function
     * before they are split into multiple class
     *
     * @param maxBatchSize the maximum batch size
     * @return a new data loader options instance for fluent coding
     */
    public DataLoaderOptions setMaxBatchSize(int maxBatchSize) {
        return builder().setMaxBatchSize(maxBatchSize).build();
    }

    /**
     * @return the statistics collector to use with these options
     */
    public StatisticsCollector getStatisticsCollector() {
        return nonNull(this.statisticsCollector.get());
    }

    /**
     * Sets the statistics collector supplier that will be used with these data loader options.  Since it uses
     * the supplier pattern, you can create a new statistics collector on each call, or you can reuse
     * a common value
     *
     * @param statisticsCollector the statistics collector to use
     * @return a new data loader options instance for fluent coding
     */
    public DataLoaderOptions setStatisticsCollector(Supplier<StatisticsCollector> statisticsCollector) {
        return builder().setStatisticsCollector(nonNull(statisticsCollector)).build();
    }

    /**
     * @return the batch environment provider that will be used to give context to batch load functions
     */
    public BatchLoaderContextProvider getBatchLoaderContextProvider() {
        return environmentProvider;
    }

    /**
     * Sets the batch loader environment provider that will be used to give context to batch load functions
     *
     * @param contextProvider the batch loader context provider
     * @return a new data loader options instance for fluent coding
     */
    public DataLoaderOptions setBatchLoaderContextProvider(BatchLoaderContextProvider contextProvider) {
        return builder().setBatchLoaderContextProvider(nonNull(contextProvider)).build();
    }

    /**
     * Gets the (optional) cache store implementation that is used for value caching, if caching is enabled.
     * <p>
     * If missing, a no-op implementation will be used.
     *
     * @return an optional with the cache store instance, or empty
     */
    public Optional<ValueCache<?, ?>> valueCache() {
        return Optional.ofNullable(valueCache);
    }

    /**
     * Sets the value cache implementation to use for caching values, if caching is enabled.
     *
     * @param valueCache the value cache instance
     * @return a new data loader options instance for fluent coding
     */
    public DataLoaderOptions setValueCache(ValueCache<?, ?> valueCache) {
        return builder().setValueCache(valueCache).build();
    }

    /**
     * @return the {@link ValueCacheOptions} that control how the {@link ValueCache} will be used
     */
    public ValueCacheOptions getValueCacheOptions() {
        return valueCacheOptions;
    }

    /**
     * Sets the {@link ValueCacheOptions} that control how the {@link ValueCache} will be used
     *
     * @param valueCacheOptions the value cache options
     * @return a new data loader options instance for fluent coding
     */
    public DataLoaderOptions setValueCacheOptions(ValueCacheOptions valueCacheOptions) {
        return builder().setValueCacheOptions(nonNull(valueCacheOptions)).build();
    }

    /**
     * @return the {@link BatchLoaderScheduler} to use, which can be null
     */
    public BatchLoaderScheduler getBatchLoaderScheduler() {
        return batchLoaderScheduler;
    }

    /**
     * Sets in a new {@link BatchLoaderScheduler} that allows the call to a {@link BatchLoader} function to be scheduled
     * to some future time.
     *
     * @param batchLoaderScheduler the scheduler
     * @return a new data loader options instance for fluent coding
     */
    public DataLoaderOptions setBatchLoaderScheduler(BatchLoaderScheduler batchLoaderScheduler) {
        return builder().setBatchLoaderScheduler(batchLoaderScheduler).build();
    }

    /**
     * @return the {@link DataLoaderInstrumentation} to use
     */
    public DataLoaderInstrumentation getInstrumentation() {
        return instrumentation;
    }

    /**
     * Sets in a new {@link DataLoaderInstrumentation}
     *
     * @param instrumentation the new {@link DataLoaderInstrumentation}
     * @return a new data loader options instance for fluent coding
     */
    public DataLoaderOptions setInstrumentation(DataLoaderInstrumentation instrumentation) {
        return builder().setInstrumentation(instrumentation).build();
    }

    private Builder builder() {
        return new Builder(this);
    }

    public static class Builder {
        private boolean batchingEnabled;
        private boolean cachingEnabled;
        private boolean cachingExceptionsEnabled;
        private CacheKey<?> cacheKeyFunction;
        private CacheMap<?, ?> cacheMap;
        private ValueCache<?, ?> valueCache;
        private int maxBatchSize;
        private Supplier<StatisticsCollector> statisticsCollector;
        private BatchLoaderContextProvider environmentProvider;
        private ValueCacheOptions valueCacheOptions;
        private BatchLoaderScheduler batchLoaderScheduler;
        private DataLoaderInstrumentation instrumentation;

        public Builder() {
            this(new DataLoaderOptions()); // use the defaults of the DataLoaderOptions for this builder
        }

        Builder(DataLoaderOptions other) {
            this.batchingEnabled = other.batchingEnabled;
            this.cachingEnabled = other.cachingEnabled;
            this.cachingExceptionsEnabled = other.cachingExceptionsEnabled;
            this.cacheKeyFunction = other.cacheKeyFunction;
            this.cacheMap = other.cacheMap;
            this.valueCache = other.valueCache;
            this.maxBatchSize = other.maxBatchSize;
            this.statisticsCollector = other.statisticsCollector;
            this.environmentProvider = other.environmentProvider;
            this.valueCacheOptions = other.valueCacheOptions;
            this.batchLoaderScheduler = other.batchLoaderScheduler;
            this.instrumentation = other.instrumentation;
        }

        public Builder setBatchingEnabled(boolean batchingEnabled) {
            this.batchingEnabled = batchingEnabled;
            return this;
        }

        public Builder setCachingEnabled(boolean cachingEnabled) {
            this.cachingEnabled = cachingEnabled;
            return this;
        }

        public Builder setCachingExceptionsEnabled(boolean cachingExceptionsEnabled) {
            this.cachingExceptionsEnabled = cachingExceptionsEnabled;
            return this;
        }

        public Builder setCacheKeyFunction(CacheKey<?> cacheKeyFunction) {
            this.cacheKeyFunction = cacheKeyFunction;
            return this;
        }

        public Builder setCacheMap(CacheMap<?, ?> cacheMap) {
            this.cacheMap = cacheMap;
            return this;
        }

        public Builder setValueCache(ValueCache<?, ?> valueCache) {
            this.valueCache = valueCache;
            return this;
        }

        public Builder setMaxBatchSize(int maxBatchSize) {
            this.maxBatchSize = maxBatchSize;
            return this;
        }

        public Builder setStatisticsCollector(Supplier<StatisticsCollector> statisticsCollector) {
            this.statisticsCollector = statisticsCollector;
            return this;
        }

        public Builder setBatchLoaderContextProvider(BatchLoaderContextProvider environmentProvider) {
            this.environmentProvider = environmentProvider;
            return this;
        }

        public Builder setValueCacheOptions(ValueCacheOptions valueCacheOptions) {
            this.valueCacheOptions = valueCacheOptions;
            return this;
        }

        public Builder setBatchLoaderScheduler(BatchLoaderScheduler batchLoaderScheduler) {
            this.batchLoaderScheduler = batchLoaderScheduler;
            return this;
        }

        public Builder setInstrumentation(DataLoaderInstrumentation instrumentation) {
            this.instrumentation = nonNull(instrumentation);
            return this;
        }

        public DataLoaderOptions build() {
            return new DataLoaderOptions(this);
        }

    }
}
