package org.dataloader;

import org.dataloader.fixtures.TestingClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.dataloader.fixtures.TestKit.keysAsValues;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("UnusedReturnValue")
public class DataLoaderTimeTest {


    @Test
    public void should_set_and_instant_if_dispatched() {

        TestingClock clock = new TestingClock();
        DataLoader<Integer, Integer> dataLoader = new ClockDataLoader<>(keysAsValues(), clock);
        Instant then = clock.instant();

        long sinceMS = dataLoader.getTimeSinceDispatch().toMillis();
        assertThat(sinceMS, equalTo(0L));
        assertThat(then, equalTo(dataLoader.getLastDispatchTime()));

        then = clock.instant();
        clock.jump(1000);

        sinceMS = dataLoader.getTimeSinceDispatch().toMillis();
        assertThat(sinceMS, equalTo(1000L));
        assertThat(then, equalTo(dataLoader.getLastDispatchTime()));

        // dispatch and hence reset the time of last dispatch
        then = clock.instant();
        dataLoader.dispatch();

        sinceMS = dataLoader.getTimeSinceDispatch().toMillis();
        assertThat(sinceMS, equalTo(0L));
        assertThat(then, equalTo(dataLoader.getLastDispatchTime()));

    }


}
