package org.dataloader;

/**
 * Options that control how the {@link ValueCache} is used by {@link DataLoader}
 *
 * @author <a href="https://github.com/bbakerman/">Brad Baker</a>
 */
public class ValueCacheOptions {
    private final boolean dispatchOnCacheMiss;
    private final boolean completeValueAfterCacheSet;

    private ValueCacheOptions() {
        this.dispatchOnCacheMiss = true;
        this.completeValueAfterCacheSet = false;
    }

    private ValueCacheOptions(boolean dispatchOnCacheMiss, boolean completeValueAfterCacheSet) {
        this.dispatchOnCacheMiss = dispatchOnCacheMiss;
        this.completeValueAfterCacheSet = completeValueAfterCacheSet;
    }

    public static ValueCacheOptions newOptions() {
        return new ValueCacheOptions();
    }

    /**
     * This controls whether the {@link DataLoader} will called {@link DataLoader#dispatch()} if a
     * {@link ValueCache#get(Object)} call misses.  In an async world this could take non zero time
     * to complete and hence previous dispatch calls may have already completed.
     *
     * This is true by default.
     *
     * @return true if a {@link DataLoader#dispatch()} call will be made on an async {@link ValueCache} miss
     */
    public boolean isDispatchOnCacheMiss() {
        return dispatchOnCacheMiss;
    }

    /**
     * This controls whether the {@link DataLoader} will wait for the {@link ValueCache#set(Object, Object)} call
     * to complete before it completes the returned value.  By default this is false and hence
     * the {@link ValueCache#set(Object, Object)} call may complete some time AFTER the data loader
     * value has been returned.
     *
     * This is false by default, for performance reasons.
     *
     * @return true the {@link DataLoader} will wait for the {@link ValueCache#set(Object, Object)} call to complete before
     * it completes the returned value.
     */
    public boolean isCompleteValueAfterCacheSet() {
        return completeValueAfterCacheSet;
    }

    public ValueCacheOptions setDispatchOnCacheMiss(boolean flag) {
        return new ValueCacheOptions(flag, this.completeValueAfterCacheSet);
    }

    public ValueCacheOptions setCompleteValueAfterCacheSet(boolean flag) {
        return new ValueCacheOptions(this.dispatchOnCacheMiss, flag);
    }

}
