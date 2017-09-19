package org.dataloader.stats;

/**
 * A simple set of statistics of {@link org.dataloader.DataLoader} operations
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
}
