package org.firstinspires.ftc.robotcore.external.navigation;

import java.util.Locale;

/** Instances of {@link AngularVelocity} represent an instantaneous body-referenced 3D rotation rate. */
public class AngularVelocity {
    public UnnormalizedAngleUnit angleUnit;
    public float xRotationRate;
    public float yRotationRate;
    public float zRotationRate;
    public long acquisitionTime;

    public AngularVelocity() {
        this(AngleUnit.DEGREES, 0, 0, 0, 0);
    }

    public AngularVelocity(UnnormalizedAngleUnit angleUnit, float xRotationRate, float yRotationRate,
                           float zRotationRate, long acquisitionTime) {
        this.angleUnit = angleUnit;
        this.xRotationRate = xRotationRate;
        this.yRotationRate = yRotationRate;
        this.zRotationRate = zRotationRate;
        this.acquisitionTime = acquisitionTime;
    }

    public AngularVelocity(AngleUnit angleUnit, float xRotationRate, float yRotationRate,
                           float zRotationRate, long acquisitionTime) {
        this(angleUnit.getUnnormalized(), xRotationRate, yRotationRate, zRotationRate, acquisitionTime);
    }

    public AngularVelocity toAngleUnit(AngleUnit unit) {
        UnnormalizedAngleUnit u = unit.getUnnormalized();
        if (u != this.angleUnit) {
            return new AngularVelocity(u,
                    u.fromUnit(this.angleUnit, xRotationRate),
                    u.fromUnit(this.angleUnit, yRotationRate),
                    u.fromUnit(this.angleUnit, zRotationRate),
                    this.acquisitionTime);
        }
        return this;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "{x=%.3f, y=%.3f, z=%.3f}", xRotationRate, yRotationRate, zRotationRate);
    }
}
