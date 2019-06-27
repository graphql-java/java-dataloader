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

package org.dataloader.nextgen;

import org.dataloader.*;
import org.dataloader.stats.StatisticsCollector;

import java.util.function.Supplier;


/**
 * Configuration options for {@link AutoDataLoader} instances.
 *
 * @author <a href="https://github.com/gkesler/">Greg Kesler</a>
 */
public class AutoDataLoaderOptions extends DataLoaderOptions {
    public static final Dispatcher SHARED_DISPATCHER = new Dispatcher();

    private Dispatcher dispatcher;

    /**
     * Creates a new data loader options with default settings.
     */
    public AutoDataLoaderOptions() {
        dispatcher = SHARED_DISPATCHER;
    }

    /**
     * Clones the provided data loader options.
     *
     * @param other the other options instance
     */
    public AutoDataLoaderOptions(AutoDataLoaderOptions other) {
        super(other);

        this.dispatcher = other.dispatcher;
    }

    /**
     * @return a new default data loader options that you can then customize
     */
    public static AutoDataLoaderOptions newOptions() {
        return new AutoDataLoaderOptions();
    }

    /**
     * Sets the option that determines whether batch loading is enabled.
     *
     * @param batchingEnabled {@code true} to enable batch loading, {@code false} otherwise
     *
     * @return the data loader options for fluent coding
     */
    public AutoDataLoaderOptions setBatchingEnabled(boolean batchingEnabled) {
        return (AutoDataLoaderOptions)super.setBatchingEnabled(batchingEnabled);
    }

    /**
     * Sets the option that determines whether caching is enabled.
     *
     * @param cachingEnabled {@code true} to enable caching, {@code false} otherwise
     *
     * @return the data loader options for fluent coding
     */
    public AutoDataLoaderOptions setCachingEnabled(boolean cachingEnabled) {
        return (AutoDataLoaderOptions)super.setCachingEnabled(cachingEnabled);
    }

    /**
     * Sets the option that determines whether exceptional values are cachedis enabled.
     *
     * @param cachingExceptionsEnabled {@code true} to enable caching exceptional values, {@code false} otherwise
     *
     * @return the data loader options for fluent coding
     */
    public AutoDataLoaderOptions setCachingExceptionsEnabled(boolean cachingExceptionsEnabled) {
        return (AutoDataLoaderOptions)super.setCachingExceptionsEnabled(cachingExceptionsEnabled);
    }

    /**
     * Sets the function to use for creating the cache key, if caching is enabled.
     *
     * @param cacheKeyFunction the cache key function to use
     *
     * @return the data loader options for fluent coding
     */
    public AutoDataLoaderOptions setCacheKeyFunction(CacheKey cacheKeyFunction) {
        return (AutoDataLoaderOptions)super.setCacheKeyFunction(cacheKeyFunction);
    }

    /**
     * Sets the cache map implementation to use for caching, if caching is enabled.
     *
     * @param cacheMap the cache map instance
     *
     * @return the data loader options for fluent coding
     */
    public AutoDataLoaderOptions setCacheMap(CacheMap cacheMap) {
        return (AutoDataLoaderOptions)super.setCacheMap(cacheMap);
    }

    /**
     * Sets the maximum number of keys that will be presented to the {@link BatchLoader} function
     * before they are split into multiple class
     *
     * @param maxBatchSize the maximum batch size
     *
     * @return the data loader options for fluent coding
     */
    public AutoDataLoaderOptions setMaxBatchSize(int maxBatchSize) {
        return (AutoDataLoaderOptions)super.setMaxBatchSize(maxBatchSize);
    }

    /**
     * Sets the statistics collector supplier that will be used with these data loader options.  Since it uses
     * the supplier pattern, you can create a new statistics collector on each call or you can reuse
     * a common value
     *
     * @param statisticsCollector the statistics collector to use
     *
     * @return the data loader options for fluent coding
     */
    public AutoDataLoaderOptions setStatisticsCollector(Supplier<StatisticsCollector> statisticsCollector) {
        return (AutoDataLoaderOptions)super.setStatisticsCollector(statisticsCollector);
    }

    /**
     * Sets the batch loader environment provider that will be used to give context to batch load functions
     *
     * @param contextProvider the batch loader context provider
     *
     * @return the data loader options for fluent coding
     */
    public AutoDataLoaderOptions setBatchLoaderContextProvider(BatchLoaderContextProvider contextProvider) {
        return (AutoDataLoaderOptions)super.setBatchLoaderContextProvider(contextProvider);
    }
    
    /**
     * Retrieves Dispatcher instance configured on this data loader opitons
     * 
     * @return configured Dispatcher object
     */
    public Dispatcher dispatcher () {
        return dispatcher;
    }
    
    /**
     * Sets a Dispatcher to be used by AutoDataLoaders created with this object
     * 
     * @param dispatcher new Dispatcher for these options
     * @return this instance to allow method chaining
     */
    public AutoDataLoaderOptions setDispatcher (Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
        return this;
    }
}
