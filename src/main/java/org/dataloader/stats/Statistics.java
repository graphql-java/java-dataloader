package org.dataloader.stats;

import java.util.Map;

/**
 * A simple set of statistics about {@link org.dataloader.DataLoader} operations
 */
public interface Statistics {
    /**
     * @return the number of objects {@link org.dataloader.DataLoader#load(Object)} has been asked to load
     */
    long getLoadCount();

    /**
     * @return the number of times the {@link org.dataloader.DataLoader} batch loader function has been called
     */
    long getBatchInvokeCount();

    /**
     * @return the number of objects that the {@link org.dataloader.DataLoader} batch loader function has been asked to load
     */
    long getBatchLoadCount();

    /**
     * @return batchLoadCount / loadCount
     */
    double getBatchLoadRatio();

    /**
     * @return the number of times  {@link org.dataloader.DataLoader#load(Object)} resulted in a cache hit
     */
    long getCacheHitCount();

    /**
     * @return then number of times we missed the cache during {@link org.dataloader.DataLoader#load(Object)}
     */
    long getCacheMissCount();

    /**
     * @return cacheHits / loadCount
     */
    double getCacheHitRatio();

    /**
     * @return the number of times the {@link org.dataloader.DataLoader} batch loader function throw an exception when trying to get any values
     */
    long getBatchLoadExceptionCount();

    /**
     * @return batchLoadExceptionCount / loadCount
     */
    double getBatchLoadExceptionRatio();

    /**
     * @return the number of times the {@link org.dataloader.DataLoader} batch loader function return an specific object that was in error
     */
    long getLoadErrorCount();


    /**
     * @return loadErrorCount / loadCount
     */
    double getLoadErrorRatio();


    /**
     * This will combine this set of statistics with another set of statistics so that they become the combined count of each
     *
     * @param statistics the other statistics to combine
     *
     * @return a new statistics object of the combined counts
     */
    Statistics combine(Statistics statistics);

    /**
     * @return a map representation of the statistics, perhaps to send over JSON or some such
     */
    Map<String, Number> toMap();
}
