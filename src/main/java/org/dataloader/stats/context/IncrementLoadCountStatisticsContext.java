package org.dataloader.stats.context;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class IncrementLoadCountStatisticsContext<K> {

    private final K key;
    private final @Nullable Object callContext;

    public IncrementLoadCountStatisticsContext(K key, @Nullable Object callContext) {
        this.key = key;
        this.callContext = callContext;
    }

    public K getKey() {
        return key;
    }

    public @Nullable Object getCallContext() {
        return callContext;
    }
}
