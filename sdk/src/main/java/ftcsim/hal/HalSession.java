package ftcsim.hal;

/**
 * One OpMode run's access to the hardware. When the OpMode ends the session is closed, so a
 * runaway thread that ignores STOP can no longer drive the robot.
 */
public final class HalSession {
    private volatile boolean active = true;

    public boolean isActive() {
        return active;
    }

    public void close() {
        active = false;
    }
}
