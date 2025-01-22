package org.dataloader.fixtures;

import java.time.Duration;

public class Stopwatch {

    public static Stopwatch stopwatchStarted() {
        return new Stopwatch().start();
    }

    public static Stopwatch stopwatchUnStarted() {
        return new Stopwatch();
    }

    private long started = -1;
    private long stopped = -1;

    public Stopwatch start() {
        synchronized (this) {
            if (started != -1) {
                throw new IllegalStateException("You have started it before");
            }
            started = System.currentTimeMillis();
        }
        return this;
    }

    private Stopwatch() {
    }

    public long elapsed() {
        synchronized (this) {
            if (started == -1) {
                throw new IllegalStateException("You haven't started it");
            }
            if (stopped == -1) {
                return System.currentTimeMillis() - started;
            } else {
                return stopped - started;
            }
        }
    }

    public Duration duration() {
        return Duration.ofMillis(elapsed());
    }

    public Duration stop() {
        synchronized (this) {
            if (started != -1) {
                throw new IllegalStateException("You have started it");
            }
            stopped = System.currentTimeMillis();
            return duration();
        }
    }
}
