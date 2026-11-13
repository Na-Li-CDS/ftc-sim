package org.firstinspires.ftc.robotcore.external.navigation;

import java.util.Locale;

/** Instances of {@link Velocity} represent the derivative of {@link Position} over time. */
public class Velocity {
    public DistanceUnit unit;
    public double xVeloc;
    public double yVeloc;
    public double zVeloc;
    public long acquisitionTime;

    public Velocity() {
        this(DistanceUnit.MM, 0, 0, 0, 0);
    }

    public Velocity(DistanceUnit unit, double xVeloc, double yVeloc, double zVeloc, long acquisitionTime) {
        this.unit = unit;
        this.xVeloc = xVeloc;
        this.yVeloc = yVeloc;
        this.zVeloc = zVeloc;
        this.acquisitionTime = acquisitionTime;
    }

    public Velocity toUnit(DistanceUnit distanceUnit) {
        if (distanceUnit != this.unit) {
            return new Velocity(distanceUnit,
                    distanceUnit.fromUnit(this.unit, xVeloc),
                    distanceUnit.fromUnit(this.unit, yVeloc),
                    distanceUnit.fromUnit(this.unit, zVeloc),
                    this.acquisitionTime);
        }
        return this;
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "(%.3f %.3f %.3f)%s/s", xVeloc, yVeloc, zVeloc, unit.toString());
    }
}
