package org.dataloader.orchestration.observation;

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
    private final TrackingObserver trackingObserver;

    public Tracker(TrackingObserver trackingObserver) {
        this.trackingObserver = trackingObserver;
    }

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

    public void incrementStepCount() {
        this.stepCount.incrementAndGet();
    }

    public void startingExecution() {
        trackingObserver.onStart(this);
    }

    public void loadCall(int stepIndex, DataLoader<?,?> dl) {
        synchronized (this) {
            getDLCounter(dl).incrementAndGet();
            trackingObserver.onLoad(this, stepIndex, dl);
        }
    }

    public void loadCallComplete(int stepIndex, DataLoader<?,?> dl, Throwable throwable) {
        synchronized (this) {
            getDLCounter(dl).decrementAndGet();
            trackingObserver.onLoadComplete(this,stepIndex,dl, throwable);
        }
    }

    private AtomicInteger getDLCounter(DataLoader<?, ?> dl) {
        return counters.computeIfAbsent(dl, key -> new AtomicInteger());
    }
}
