package ftcsim.hal;

/** Simple sensor values written by physics and read by device drivers. */
public final class SensorBackends {
    private SensorBackends() {
    }

    /** A distance reading in meters; {@link Double#MAX_VALUE} when nothing is in range. */
    public static final class Range {
        public volatile double meters = Double.MAX_VALUE;
    }

    /** What a color sensor sees, as normalized values at gain 1 with its LED on. */
    public static final class Color {
        public volatile float red = 0, green = 0, blue = 0, alpha = 0;
        public volatile double distanceMeters = Double.MAX_VALUE;
        public volatile boolean ledOn = true;
    }
}
