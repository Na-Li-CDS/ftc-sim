package org.firstinspires.ftc.robotcore.external.navigation;

import java.util.Locale;

/**
 * Yaw, pitch and roll of a robot: yaw is about the robot's Z axis (up), pitch about X (right)
 * and roll about Y (forward), applied intrinsically in Z, X, Y order.
 */
public class YawPitchRollAngles {
    private final AngleUnit angleUnit;
    private final double yaw;
    private final double pitch;
    private final double roll;
    private final long acquisitionTime;

    public YawPitchRollAngles(AngleUnit angleUnit, double yaw, double pitch, double roll, long acquisitionTime) {
        this.angleUnit = angleUnit;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.acquisitionTime = acquisitionTime;
    }

    /** Yaw in the angle unit these angles were created with (degrees for the IMU). */
    public double getYaw() {
        return yaw;
    }

    public double getYaw(AngleUnit angleUnit) {
        return angleUnit.fromUnit(this.angleUnit, yaw);
    }

    public double getPitch() {
        return pitch;
    }

    public double getPitch(AngleUnit angleUnit) {
        return angleUnit.fromUnit(this.angleUnit, pitch);
    }

    public double getRoll() {
        return roll;
    }

    public double getRoll(AngleUnit angleUnit) {
        return angleUnit.fromUnit(this.angleUnit, roll);
    }

    /** System.nanoTime() at which the angles were read. */
    public long getAcquisitionTime() {
        return acquisitionTime;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "{yaw=%.3f, pitch=%.3f, roll=%.3f}",
                getYaw(AngleUnit.DEGREES), getPitch(AngleUnit.DEGREES), getRoll(AngleUnit.DEGREES));
    }
}
