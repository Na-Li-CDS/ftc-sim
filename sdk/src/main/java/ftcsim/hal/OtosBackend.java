package ftcsim.hal;

/**
 * The SparkFun Optical Tracking Odometry Sensor. It tracks its own motion over the floor and
 * reports the pose of the robot's tracking center using the offset robot code gives it.
 *
 * All values here are meters and radians in the OTOS frame: +X right, +Y forward, heading CCW.
 */
public final class OtosBackend {
    // configuration (robot code)
    private double offX = 0, offY = 0, offH = 0;
    private double linearScalar = 1.0, angularScalar = 1.0;

    // tracked sensor pose in the OTOS world frame
    private double sx = 0, sy = 0, sh = 0;
    private double vx = 0, vy = 0, vh = 0;
    private double ax = 0, ay = 0, ah = 0;

    /**
     * Physics step: sensor displacement in its own frame (meters, +X right, +Y forward),
     * rotation (rad), over dt seconds.
     */
    public synchronized void step(double dxSensor, double dySensor, double dTheta, double dt) {
        dxSensor *= linearScalar;
        dySensor *= linearScalar;
        dTheta *= angularScalar;
        double mid = sh + dTheta / 2;
        double c = Math.cos(mid), s = Math.sin(mid);
        double wx = dxSensor * c - dySensor * s;
        double wy = dxSensor * s + dySensor * c;
        sx += wx;
        sy += wy;
        sh += dTheta;
        if (dt > 0) {
            double nvx = wx / dt, nvy = wy / dt, nvh = dTheta / dt;
            double a = Math.min(1.0, dt / 0.01);
            double nax = (nvx - vx) / dt, nay = (nvy - vy) / dt, nah = (nvh - vh) / dt;
            ax += a * (nax - ax);
            ay += a * (nay - ay);
            ah += a * (nah - ah);
            vx += a * (nvx - vx);
            vy += a * (nvy - vy);
            vh += a * (nvh - vh);
        }
    }

    /** Robot pose {x, y, h} derived from the tracked sensor pose and the configured offset. */
    public synchronized double[] robotPose() {
        double rh = sh - offH;
        double c = Math.cos(rh), s = Math.sin(rh);
        return new double[]{sx - (offX * c - offY * s), sy - (offX * s + offY * c), wrap(rh)};
    }

    public synchronized double[] velocity() {
        return new double[]{vx, vy, vh};
    }

    public synchronized double[] acceleration() {
        return new double[]{ax, ay, ah};
    }

    public synchronized void setRobotPose(double x, double y, double h) {
        sh = h + offH;
        double c = Math.cos(h), s = Math.sin(h);
        sx = x + offX * c - offY * s;
        sy = y + offX * s + offY * c;
    }

    public synchronized void setOffset(double x, double y, double h) {
        double[] pose = robotPose();
        offX = x;
        offY = y;
        offH = h;
        setRobotPose(pose[0], pose[1], pose[2]);
    }

    public synchronized double[] offset() {
        return new double[]{offX, offY, offH};
    }

    public synchronized void resetTracking() {
        setRobotPose(0, 0, 0);
        vx = vy = vh = ax = ay = ah = 0;
    }

    public synchronized double getLinearScalar() {
        return linearScalar;
    }

    public synchronized void setLinearScalar(double v) {
        linearScalar = v;
    }

    public synchronized double getAngularScalar() {
        return angularScalar;
    }

    public synchronized void setAngularScalar(double v) {
        angularScalar = v;
    }

    public synchronized void powerCycle() {
        offX = offY = offH = 0;
        linearScalar = angularScalar = 1.0;
        sx = sy = sh = 0;
        vx = vy = vh = ax = ay = ah = 0;
    }

    private static double wrap(double a) {
        return Math.atan2(Math.sin(a), Math.cos(a));
    }
}
