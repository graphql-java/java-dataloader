package org.dataloader.stats;

import org.dataloader.annotations.PublicSpi;
import org.dataloader.stats.context.IncrementBatchLoadCountByStatisticsContext;
import org.dataloader.stats.context.IncrementBatchLoadExceptionCountStatisticsContext;
import org.dataloader.stats.context.IncrementCacheHitCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadCountStatisticsContext;
import org.dataloader.stats.context.IncrementLoadErrorCountStatisticsContext;

/**
 * This allows statistics to be collected for {@link org.dataloader.DataLoader} operations
 */
@PublicSpi
public interface StatisticsCollector {

    /**
     * Called to increment the number of loads
     *
     * @param <K> the class of the key in the data loader
     * @param context the context containing metadata of the data loader invocation
     *
     * @return the current value after increment
     */
    default <K> long incrementLoadCount(IncrementLoadCountStatisticsContext<K> context) {
        return incrementLoadCount();
    }

    /**
     * Called to increment the number of loads
     *
     * @deprecated use {@link #incrementLoadCount(IncrementLoadCountStatisticsContext)}
     * @return the current value after increment
     */
    @Deprecated
    long incrementLoadCount();

    /**
     * Called to increment the number of loads that resulted in an object deemed in error
     *
     * @param <K> the class of the key in the data loader
     * @param context the context containing metadata of the data loader invocation
     *
     * @return the current value after increment
     */
    default <K> long incrementLoadErrorCount(IncrementLoadErrorCountStatisticsContext<K> context) {
        return incrementLoadErrorCount();
    }

    /**
     * Called to increment the number of loads that resulted in an object deemed in error
     *
     * @deprecated use {@link #incrementLoadErrorCount(IncrementLoadErrorCountStatisticsContext)}
     * @return the current value after increment
     */
    @Deprecated
    long incrementLoadErrorCount();

    /**
     * Called to increment the number of batch loads
     *
     * @param <K> the class of the key in the data loader
     * @param delta how much to add to the count
     * @param context the context containing metadata of the data loader invocation
     *
     * @return the current value after increment
     */
    default <K> long incrementBatchLoadCountBy(long delta, IncrementBatchLoadCountByStatisticsContext<K> context) {
        return incrementBatchLoadCountBy(delta);
    }

    /**
     * Called to increment the number of batch loads
     *
     * @param delta how much to add to the count
     *
     * @deprecated use {@link #incrementBatchLoadCountBy(long, IncrementBatchLoadCountByStatisticsContext)}
     * @return the current value after increment
     */
    @Deprecated
    long incrementBatchLoadCountBy(long delta);

    /**
     * Called to increment the number of batch loads exceptions
     *
     * @param <K> the class of the key in the data loader
     * @param context the context containing metadata of the data loader invocation
     *
     * @return the current value after increment
     */
    default <K> long incrementBatchLoadExceptionCount(IncrementBatchLoadExceptionCountStatisticsContext<K> context) {
        return incrementBatchLoadExceptionCount();
    }

    /**
     * Called to increment the number of batch loads exceptions
     *
     * @deprecated use {@link #incrementBatchLoadExceptionCount(IncrementBatchLoadExceptionCountStatisticsContext)}
     * @return the current value after increment
     */
    @Deprecated
    long incrementBatchLoadExceptionCount();

    /**
     * Called to increment the number of cache hits
     *
     * @param <K> the class of the key in the data loader
     * @param context the context containing metadata of the data loader invocation
     *
     * @return the current value after increment
     */
    default <K> long incrementCacheHitCount(IncrementCacheHitCountStatisticsContext<K> context) {
        return incrementCacheHitCount();
    }

    /**
     * Called to increment the number of cache hits
     *
     * @deprecated use {@link #incrementCacheHitCount(IncrementCacheHitCountStatisticsContext)}
     * @return the current value after increment
     */
    @Deprecated
    long incrementCacheHitCount();

    /**
     * @return the statistics that have been gathered to this point in time
     */
    Statistics getStatistics();
}
