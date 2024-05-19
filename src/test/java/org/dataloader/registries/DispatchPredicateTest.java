package org.dataloader.registries;

import org.dataloader.ClockDataLoader;
import org.dataloader.DataLoader;
import org.dataloader.fixtures.TestKit;
import org.dataloader.fixtures.TestingClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DispatchPredicateTest {

    @Test
    public void default_logical_method() {

        String key = "k";
        DataLoader<?, ?> testDL = TestKit.idLoader();

        DispatchPredicate alwaysTrue = (k, dl) -> true;
        DispatchPredicate alwaysFalse = (k, dl) -> false;

        assertFalse(alwaysFalse.and(alwaysFalse).test(key, testDL));
        assertFalse(alwaysFalse.and(alwaysTrue).test(key, testDL));
        assertFalse(alwaysTrue.and(alwaysFalse).test(key, testDL));
        assertTrue(alwaysTrue.and(alwaysTrue).test(key, testDL));

        assertTrue(alwaysFalse.negate().test(key, testDL));
        assertFalse(alwaysTrue.negate().test(key, testDL));

        assertTrue(alwaysTrue.or(alwaysFalse).test(key, testDL));
        assertTrue(alwaysFalse.or(alwaysTrue).test(key, testDL));
        assertFalse(alwaysFalse.or(alwaysFalse).test(key, testDL));
    }

    @Test
    public void dispatchIfLongerThan_test() {
        TestingClock clock = new TestingClock();
        ClockDataLoader<String, String> dlA = new ClockDataLoader<>(TestKit.keysAsValues(), clock);

        Duration ms200 = Duration.ofMillis(200);
        DispatchPredicate dispatchPredicate = DispatchPredicate.dispatchIfLongerThan(ms200);

        assertFalse(dispatchPredicate.test("k", dlA));

        clock.jump(199);
        assertFalse(dispatchPredicate.test("k", dlA));

        clock.jump(100);
        assertTrue(dispatchPredicate.test("k", dlA));
    }

    @Test
    public void dispatchIfDepthGreaterThan_test() {
        DataLoader<Object, Object> dlA = TestKit.idLoader();

        DispatchPredicate dispatchPredicate = DispatchPredicate.dispatchIfDepthGreaterThan(4);
        assertFalse(dispatchPredicate.test("k", dlA));

        dlA.load("1");
        dlA.load("2");
        dlA.load("3");
        dlA.load("4");

        assertFalse(dispatchPredicate.test("k", dlA));


        dlA.load("5");
        assertTrue(dispatchPredicate.test("k", dlA));

    }

    @Test
    public void combined_some_things() {

        TestingClock clock = new TestingClock();
        ClockDataLoader<String, String> dlA = new ClockDataLoader<>(TestKit.keysAsValues(), clock);

        Duration ms200 = Duration.ofMillis(200);

        DispatchPredicate dispatchIfLongerThan = DispatchPredicate.dispatchIfLongerThan(ms200);
        DispatchPredicate dispatchIfDepthGreaterThan = DispatchPredicate.dispatchIfDepthGreaterThan(4);
        DispatchPredicate combinedPredicate = dispatchIfLongerThan.and(dispatchIfDepthGreaterThan);

        assertFalse(combinedPredicate.test("k", dlA));

        clock.jump(500); // that's enough time for one condition

        assertFalse(combinedPredicate.test("k", dlA));

        dlA.load("1");
        dlA.load("2");
        dlA.load("3");
        dlA.load("4");

        assertFalse(combinedPredicate.test("k", dlA));


        dlA.load("5");
        assertTrue(combinedPredicate.test("k", dlA));

    }
}