package com.qualcomm.robotcore.util;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Measure elapsed time. Does not measure deltas between calls; use {@link #reset()} to restart. */
public class ElapsedTime {

    public enum Resolution {
        SECONDS,
        MILLISECONDS
    }

    public static final long SECOND_IN_NANO = 1000000000;
    public static final long MILLIS_IN_NANO = 1000000;

    protected volatile long nsStartTime;
    protected final double resolution;

    public ElapsedTime() {
        reset();
        this.resolution = SECOND_IN_NANO;
    }

    public ElapsedTime(long startTime) {
        this.nsStartTime = startTime;
        this.resolution = SECOND_IN_NANO;
    }

    public ElapsedTime(Resolution resolution) {
        reset();
        switch (resolution) {
            case MILLISECONDS:
                this.resolution = MILLIS_IN_NANO;
                break;
            case SECONDS:
            default:
                this.resolution = SECOND_IN_NANO;
                break;
        }
    }

    protected long nsNow() {
        return System.nanoTime();
    }

    public long now(TimeUnit unit) {
        return unit.convert(nsNow(), TimeUnit.NANOSECONDS);
    }

    public void reset() {
        nsStartTime = nsNow();
    }

    /** Returns the time at which the timer was last reset, in units of resolution. */
    public double startTime() {
        return nsStartTime / resolution;
    }

    public long startTimeNanoseconds() {
        return this.nsStartTime;
    }

    /** Returns the duration since the timer was last reset, in units of resolution. */
    public double time() {
        return (nsNow() - nsStartTime) / resolution;
    }

    public long time(TimeUnit unit) {
        return unit.convert(nanoseconds(), TimeUnit.NANOSECONDS);
    }

    public double seconds() {
        return nanoseconds() / ((double) SECOND_IN_NANO);
    }

    public double milliseconds() {
        return seconds() * 1000;
    }

    public long nanoseconds() {
        return (nsNow() - nsStartTime);
    }

    public Resolution getResolution() {
        if (this.resolution == MILLIS_IN_NANO) return Resolution.MILLISECONDS;
        return Resolution.SECONDS;
    }

    private String resolutionStr() {
        if (resolution == SECOND_IN_NANO) return "seconds";
        if (resolution == MILLIS_IN_NANO) return "milliseconds";
        return "unknown units";
    }

    public void log(String label) {
        RobotLog.v(String.format(Locale.US, "TIMER: %20s - %1.3f %s", label, time(), resolutionStr()));
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%1.4f %s", time(), resolutionStr());
    }
}
