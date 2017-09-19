package org.dataloader.stats;

/**
 * This can collect statistics per thread as well as in an overall sense.  This allows you to snapshot stats for a web request say
 * as well as all requests.
 *
 * You will want to call {@link #resetThread()} to clean up the thread local aspects of this object per request thread
 *
 * @see org.dataloader.stats.StatisticsCollector
 */
public class ThreadLocalStatisticsCollector implements StatisticsCollector {

    private static final ThreadLocal<SimpleStatisticsCollector> collector = ThreadLocal.withInitial(SimpleStatisticsCollector::new);

    private final SimpleStatisticsCollector overallCollector = new SimpleStatisticsCollector();

    /**
     * Removes the underlying thread local value for this current thread.  This is a way to reset the thread local
     * values for the current thread and start afresh
     *
     * @return this collector for fluent coding
     */
    public ThreadLocalStatisticsCollector resetThread() {
        collector.remove();
        return this;
    }

    @Override
    public long incrementLoadCount() {
        overallCollector.incrementLoadCount();
        return collector.get().incrementLoadCount();
    }

    @Override
    public long incrementBatchLoadCount() {
        overallCollector.incrementBatchLoadCount();
        return collector.get().incrementBatchLoadCount();
    }

    @Override
    public long incrementCacheHitCount() {
        overallCollector.incrementCacheHitCount();
        return collector.get().incrementCacheHitCount();
    }

    /**
     * This returns the statistics for this thread.
     *
     * @return this thread's statistics
     */
    @Override
    public Statistics getStatistics() {
        return collector.get().getStatistics();
    }

    /**
     * This returns the overall statistics, that is not per thread but for the life of this object
     *
     * @return overall statistics
     */
    public Statistics getOverallStatistics() {
        return overallCollector.getStatistics();
    }

    @Override
    public String toString() {
        return "ThreadLocalStatisticsCollector{" +
                "thread=" + getStatistics().toString() +
                "overallCollector=" + overallCollector.getStatistics().toString() +
                '}';
    }
}
