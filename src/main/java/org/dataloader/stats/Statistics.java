package org.dataloader.stats;

/**
 * A simple set of statistics about {@link org.dataloader.DataLoader} operations
 */
public interface Statistics {
    /**
     * @return the number of times {@link org.dataloader.DataLoader#load(Object)} has been called
     */
    long getLoadCount();

    /**
     * @return the number of times the {@link org.dataloader.DataLoader} batch loader function has been called
     */
    long getBatchLoadCount();

    /**
     * @return batchLoadCount / loadCount
     */
    float getBatchLoadRatio();

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
    float getCacheHitRatio();

    /**
     * @return the number of times the {@link org.dataloader.DataLoader} batch loader function throw an exception when trying to get any values
     */
    long getBatchLoadExceptionCount();

    /**
     * @return batchLoadExceptionCount / loadCount
     */
    float getBatchLoadExceptionRatio();

    /**
     * @return the number of times the {@link org.dataloader.DataLoader} batch loader function return an specific object that was in error
     */
    long getLoadErrorCount();


    /**
     * @return loadErrorCount / loadCount
     */
    float getLoadErrorRatio();

}
