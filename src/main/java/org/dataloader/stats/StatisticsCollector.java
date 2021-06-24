package org.dataloader.stats;

import org.dataloader.annotations.PublicSpi;

/**
 * This allows statistics to be collected for {@link org.dataloader.DataLoader} operations
 */
@PublicSpi
public interface StatisticsCollector {

    /**
     * Called to increment the number of loads
     *
     * @return the current value after increment
     */
    long incrementLoadCount();

    /**
     * Called to increment the number of loads that resulted in an object deemed in error
     *
     * @return the current value after increment
     */
    long incrementLoadErrorCount();

    /**
     * Called to increment the number of batch loads
     *
     * @param delta how much to add to the count
     *
     * @return the current value after increment
     */
    long incrementBatchLoadCountBy(long delta);

    /**
     * Called to increment the number of batch loads exceptions
     *
     * @return the current value after increment
     */
    long incrementBatchLoadExceptionCount();

    /**
     * Called to increment the number of cache hits
     *
     * @return the current value after increment
     */
    long incrementCacheHitCount();

    /**
     * @return the statistics that have been gathered up to this point in time
     */
    Statistics getStatistics();
}
