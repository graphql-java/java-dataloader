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
import org.dataloader.impl.Assertions;
import org.dataloader.instrumentation.DataLoaderInstrumentation;
import org.dataloader.instrumentation.DataLoaderInstrumentationHelper;
import org.dataloader.scheduler.BatchLoaderScheduler;
import org.dataloader.stats.NoOpStatisticsCollector;
import org.dataloader.stats.StatisticsCollector;

import java.util.Optional;
import java.util.function.Supplier;

import static org.dataloader.impl.Assertions.nonNull;

/**
 * Configuration options for {@link DataLoader} instances.
 *
 * @author <a href="https://github.com/aschrijver/">Arnold Schrijver</a>
 */
@PublicApi
public class DataLoaderOptions {

    private static final BatchLoaderContextProvider NULL_PROVIDER = () -> null;

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

    /**
     * Creates a new data loader options with default settings.
     */
    public DataLoaderOptions() {
        batchingEnabled = true;
        cachingEnabled = true;
        cachingExceptionsEnabled = true;
        maxBatchSize = -1;
        statisticsCollector = NoOpStatisticsCollector::new;
        environmentProvider = NULL_PROVIDER;
        valueCacheOptions = ValueCacheOptions.newOptions();
        batchLoaderScheduler = null;
        instrumentation = DataLoaderInstrumentationHelper.NOOP_INSTRUMENTATION;
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
     * @return the data loader options for fluent coding
     */
    public DataLoaderOptions setBatchingEnabled(boolean batchingEnabled) {
        this.batchingEnabled = batchingEnabled;
        return this;
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
     * @return the data loader options for fluent coding
     */
    public DataLoaderOptions setCachingEnabled(boolean cachingEnabled) {
        this.cachingEnabled = cachingEnabled;
        return this;
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
     * @return the data loader options for fluent coding
     */
    public DataLoaderOptions setCachingExceptionsEnabled(boolean cachingExceptionsEnabled) {
        this.cachingExceptionsEnabled = cachingExceptionsEnabled;
        return this;
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
     * @return the data loader options for fluent coding
     */
    public DataLoaderOptions setCacheKeyFunction(CacheKey<?> cacheKeyFunction) {
        this.cacheKeyFunction = cacheKeyFunction;
        return this;
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
     * @return the data loader options for fluent coding
     */
    public DataLoaderOptions setCacheMap(CacheMap<?, ?> cacheMap) {
        this.cacheMap = cacheMap;
        return this;
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
     * @return the data loader options for fluent coding
     */
    public DataLoaderOptions setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
        return this;
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
     * @return the data loader options for fluent coding
     */
    public DataLoaderOptions setStatisticsCollector(Supplier<StatisticsCollector> statisticsCollector) {
        this.statisticsCollector = nonNull(statisticsCollector);
        return this;
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
     * @return the data loader options for fluent coding
     */
    public DataLoaderOptions setBatchLoaderContextProvider(BatchLoaderContextProvider contextProvider) {
        this.environmentProvider = nonNull(contextProvider);
        return this;
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
     * @return the data loader options for fluent coding
     */
    public DataLoaderOptions setValueCache(ValueCache<?, ?> valueCache) {
        this.valueCache = valueCache;
        return this;
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
     * @return the data loader options for fluent coding
     */
    public DataLoaderOptions setValueCacheOptions(ValueCacheOptions valueCacheOptions) {
        this.valueCacheOptions = Assertions.nonNull(valueCacheOptions);
        return this;
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
     * @return the data loader options for fluent coding
     */
    public DataLoaderOptions setBatchLoaderScheduler(BatchLoaderScheduler batchLoaderScheduler) {
        this.batchLoaderScheduler = batchLoaderScheduler;
        return this;
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
     * @return the data loader options for fluent coding
     */
    public DataLoaderOptions setInstrumentation(DataLoaderInstrumentation instrumentation) {
        this.instrumentation = nonNull(instrumentation);
        return this;
    }
}
