package org.dataloader.stats;

import java.util.LinkedHashMap;
import java.util.Map;

public class StatisticsImpl implements Statistics {

    private final long loadCount;
    private final long loadErrorCount;
    private final long batchInvokeCount;
    private final long batchLoadCount;
    private final long batchLoadExceptionCount;
    private final long cacheHitCount;

    /**
     * Zero statistics
     */
    public StatisticsImpl() {
        this(0, 0, 0, 0, 0, 0);
    }

    public StatisticsImpl(long loadCount, long loadErrorCount, long batchInvokeCount, long batchLoadCount, long batchLoadExceptionCount, long cacheHitCount) {
        this.loadCount = loadCount;
        this.batchInvokeCount = batchInvokeCount;
        this.batchLoadCount = batchLoadCount;
        this.cacheHitCount = cacheHitCount;
        this.batchLoadExceptionCount = batchLoadExceptionCount;
        this.loadErrorCount = loadErrorCount;
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0f : ((double) numerator) / ((double) denominator);
    }

    @Override
    public long getLoadCount() {
        return loadCount;
    }


    @Override
    public long getLoadErrorCount() {
        return loadErrorCount;
    }

    @Override
    public double getLoadErrorRatio() {
        return ratio(loadErrorCount, loadCount);
    }

    @Override
    public long getBatchInvokeCount() {
        return batchInvokeCount;
    }

    @Override
    public long getBatchLoadCount() {
        return batchLoadCount;
    }

    @Override
    public double getBatchLoadRatio() {
        return ratio(batchLoadCount, loadCount);
    }

    @Override
    public long getBatchLoadExceptionCount() {
        return batchLoadExceptionCount;
    }

    @Override
    public double getBatchLoadExceptionRatio() {
        return ratio(batchLoadExceptionCount, loadCount);
    }

    @Override
    public long getCacheHitCount() {
        return cacheHitCount;
    }

    @Override
    public long getCacheMissCount() {
        return loadCount - cacheHitCount;
    }

    @Override
    public double getCacheHitRatio() {
        return ratio(cacheHitCount, loadCount);
    }


    @Override
    public Statistics combine(Statistics other) {
        return new StatisticsImpl(
                this.loadCount + other.getLoadCount(),
                this.loadErrorCount + other.getLoadErrorCount(),
                this.batchInvokeCount + other.getBatchInvokeCount(),
                this.batchLoadCount + other.getBatchLoadCount(),
                this.batchLoadExceptionCount + other.getBatchLoadExceptionCount(),
                this.cacheHitCount + other.getCacheHitCount()
        );
    }

    @Override
    public Map<String, Number> toMap() {
        Map<String, Number> stats = new LinkedHashMap<>();
        stats.put("loadCount", getLoadCount());
        stats.put("loadErrorCount", getLoadErrorCount());
        stats.put("loadErrorRatio", getLoadErrorRatio());

        stats.put("batchInvokeCount", getBatchInvokeCount());
        stats.put("batchLoadCount", getBatchLoadCount());
        stats.put("batchLoadRatio", getBatchLoadRatio());
        stats.put("batchLoadExceptionCount", getBatchLoadExceptionCount());
        stats.put("batchLoadExceptionRatio", getBatchLoadExceptionRatio());

        stats.put("cacheHitCount", getCacheHitCount());
        stats.put("cacheHitRatio", getCacheHitRatio());
        return stats;
    }

    @Override
    public String toString() {
        return "StatisticsImpl{" +
                "loadCount=" + loadCount +
                ", loadErrorCount=" + loadErrorCount +
                ", batchLoadCount=" + batchLoadCount +
                ", batchLoadExceptionCount=" + batchLoadExceptionCount +
                ", cacheHitCount=" + cacheHitCount +
                '}';
    }
}
