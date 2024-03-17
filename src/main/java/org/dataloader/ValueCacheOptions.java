package org.dataloader;

/**
 * Options that control how the {@link ValueCache} is used by {@link DataLoader}
 *
 * @author <a href="https://github.com/bbakerman/">Brad Baker</a>
 */
public class ValueCacheOptions {
    private final boolean completeValueAfterCacheSet;

    private ValueCacheOptions() {
        this.completeValueAfterCacheSet = false;
    }

    private ValueCacheOptions(boolean completeValueAfterCacheSet) {
        this.completeValueAfterCacheSet = completeValueAfterCacheSet;
    }

    public static ValueCacheOptions newOptions() {
        return new ValueCacheOptions();
    }

    /**
     * This controls whether the {@link DataLoader} will wait for the {@link ValueCache#set(Object, Object)} call
     * to complete before it completes the returned value.  By default, this is false and hence
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

    public ValueCacheOptions setCompleteValueAfterCacheSet(boolean flag) {
        return new ValueCacheOptions(flag);
    }

}
