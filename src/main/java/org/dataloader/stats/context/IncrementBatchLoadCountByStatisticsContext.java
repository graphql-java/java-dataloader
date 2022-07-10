package org.dataloader.stats.context;

import java.util.Collections;
import java.util.List;

public class IncrementBatchLoadCountByStatisticsContext<K> {

    private final List<K> keys;
    private final List<Object> callContexts;

    public IncrementBatchLoadCountByStatisticsContext(List<K> keys, List<Object> callContexts) {
        this.keys = keys;
        this.callContexts = callContexts;
    }

    public IncrementBatchLoadCountByStatisticsContext(K key, Object callContext) {
        this.keys = Collections.singletonList(key);
        this.callContexts = Collections.singletonList(callContext);
    }

    public List<K> getKeys() {
        return keys;
    }

    public List<Object> getCallContexts() {
        return callContexts;
    }
}
