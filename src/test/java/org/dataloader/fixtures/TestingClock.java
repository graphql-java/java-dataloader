package org.dataloader.fixtures;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A mutable (but time fixed) clock that can jump forward or back in time
 */
public class TestingClock extends Clock {

    private Clock clock;

    public TestingClock() {
        clock = Clock.fixed(Instant.ofEpochMilli(0), ZoneId.systemDefault());
    }

    public Clock jump(int millisDelta) {
        clock = Clock.offset(clock, Duration.ofMillis(millisDelta));
        return clock;
    }

    @Override
    public ZoneId getZone() {
        return clock.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return clock.withZone(zone);
    }

    @Override
    public Instant instant() {
        return clock.instant();
    }
}
