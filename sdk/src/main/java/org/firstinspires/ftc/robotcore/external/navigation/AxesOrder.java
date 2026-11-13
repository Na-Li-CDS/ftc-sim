package org.firstinspires.ftc.robotcore.external.navigation;

/**
 * {@link AxesOrder} indicates the chronological order of axes about which the three rotations
 * of an {@link Orientation} take place.
 */
public enum AxesOrder {
    XZX(new int[]{0, 2, 0}), XYX(new int[]{0, 1, 0}), YXY(new int[]{1, 0, 1}),
    YZY(new int[]{1, 2, 1}), ZYZ(new int[]{2, 1, 2}), ZXZ(new int[]{2, 0, 2}),
    XZY(new int[]{0, 2, 1}), XYZ(new int[]{0, 1, 2}), YXZ(new int[]{1, 0, 2}),
    YZX(new int[]{1, 2, 0}), ZYX(new int[]{2, 1, 0}), ZXY(new int[]{2, 0, 1});

    private final int[] indices;

    AxesOrder(int[] indices) {
        this.indices = indices;
    }

    /** Returns the numerical axes indices associated with this {@link AxesOrder}. */
    public int[] indices() {
        return this.indices.clone();
    }

    public Axis[] axes() {
        Axis[] result = new Axis[3];
        result[0] = Axis.fromIndex(this.indices[0]);
        result[1] = Axis.fromIndex(this.indices[1]);
        result[2] = Axis.fromIndex(this.indices[2]);
        return result;
    }

    /** Returns the {@link AxesOrder} which is the chronological reverse of the receiver. */
    public AxesOrder reverse() {
        switch (this) {
            default:
            case XZX: return XZX;
            case XYX: return XYX;
            case YXY: return YXY;
            case YZY: return YZY;
            case ZYZ: return ZYZ;
            case ZXZ: return ZXZ;
            case XZY: return YZX;
            case XYZ: return ZYX;
            case YXZ: return ZXY;
            case YZX: return XZY;
            case ZYX: return XYZ;
            case ZXY: return YXZ;
        }
    }
}
