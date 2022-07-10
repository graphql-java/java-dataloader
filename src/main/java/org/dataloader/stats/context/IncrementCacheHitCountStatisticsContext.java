package org.dataloader.stats.context;

public class IncrementCacheHitCountStatisticsContext<K> {

    private final K key;
    private final Object callContext;

    public IncrementCacheHitCountStatisticsContext(K key, Object callContext) {
        this.key = key;
        this.callContext = callContext;
    }

    public IncrementCacheHitCountStatisticsContext(K key) {
        this(key, null);
    }

    public K getKey() {
        return key;
    }

    public Object getCallContext() {
        return callContext;
    }
}
