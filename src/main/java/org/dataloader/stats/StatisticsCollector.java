package org.dataloader.stats;

/**
 * This allows statistics to be collected for {@link org.dataloader.DataLoader} operations
 */
public interface StatisticsCollector {

    /**
     * Called to increment the number of loads
     *
     * @return the current value after increment
     */
    long incrementLoadCount();

    /**
     * Called to increment the number of batch loads
     *
     * @return the current value after increment
     */
    long incrementBatchLoadCount();

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
