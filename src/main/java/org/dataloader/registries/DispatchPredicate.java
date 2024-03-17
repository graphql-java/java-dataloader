package org.dataloader.registries;

import org.dataloader.DataLoader;

import java.time.Duration;
import java.util.Objects;

/**
 * A predicate class used by {@link ScheduledDataLoaderRegistry} to decide whether to dispatch or not
 */
@FunctionalInterface
public interface DispatchPredicate {

    /**
     * A predicate that always returns true
     */
    DispatchPredicate DISPATCH_ALWAYS = (dataLoaderKey, dataLoader) -> true;
    /**
     * A predicate that always returns false
     */
    DispatchPredicate DISPATCH_NEVER = (dataLoaderKey, dataLoader) -> false;

    /**
     * This predicate tests whether the data loader should be dispatched or not.
     *
     * @param dataLoaderKey the key of the data loader when registered
     * @param dataLoader    the dataloader to dispatch
     *
     * @return true if the data loader SHOULD be dispatched
     */
    boolean test(String dataLoaderKey, DataLoader<?, ?> dataLoader);


    /**
     * Returns a composed predicate that represents a short-circuiting logical
     * AND of this predicate and another.
     *
     * @param other a predicate that will be logically-ANDed with this
     *              predicate
     *
     * @return a composed predicate that represents the short-circuiting logical
     * AND of this predicate and the {@code other} predicate
     */
    default DispatchPredicate and(DispatchPredicate other) {
        Objects.requireNonNull(other);
        return (k, dl) -> test(k, dl) && other.test(k, dl);
    }

    /**
     * Returns a predicate that represents the logical negation of this
     * predicate.
     *
     * @return a predicate that represents the logical negation of this
     * predicate
     */
    default DispatchPredicate negate() {
        return (k, dl) -> !test(k, dl);
    }

    /**
     * Returns a composed predicate that represents a short-circuiting logical
     * OR of this predicate and another.
     *
     * @param other a predicate that will be logically-ORed with this
     *              predicate
     *
     * @return a composed predicate that represents the short-circuiting logical
     * OR of this predicate and the {@code other} predicate
     */
    default DispatchPredicate or(DispatchPredicate other) {
        Objects.requireNonNull(other);
        return (k, dl) -> test(k, dl) || other.test(k, dl);
    }

    /**
     * This predicate will return true if the {@link DataLoader} has not been dispatched
     * for at least the duration length of time.
     *
     * @param duration the length of time to check
     *
     * @return true if the data loader has not been dispatched in duration time
     */
    static DispatchPredicate dispatchIfLongerThan(Duration duration) {
        return (dataLoaderKey, dataLoader) -> {
            int i = dataLoader.getTimeSinceDispatch().compareTo(duration);
            return i > 0;
        };
    }

    /**
     * This predicate will return true if the {@link DataLoader#dispatchDepth()} is greater than the specified depth.
     *
     * This will act as minimum batch size.  There must be more than `depth` items queued for the predicate to return true.
     *
     * @param depth the value to be greater than
     *
     * @return true if the {@link DataLoader#dispatchDepth()} is greater than the specified depth.
     */
    static DispatchPredicate dispatchIfDepthGreaterThan(int depth) {
        return (dataLoaderKey, dataLoader) -> dataLoader.dispatchDepth() > depth;
    }
}
