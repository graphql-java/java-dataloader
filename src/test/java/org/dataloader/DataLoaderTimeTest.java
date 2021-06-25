package org.dataloader;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

@SuppressWarnings("UnusedReturnValue")
public class DataLoaderTimeTest {

    private <T> BatchLoader<T, T> keysAsValues() {
        return CompletableFuture::completedFuture;
    }

    AtomicReference<Clock> clockRef = new AtomicReference<>();

    @Test
    public void should_set_and_instant_if_dispatched() {
        Clock clock = zeroEpoch();
        clockRef.set(clock);

        Instant startInstant = now();

        DataLoader<Integer, Integer> dataLoader = new DataLoader<Integer, Integer>(keysAsValues()) {
            @Override
            Clock clock() {
                return clockRef.get();
            }
        };

        long sinceMS = msSince(dataLoader.getLastDispatchTime());
        assertThat(sinceMS, equalTo(0L));
        assertThat(startInstant, equalTo(dataLoader.getLastDispatchTime()));

        jump(clock, 1000);
        dataLoader.dispatch();

        sinceMS = msSince(dataLoader.getLastDispatchTime());
        assertThat(sinceMS, equalTo(1000L));
    }

    private long msSince(Instant lastDispatchTime) {
        return Duration.between(lastDispatchTime, now()).toMillis();
    }

    private Instant now() {
        return Instant.now(clockRef.get());
    }

    private Clock jump(Clock clock, int millis) {
        clock = Clock.offset(clock, Duration.ofMillis(millis));
        clockRef.set(clock);
        return clock;
    }

    private Clock zeroEpoch() {
        return Clock.fixed(Instant.ofEpochMilli(0), ZoneId.systemDefault());
    }

}
