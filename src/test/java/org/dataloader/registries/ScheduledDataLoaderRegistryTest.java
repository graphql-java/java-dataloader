package org.dataloader.registries;

import junit.framework.TestCase;
import org.awaitility.core.ConditionTimeoutException;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.fixtures.TestKit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.TWO_SECONDS;
import static org.dataloader.fixtures.TestKit.keysAsValues;
import static org.dataloader.fixtures.TestKit.snooze;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ScheduledDataLoaderRegistryTest extends TestCase {

    DispatchPredicate alwaysDispatch = (key, dl) -> true;
    DispatchPredicate neverDispatch = (key, dl) -> false;


    public void test_basic_setup_works_like_a_normal_dlr() {

        List<List<String>> aCalls = new ArrayList<>();
        List<List<String>> bCalls = new ArrayList<>();

        DataLoader<String, String> dlA = TestKit.idLoader(aCalls);
        dlA.load("AK1");
        dlA.load("AK2");

        DataLoader<String, String> dlB = TestKit.idLoader(bCalls);
        dlB.load("BK1");
        dlB.load("BK2");

        DataLoaderRegistry otherDLR = DataLoaderRegistry.newRegistry().register("b", dlB).build();

        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry.newScheduledRegistry()
                .register("a", dlA)
                .registerAll(otherDLR)
                .dispatchPredicate(alwaysDispatch)
                .scheduledExecutorService(Executors.newSingleThreadScheduledExecutor())
                .schedule(Duration.ofMillis(100))
                .build();

        assertThat(registry.getScheduleDuration(), equalTo(Duration.ofMillis(100)));

        int count = registry.dispatchAllWithCount();
        assertThat(count, equalTo(4));
        assertThat(aCalls, equalTo(singletonList(asList("AK1", "AK2"))));
        assertThat(bCalls, equalTo(singletonList(asList("BK1", "BK2"))));
    }

    public void test_predicate_always_false() {

        List<List<String>> calls = new ArrayList<>();
        DataLoader<String, String> dlA = DataLoaderFactory.newDataLoader(keysAsValues(calls));
        dlA.load("K1");
        dlA.load("K2");

        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry.newScheduledRegistry()
                .register("a", dlA)
                .dispatchPredicate(neverDispatch)
                .schedule(Duration.ofMillis(10))
                .build();

        int count = registry.dispatchAllWithCount();
        assertThat(count, equalTo(0));
        assertThat(calls.size(), equalTo(0));

        snooze(200);

        count = registry.dispatchAllWithCount();
        assertThat(count, equalTo(0));
        assertThat(calls.size(), equalTo(0));

        snooze(200);
        count = registry.dispatchAllWithCount();
        assertThat(count, equalTo(0));
        assertThat(calls.size(), equalTo(0));
    }

    public void test_predicate_that_eventually_returns_true() {


        AtomicInteger counter = new AtomicInteger();
        DispatchPredicate neverDispatch = (key, dl) -> counter.incrementAndGet() > 5;

        List<List<String>> calls = new ArrayList<>();
        DataLoader<String, String> dlA = DataLoaderFactory.newDataLoader(keysAsValues(calls));
        CompletableFuture<String> p1 = dlA.load("K1");
        CompletableFuture<String> p2 = dlA.load("K2");

        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry.newScheduledRegistry()
                .register("a", dlA)
                .dispatchPredicate(neverDispatch)
                .schedule(Duration.ofMillis(10))
                .build();


        int count = registry.dispatchAllWithCount();
        assertThat(count, equalTo(0));
        assertThat(calls.size(), equalTo(0));
        assertFalse(p1.isDone());
        assertFalse(p2.isDone());

        snooze(200);

        registry.dispatchAll();
        assertTrue(p1.isDone());
        assertTrue(p2.isDone());
    }

    public void test_dispatchAllWithCountImmediately() {
        List<List<String>> calls = new ArrayList<>();
        DataLoader<String, String> dlA = DataLoaderFactory.newDataLoader(keysAsValues(calls));
        dlA.load("K1");
        dlA.load("K2");

        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry.newScheduledRegistry()
                .register("a", dlA)
                .dispatchPredicate(neverDispatch)
                .schedule(Duration.ofMillis(10))
                .build();

        int count = registry.dispatchAllWithCountImmediately();
        assertThat(count, equalTo(2));
        assertThat(calls, equalTo(singletonList(asList("K1", "K2"))));
    }

    public void test_dispatchAllImmediately() {
        List<List<String>> calls = new ArrayList<>();
        DataLoader<String, String> dlA = DataLoaderFactory.newDataLoader(keysAsValues(calls));
        dlA.load("K1");
        dlA.load("K2");

        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry.newScheduledRegistry()
                .register("a", dlA)
                .dispatchPredicate(neverDispatch)
                .schedule(Duration.ofMillis(10))
                .build();

        registry.dispatchAllImmediately();
        assertThat(calls, equalTo(singletonList(asList("K1", "K2"))));
    }

    public void test_rescheduleNow() {
        AtomicInteger i = new AtomicInteger();
        DispatchPredicate countingPredicate = (dataLoaderKey, dataLoader) -> i.incrementAndGet() > 5;

        List<List<String>> calls = new ArrayList<>();
        DataLoader<String, String> dlA = DataLoaderFactory.newDataLoader(keysAsValues(calls));
        dlA.load("K1");
        dlA.load("K2");

        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry.newScheduledRegistry()
                .register("a", dlA)
                .dispatchPredicate(countingPredicate)
                .schedule(Duration.ofMillis(100))
                .build();

        // we never called dispatch per say - we started the scheduling direct
        registry.rescheduleNow();
        assertTrue(calls.isEmpty());

        snooze(2000);
        assertThat(calls, equalTo(singletonList(asList("K1", "K2"))));
    }

    public void test_it_will_take_out_the_schedule_once_it_dispatches() {
        AtomicInteger counter = new AtomicInteger();
        DispatchPredicate countingPredicate = (dataLoaderKey, dataLoader) -> counter.incrementAndGet() > 5;

        List<List<String>> calls = new ArrayList<>();
        DataLoader<String, String> dlA = DataLoaderFactory.newDataLoader(keysAsValues(calls));
        dlA.load("K1");
        dlA.load("K2");

        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry.newScheduledRegistry()
                .register("a", dlA)
                .dispatchPredicate(countingPredicate)
                .schedule(Duration.ofMillis(100))
                .build();

        registry.dispatchAll();
        // we have 5 * 100 mills to reach this line
        assertTrue(calls.isEmpty());

        snooze(2000);
        assertThat(calls, equalTo(singletonList(asList("K1", "K2"))));

        // reset our counter state
        counter.set(0);

        dlA.load("K3");
        dlA.load("K4");

        // no one has called dispatch - there is no rescheduling
        snooze(2000);
        assertThat(calls, equalTo(singletonList(asList("K1", "K2"))));

        registry.dispatchAll();
        // we have 5 * 100 mills to reach this line
        assertThat(calls, equalTo(singletonList(asList("K1", "K2"))));

        snooze(2000);

        assertThat(calls, equalTo(asList(asList("K1", "K2"), asList("K3", "K4"))));
    }

    public void test_close_is_a_one_way_door() {
        AtomicInteger counter = new AtomicInteger();
        DispatchPredicate countingPredicate = (dataLoaderKey, dataLoader) -> {
            counter.incrementAndGet();
            return false;
        };

        DataLoader<String, String> dlA = TestKit.idLoader();
        dlA.load("K1");
        dlA.load("K2");

        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry.newScheduledRegistry()
                .register("a", dlA)
                .dispatchPredicate(countingPredicate)
                .schedule(Duration.ofMillis(10))
                .build();

        registry.rescheduleNow();

        snooze(200);

        assertTrue(counter.get() > 0);

        registry.close();

        snooze(100);
        int countThen = counter.get();

        registry.rescheduleNow();
        snooze(200);
        assertEquals(counter.get(), countThen);

        registry.rescheduleNow();
        snooze(200);
        assertEquals(counter.get(), countThen);

        registry.dispatchAll();
        snooze(200);
        assertEquals(counter.get(), countThen + 1); // will have re-entered

        snooze(200);
        assertEquals(counter.get(), countThen + 1);
    }

    public void test_can_tick_after_first_dispatch_for_chain_data_loaders() {

        // delays much bigger than the tick rate will mean multiple calls to dispatch
        DataLoader<String, String> dlA = TestKit.idLoaderAsync(Duration.ofMillis(100));
        DataLoader<String, String> dlB = TestKit.idLoaderAsync(Duration.ofMillis(200));

        CompletableFuture<String> chainedCF = dlA.load("AK1").thenCompose(dlB::load);

        AtomicBoolean done = new AtomicBoolean();
        chainedCF.whenComplete((v, t) -> done.set(true));

        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry.newScheduledRegistry()
                .register("a", dlA)
                .register("b", dlB)
                .dispatchPredicate(alwaysDispatch)
                .schedule(Duration.ofMillis(10))
                .tickerMode(true)
                .build();

        assertThat(registry.isTickerMode(), equalTo(true));

        int count = registry.dispatchAllWithCount();
        assertThat(count,equalTo(1));

        await().atMost(TWO_SECONDS).untilAtomic(done, is(true));

        registry.close();
    }

    public void test_chain_data_loaders_will_hang_if_not_in_ticker_mode() {

        // delays much bigger than the tick rate will mean multiple calls to dispatch
        DataLoader<String, String> dlA = TestKit.idLoaderAsync(Duration.ofMillis(100));
        DataLoader<String, String> dlB = TestKit.idLoaderAsync(Duration.ofMillis(200));

        CompletableFuture<String> chainedCF = dlA.load("AK1").thenCompose(dlB::load);

        AtomicBoolean done = new AtomicBoolean();
        chainedCF.whenComplete((v, t) -> done.set(true));

        ScheduledDataLoaderRegistry registry = ScheduledDataLoaderRegistry.newScheduledRegistry()
                .register("a", dlA)
                .register("b", dlB)
                .dispatchPredicate(alwaysDispatch)
                .schedule(Duration.ofMillis(10))
                .tickerMode(false)
                .build();

        assertThat(registry.isTickerMode(), equalTo(false));

        int count = registry.dispatchAllWithCount();
        assertThat(count,equalTo(1));

        try {
            await().atMost(TWO_SECONDS).untilAtomic(done, is(true));
            fail("This should not have completed but rather timed out");
        } catch (ConditionTimeoutException expected) {
        }
        registry.close();
    }
}