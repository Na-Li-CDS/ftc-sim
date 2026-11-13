package org.firstinspires.ftc.robotcore.external.navigation;

import java.util.Locale;

import ftcsim.hal.Rotations;

/**
 * Instances of {@link Orientation} represent a rotated stance in three-dimensional space by way
 * of a set of three successive rotations.
 */
public class Orientation {
    public AxesReference axesReference;
    public AxesOrder axesOrder;
    public AngleUnit angleUnit;
    public float firstAngle;
    public float secondAngle;
    public float thirdAngle;
    public long acquisitionTime;

    public Orientation() {
        this(AxesReference.EXTRINSIC, AxesOrder.XYZ, AngleUnit.RADIANS, 0, 0, 0, 0);
    }

    public Orientation(AxesReference axesReference, AxesOrder axesOrder, AngleUnit angleUnit,
                       float firstAngle, float secondAngle, float thirdAngle, long acquisitionTime) {
        this.axesReference = axesReference;
        this.axesOrder = axesOrder;
        this.angleUnit = angleUnit;
        this.firstAngle = firstAngle;
        this.secondAngle = secondAngle;
        this.thirdAngle = thirdAngle;
        this.acquisitionTime = acquisitionTime;
    }

    public Orientation toAngleUnit(AngleUnit angleUnit) {
        if (angleUnit != this.angleUnit) {
            return new Orientation(this.axesReference, this.axesOrder, angleUnit,
                    angleUnit.fromUnit(this.angleUnit, firstAngle),
                    angleUnit.fromUnit(this.angleUnit, secondAngle),
                    angleUnit.fromUnit(this.angleUnit, thirdAngle),
                    this.acquisitionTime);
        }
        return this;
    }

    public Orientation toAxesReference(AxesReference axesReference) {
        if (this.axesReference != axesReference) {
            // Extrinsic (a, b, c) about (X, Y, Z) is the same rotation as intrinsic (c, b, a) about (Z, Y, X).
            return new Orientation(axesReference, this.axesOrder.reverse(), this.angleUnit,
                    this.thirdAngle, this.secondAngle, this.firstAngle, this.acquisitionTime);
        }
        return this;
    }

    public Orientation toAxesOrder(AxesOrder axesOrder) {
        if (this.axesOrder != axesOrder) {
            return toAxes(this.axesReference, axesOrder);
        }
        return this;
    }

    private Orientation toAxes(AxesReference reference, AxesOrder order) {
        double[][] m = rotationMatrix();
        double[] angles = Rotations.toEuler(m, reference, order);
        return new Orientation(reference, order, this.angleUnit,
                (float) this.angleUnit.fromRadians(angles[0]),
                (float) this.angleUnit.fromRadians(angles[1]),
                (float) this.angleUnit.fromRadians(angles[2]),
                this.acquisitionTime);
    }

    double[][] rotationMatrix() {
        return Rotations.fromEuler(axesReference, axesOrder,
                angleUnit.toRadians((double) firstAngle),
                angleUnit.toRadians((double) secondAngle),
                angleUnit.toRadians((double) thirdAngle));
    }

    @Override
    public String toString() {
        if (this.angleUnit == AngleUnit.DEGREES) {
            return String.format(Locale.getDefault(), "{%s %s %.0f %.0f %.0f}",
                    this.axesReference.toString(), this.axesOrder.toString(),
                    this.firstAngle, this.secondAngle, this.thirdAngle);
        }
        return String.format(Locale.getDefault(), "{%s %s %.3f %.3f %.3f}",
                this.axesReference.toString(), this.axesOrder.toString(),
                this.firstAngle, this.secondAngle, this.thirdAngle);
    }
}
