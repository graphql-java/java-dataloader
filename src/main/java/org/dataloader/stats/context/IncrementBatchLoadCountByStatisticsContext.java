package org.dataloader.stats.context;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@NullMarked
public class IncrementBatchLoadCountByStatisticsContext<K> {

    private final List<K> keys;
    private final List<@Nullable Object> callContexts;

    public IncrementBatchLoadCountByStatisticsContext(List<K> keys, List<@Nullable Object> callContexts) {
        this.keys = keys;
        this.callContexts = callContexts;
    }

    public IncrementBatchLoadCountByStatisticsContext(K key, @Nullable Object callContext) {
        this(Collections.singletonList(key), Collections.singletonList(callContext));
    }

    public List<K> getKeys() {
        return keys;
    }

    public List<@Nullable Object> getCallContexts() {
        return callContexts;
    }
}
