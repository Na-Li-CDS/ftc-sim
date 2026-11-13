package org.firstinspires.ftc.robotcore.external.navigation;

import java.util.Locale;

import ftcsim.hal.Rotations;

/** A {@link Quaternion} can indicate an orientation in three-space without the trouble of possible gimbal-lock. */
public class Quaternion {
    public float w;
    public float x;
    public float y;
    public float z;
    public long acquisitionTime;

    public static Quaternion identityQuaternion() {
        return new Quaternion(1.0f, 0.0f, 0.0f, 0.0f, 0);
    }

    public Quaternion() {
        this(0, 0, 0, 0, 0);
    }

    public Quaternion(float w, float x, float y, float z, long acquisitionTime) {
        this.w = w;
        this.x = x;
        this.y = y;
        this.z = z;
        this.acquisitionTime = acquisitionTime;
    }

    public float magnitude() {
        return (float) Math.sqrt(w * w + x * x + y * y + z * z);
    }

    public Quaternion normalized() {
        float mag = this.magnitude();
        return new Quaternion(w / mag, x / mag, y / mag, z / mag, this.acquisitionTime);
    }

    public Quaternion conjugate() {
        return new Quaternion(w, -x, -y, -z, this.acquisitionTime);
    }

    @Deprecated
    public Quaternion congugate() {
        return conjugate();
    }

    public Quaternion inverse() {
        return normalized().conjugate();
    }

    public Quaternion multiply(Quaternion q, long acquisitionTime) {
        return new Quaternion(
                this.w * q.w - this.x * q.x - this.y * q.y - this.z * q.z,
                this.w * q.x + this.x * q.w + this.y * q.z - this.z * q.y,
                this.w * q.y - this.x * q.z + this.y * q.w + this.z * q.x,
                this.w * q.z + this.x * q.y - this.y * q.x + this.z * q.w,
                acquisitionTime);
    }

    public Orientation toOrientation(AxesReference axesReference, AxesOrder axesOrder, AngleUnit angleUnit) {
        double[][] m = Rotations.fromQuaternion(w, x, y, z);
        return Rotations.toOrientation(m, axesReference, axesOrder, angleUnit, this.acquisitionTime);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "{w=%.3f, x=%.3f, y=%.3f, z=%.3f}", w, x, y, z);
    }
}
