package org.dataloader.stats.context;

public class IncrementLoadCountStatisticsContext<K> {

    private final K key;
    private final Object callContext;

    public IncrementLoadCountStatisticsContext(K key, Object callContext) {
        this.key = key;
        this.callContext = callContext;
    }

    public K getKey() {
        return key;
    }

    public Object getCallContext() {
        return callContext;
    }
}
