package ftcsim.hal;

import com.qualcomm.robotcore.hardware.PwmControl;

/** A servo port on a hub. Robot code writes the commanded position; physics moves the horn. */
public final class ServoPort {
    public final String name;
    public final SimHub hub;
    public final int port;
    /** True for a continuous-rotation servo, false for a positional servo. */
    public final boolean continuous;

    /** Last commanded position in [0, 1]; NaN until robot code commands one. */
    public volatile double position = Double.NaN;
    /** A servo is limp until it receives its first command, as on the robot. */
    public volatile boolean pwmEnabled = false;
    public volatile PwmControl.PwmRange pwmRange = PwmControl.PwmRange.defaultRange;

    /** Horn angle (positional, degrees from center) or accumulated angle (continuous), from physics. */
    public volatile double angleDeg = 0;
    /** Angular speed in degrees per second, from physics. */
    public volatile double speedDegPerSec = 0;

    public ServoPort(String name, SimHub hub, int port, boolean continuous) {
        this.name = name;
        this.hub = hub;
        this.port = port;
        this.continuous = continuous;
    }

    /** The pulse width the hub sends, in microseconds (0 when disabled). */
    public double pulseWidthUs() {
        if (!pwmEnabled || Double.isNaN(position)) return 0;
        PwmControl.PwmRange r = pwmRange;
        return r.usPulseLower + position * (r.usPulseUpper - r.usPulseLower);
    }

    public void powerCycle() {
        position = Double.NaN;
        pwmEnabled = false;
        pwmRange = PwmControl.PwmRange.defaultRange;
    }
}
