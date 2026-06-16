package org.dataloader;

import org.awaitility.Duration;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Arrays.asList;
import static org.awaitility.Awaitility.await;
import static org.dataloader.DataLoaderFactory.newMappedPublisherDataLoader;
import static org.dataloader.DataLoaderFactory.newPublisherDataLoader;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Reproduces <a href="https://github.com/graphql-java/java-dataloader/issues/273">issue #273</a>.
 * <p>
 * A reactive publisher only emits while it has outstanding demand.  The reactive subscribers used
 * to request {@code keys.size()} exactly once and then wait for {@code onComplete}.  A publisher
 * that emits values lazily as more demand arrives - for example one that emits an unrelated value
 * before the one that actually matches a key - would therefore leave the data loader blocked
 * forever, because the matching value was never requested and the publisher never completed.
 */
public class ReactiveBackpressureTest {

    @Test
    public void mapped_loader_recovers_from_a_lazy_backpressured_publisher_that_never_completes() {
        List<Collection<String>> loadCalls = new ArrayList<>();

        DataLoader<String, String> loader = newMappedPublisherDataLoader((keys, subscriber) -> {
            loadCalls.add(new ArrayList<>(keys));

            List<Map.Entry<String, String>> items = new ArrayList<>();
            // an entry that does not match any requested key - the original code consumed the one
            // and only window of demand on this value and then blocked
            items.add(Map.entry("an-unrelated-key", "an-unrelated-value"));
            for (String key : keys) {
                items.add(Map.entry(key, "value-" + key));
            }
            // note: this publisher NEVER calls onComplete - it only emits as demand arrives
            new BackpressuredPublisher<>(items, false).subscribe(subscriber);
        });

        CompletableFuture<String> cfA = loader.load("a");
        CompletableFuture<String> cfB = loader.load("b");
        CompletableFuture<List<String>> dispatch = loader.dispatch();

        // before the fix the matching values were never re-requested and dispatch never completed
        await().atMost(Duration.FIVE_SECONDS).until(() -> cfA.isDone() && cfB.isDone() && dispatch.isDone());

        assertThat(cfA.join(), equalTo("value-a"));
        assertThat(cfB.join(), equalTo("value-b"));
        // we got everything we asked for so we completed early rather than waiting on the publisher
        assertThat(dispatch.join(), equalTo(asList("value-a", "value-b")));
        assertThat(loadCalls, equalTo(List.of(asList("a", "b"))));
    }

    @Test
    public void list_loader_recovers_from_a_lazy_backpressured_publisher_that_never_completes() {
        List<Collection<String>> loadCalls = new ArrayList<>();

        DataLoader<String, String> loader = newPublisherDataLoader((keys, subscriber) -> {
            loadCalls.add(new ArrayList<>(keys));

            List<String> items = new ArrayList<>();
            for (String key : keys) {
                items.add("value-" + key);
            }
            new BackpressuredPublisher<>(items, false).subscribe(subscriber);
        });

        CompletableFuture<String> cfA = loader.load("a");
        CompletableFuture<String> cfB = loader.load("b");
        CompletableFuture<List<String>> dispatch = loader.dispatch();

        await().atMost(Duration.FIVE_SECONDS).until(() -> cfA.isDone() && cfB.isDone() && dispatch.isDone());

        assertThat(cfA.join(), equalTo("value-a"));
        assertThat(cfB.join(), equalTo("value-b"));
        assertThat(dispatch.join(), equalTo(asList("value-a", "value-b")));
        assertThat(loadCalls, equalTo(List.of(asList("a", "b"))));
    }

    /**
     * A minimal reactive-streams {@link Publisher} that strictly honours backpressure: it only ever
     * emits an item when there is outstanding demand for it, and (optionally) never signals
     * {@code onComplete}.  It uses the standard work-in-progress drain loop so it behaves correctly
     * even when {@code request} is called re-entrantly from within {@code onNext}.
     */
    static final class BackpressuredPublisher<T> implements Publisher<T> {
        private final List<T> items;
        private final boolean completeWhenDrained;

        BackpressuredPublisher(List<T> items, boolean completeWhenDrained) {
            this.items = items;
            this.completeWhenDrained = completeWhenDrained;
        }

        @Override
        public void subscribe(Subscriber<? super T> subscriber) {
            subscriber.onSubscribe(new Subscription() {
                final AtomicLong demand = new AtomicLong();
                final AtomicInteger wip = new AtomicInteger();
                volatile boolean cancelled;
                int idx;
                boolean completed;

                @Override
                public void request(long n) {
                    if (n <= 0) {
                        return;
                    }
                    demand.addAndGet(n);
                    drain();
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }

                private void drain() {
                    if (wip.getAndIncrement() != 0) {
                        // a drain is already in progress (possibly our own re-entrant caller) - it will
                        // pick up the extra demand we just registered
                        return;
                    }
                    int missed = 1;
                    for (; ; ) {
                        while (!cancelled && demand.get() > 0 && idx < items.size()) {
                            T item = items.get(idx++);
                            demand.decrementAndGet();
                            subscriber.onNext(item);
                        }
                        if (!cancelled && completeWhenDrained && !completed && idx >= items.size()) {
                            completed = true;
                            subscriber.onComplete();
                        }
                        missed = wip.addAndGet(-missed);
                        if (missed == 0) {
                            break;
                        }
                    }
                }
            });
        }
    }
}
