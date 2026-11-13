package ftcsim.hal;

/**
 * The goBILDA Pinpoint co-processor: it reads two dead-wheel pods and its own IMU and integrates
 * the robot pose about 1500 times a second, using whatever pod offsets, resolution and
 * directions robot code configured. Physics feeds it the true pod travel; misconfiguration
 * therefore produces the same errors as on the robot.
 *
 * Pinpoint frame: +X forward, +Y left, heading counter-clockwise.
 */
public final class PinpointBackend {
    public static final double SWINGARM_TICKS_PER_MM = 13.26291192;
    public static final double FOUR_BAR_TICKS_PER_MM = 19.89436789;

    /** Physical pods (from the robot JSON). */
    private final double physTicksPerMmX;
    private final double physTicksPerMmY;

    // configuration written by robot code (persists until power cycle, like the device's registers)
    private double ticksPerMm = FOUR_BAR_TICKS_PER_MM;
    private double xOffsetMm = 0;
    private double yOffsetMm = 0;
    private double yawScalar = 1.0;
    private boolean xReversed = false;
    private boolean yReversed = false;

    // device state
    private double encX = 0, encY = 0;
    private double posX = 0, posY = 0, heading = 0;
    private double velX = 0, velY = 0, velH = 0;
    private long calibratingUntil = 0;

    public PinpointBackend(double physTicksPerMmX, double physTicksPerMmY) {
        this.physTicksPerMmX = physTicksPerMmX;
        this.physTicksPerMmY = physTicksPerMmY;
    }

    /**
     * Physics step: forward travel of the X pod and leftward travel of the Y pod (mm), and the
     * robot's true heading change (rad) over dt seconds.
     */
    public synchronized void step(double xPodMm, double yPodMm, double dHeading, double dt) {
        double dTicksX = xPodMm * physTicksPerMmX;
        double dTicksY = yPodMm * physTicksPerMmY;
        encX += dTicksX;
        encY += dTicksY;
        if (System.nanoTime() < calibratingUntil) {
            velX = velY = velH = 0;
            return;
        }
        double dX = dTicksX / ticksPerMm * (xReversed ? -1 : 1);
        double dY = dTicksY / ticksPerMm * (yReversed ? -1 : 1);
        double dTheta = dHeading * yawScalar;
        double dxc = dX + xOffsetMm * dTheta;
        double dyc = dY - yOffsetMm * dTheta;
        double mid = heading + dTheta / 2;
        double c = Math.cos(mid), s = Math.sin(mid);
        double fx = dxc * c - dyc * s;
        double fy = dxc * s + dyc * c;
        posX += fx;
        posY += fy;
        heading += dTheta;
        if (dt > 0) {
            double a = Math.min(1.0, dt / 0.01);  // light filtering, as the device does
            velX += a * (fx / dt - velX);
            velY += a * (fy / dt - velY);
            velH += a * (dTheta / dt - velH);
        }
    }

    public static final class Snapshot {
        public final int encoderX, encoderY;
        public final double xMm, yMm, headingRad, velXMmPerSec, velYMmPerSec, velHeadingRadPerSec;
        public final boolean calibrating;
        public final double xOffsetMm, yOffsetMm, yawScalar;

        Snapshot(PinpointBackend p) {
            encoderX = (int) Math.round(p.encX);
            encoderY = (int) Math.round(p.encY);
            xMm = p.posX;
            yMm = p.posY;
            headingRad = p.heading;
            velXMmPerSec = p.velX;
            velYMmPerSec = p.velY;
            velHeadingRadPerSec = p.velH;
            calibrating = System.nanoTime() < p.calibratingUntil;
            xOffsetMm = p.xOffsetMm;
            yOffsetMm = p.yOffsetMm;
            yawScalar = p.yawScalar;
        }
    }

    public synchronized Snapshot read() {
        return new Snapshot(this);
    }

    public synchronized void setOffsets(double xOffsetMm, double yOffsetMm) {
        this.xOffsetMm = xOffsetMm;
        this.yOffsetMm = yOffsetMm;
    }

    public synchronized void setTicksPerMm(double ticksPerMm) {
        this.ticksPerMm = ticksPerMm;
    }

    public synchronized void setDirections(boolean xReversed, boolean yReversed) {
        this.xReversed = xReversed;
        this.yReversed = yReversed;
    }

    public synchronized void setYawScalar(double yawScalar) {
        this.yawScalar = yawScalar;
    }

    public synchronized void setPose(double xMm, double yMm, double headingRad) {
        posX = xMm;
        posY = yMm;
        heading = headingRad;
    }

    public synchronized void setX(double xMm) {
        posX = xMm;
    }

    public synchronized void setY(double yMm) {
        posY = yMm;
    }

    public synchronized void setHeading(double headingRad) {
        heading = headingRad;
    }

    public synchronized void resetPosAndImu() {
        posX = posY = heading = 0;
        velX = velY = velH = 0;
        calibratingUntil = System.nanoTime() + 250000000L;
    }

    public synchronized void recalibrateImu() {
        calibratingUntil = System.nanoTime() + 250000000L;
    }

    public synchronized void powerCycle() {
        ticksPerMm = FOUR_BAR_TICKS_PER_MM;
        xOffsetMm = yOffsetMm = 0;
        yawScalar = 1.0;
        xReversed = yReversed = false;
        encX = encY = 0;
        posX = posY = heading = 0;
        velX = velY = velH = 0;
        calibratingUntil = 0;
    }
}
