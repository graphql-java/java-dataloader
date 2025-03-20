package org.dataloader.orchestration;

import org.dataloader.DataLoader;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This needs HEAPS more work - heaps more - I am not sure if its just counts of call backs or what.
 * <p>
 * This is just POC stuff for now
 */
public class Tracker {
    private final AtomicInteger stepCount = new AtomicInteger();
    private final Map<DataLoader<?,?>, AtomicInteger> counters = new HashMap<>();

    public int getOutstandingLoadCount(DataLoader<?,?> dl) {
        synchronized (this) {
            return getDLCounter(dl).intValue();
        }
    }

    public int getOutstandingLoadCount() {
        int count = 0;
        synchronized (this) {
            for (AtomicInteger atomicInteger : counters.values()) {
                count += atomicInteger.get();
            }
        }
        return count;
    }

    public int getStepCount() {
        return stepCount.get();
    }

    void incrementStepCount() {
        this.stepCount.incrementAndGet();
    }

    void loadCall(DataLoader<?,?> dl) {
        synchronized (this) {
            getDLCounter(dl).incrementAndGet();
        }
    }

    void loadCallComplete(DataLoader<?,?> dl) {
        synchronized (this) {
            getDLCounter(dl).decrementAndGet();
        }
    }

    private AtomicInteger getDLCounter(DataLoader<?, ?> dl) {
        return counters.computeIfAbsent(dl, key -> new AtomicInteger());
    }
}
