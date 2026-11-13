package ftcsim.hal;

import java.util.concurrent.locks.LockSupport;

/**
 * Simulated bus latency. On a real Control Hub every motor/servo command and every un-cached
 * read is a USB/RS485 transaction that takes about a millisecond, and I2C sensor reads take
 * longer. Reproducing that keeps OpMode loop times (and therefore controller tuning) realistic.
 * Set both to 0 in the robot JSON ("io" section) to run as fast as possible.
 */
public final class Latency {
    public static volatile long commandNanos = 1000000L;
    public static volatile long i2cNanos = 2500000L;

    private Latency() {
    }

    public static void command() {
        spin(commandNanos);
    }

    public static void i2c() {
        spin(i2cNanos);
    }

    static void spin(long nanos) {
        if (nanos <= 0) return;
        long end = System.nanoTime() + nanos;
        if (nanos > 3000000L) {
            LockSupport.parkNanos(nanos - 1500000L);
        }
        while (System.nanoTime() < end) {
            Thread.yield();
        }
    }
}
