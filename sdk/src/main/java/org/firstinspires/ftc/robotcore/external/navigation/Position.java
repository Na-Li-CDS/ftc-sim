package org.firstinspires.ftc.robotcore.external.navigation;

import java.util.Locale;

/** Instances of {@link Position} represent a three-dimensional distance in a particular distance unit. */
public class Position {
    public DistanceUnit unit;
    public double x;
    public double y;
    public double z;
    public long acquisitionTime;

    public Position() {
        this(DistanceUnit.MM, 0, 0, 0, 0);
    }

    public Position(DistanceUnit unit, double x, double y, double z, long acquisitionTime) {
        this.unit = unit;
        this.x = x;
        this.y = y;
        this.z = z;
        this.acquisitionTime = acquisitionTime;
    }

    public Position toUnit(DistanceUnit distanceUnit) {
        if (distanceUnit != this.unit) {
            return new Position(distanceUnit,
                    distanceUnit.fromUnit(this.unit, x),
                    distanceUnit.fromUnit(this.unit, y),
                    distanceUnit.fromUnit(this.unit, z),
                    this.acquisitionTime);
        }
        return this;
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "(%.3f %.3f %.3f)%s", x, y, z, unit.toString());
    }
}
