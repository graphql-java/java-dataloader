package org.dataloader;

import org.dataloader.annotations.PublicApi;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A dispatch strategy that dispatches immediately if it is not busy and not currently dispatching.
 * <p/>
 * Busy is determined by a busy counter, which is > 0 if busy, or zero when not busy.
 * The two methods to increase and decrease the busy counter are {@link #incrementBusyCount()} and {@link #decrementBusyCount()}
 * <p/>
 * This Strategy must be configured as part of {@link DispatchStrategy}
 */
@PublicApi
@NullMarked
public class NotBusyDispatchStrategy implements DispatchStrategy {


    // 30 bits for busy counting
    // 1 bit for dataLoaderToDispatch
    // 1 bit for currentlyDispatching

    // Bit positions (from right to left)
    static final int currentlyDispatchingShift = 0;
    static final int dataLoaderToDispatchShift = 1;
    static final int busyCountShift = 2;

    // mask
    static final int booleanMask = 1;
    static final int busyCountMask = (1 << 30) - 1;

    public static int getBusyCount(int state) {
        return (state >> busyCountShift) & busyCountMask;
    }

    public static int setBusyCount(int state, int busyCount) {
        return (state & ~(busyCountMask << busyCountShift)) |
               (busyCount << busyCountShift);
    }

    public static int setDataLoaderToDispatch(int state, boolean dataLoaderToDispatch) {
        return (state & ~(booleanMask << dataLoaderToDispatchShift)) |
               ((dataLoaderToDispatch ? 1 : 0) << dataLoaderToDispatchShift);
    }

    public static int setCurrentlyDispatching(int state, boolean currentlyDispatching) {
        return (state & ~(booleanMask << currentlyDispatchingShift)) |
               ((currentlyDispatching ? 1 : 0) << currentlyDispatchingShift);
    }


    public static boolean getDataLoaderToDispatch(int state) {
        return ((state >> dataLoaderToDispatchShift) & booleanMask) != 0;
    }

    public static boolean getCurrentlyDispatching(int state) {
        return ((state >> currentlyDispatchingShift) & booleanMask) != 0;
    }


    private final AtomicInteger state = new AtomicInteger();
    private final DataLoaderRegistry dataLoaderRegistry;

    public NotBusyDispatchStrategy(DataLoaderRegistry dataLoaderRegistry) {
        this.dataLoaderRegistry = dataLoaderRegistry;
    }


    private int incrementBusyCountImpl() {
        while (true) {
            int oldState = getState();
            int busyCount = getBusyCount(oldState);
            int newState = setBusyCount(oldState, busyCount + 1);
            if (tryUpdateState(oldState, newState)) {
                return newState;
            }
        }
    }

    private int decrementBusyCountImpl() {
        while (true) {
            int oldState = getState();
            int busyCount = getBusyCount(oldState);
            int newState = setBusyCount(oldState, busyCount - 1);
            if (tryUpdateState(oldState, newState)) {
                return newState;
            }
        }
    }

    private int getState() {
        return state.get();
    }


    private boolean tryUpdateState(int oldState, int newState) {
        return state.compareAndSet(oldState, newState);
    }


    public void decrementBusyCount() {
        int newState = decrementBusyCountImpl();
        if (getBusyCount(newState) == 0 && getDataLoaderToDispatch(newState) && !getCurrentlyDispatching(newState)) {
            dispatchImpl();
        }
    }

    public void incrementBusyCount() {
        incrementBusyCountImpl();
    }


    private void newDataLoaderInvocationMaybeDispatch() {
        int currentState;
        while (true) {
            int oldState = getState();
            if (getDataLoaderToDispatch(oldState)) {
                return;
            }
            int newState = setDataLoaderToDispatch(oldState, true);
            if (tryUpdateState(oldState, newState)) {
                currentState = newState;
                break;
            }
        }

        if (getBusyCount(currentState) == 0 && !getCurrentlyDispatching(currentState)) {
            dispatchImpl();
        }
    }


    private void dispatchImpl() {
        while (true) {
            int oldState = getState();
            if (!getDataLoaderToDispatch(oldState)) {
                int newState = setCurrentlyDispatching(oldState, false);
                if (tryUpdateState(oldState, newState)) {
                    return;
                }
            }
            int newState = setCurrentlyDispatching(oldState, true);
            newState = setDataLoaderToDispatch(newState, false);
            if (tryUpdateState(oldState, newState)) {
                break;
            }
        }

        List<DataLoader<?, ?>> dataLoaders = dataLoaderRegistry.getDataLoaders();
        List<CompletableFuture<? extends List<?>>> allDispatchedCFs = new ArrayList<>();
        for (DataLoader<?, ?> dataLoader : dataLoaders) {
            CompletableFuture<? extends List<?>> dispatch = dataLoader.dispatch();
            allDispatchedCFs.add(dispatch);
        }
        CompletableFuture.allOf(allDispatchedCFs.toArray(new CompletableFuture[0]))
                .whenComplete((unused, throwable) -> {
                    dispatchImpl();
                });

    }

    @Override
    public void loadCalled(DataLoader<?, ?> dataLoader) {
        newDataLoaderInvocationMaybeDispatch();
    }

    private static String printState(int state) {
        return "busyCount= " + getBusyCount(state) +
               ",dataLoaderToDispatch= " + getDataLoaderToDispatch(state) +
               ",currentlyDispatching= " + getCurrentlyDispatching(state);
    }

    @Override
    public String toString() {
        return "NotBusyDispatchStrategy{" +
               "state=" + printState(getState()) +
               ", dataLoaderRegistry=" + dataLoaderRegistry +
               '}';
    }
}
