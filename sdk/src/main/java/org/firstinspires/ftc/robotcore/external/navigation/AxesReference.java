package org.firstinspires.ftc.robotcore.external.navigation;

/**
 * {@link AxesReference} indicates whether we have intrinsic rotations, where the axes move with
 * the object that is rotating, or extrinsic rotations, where they remain fixed in the world.
 */
public enum AxesReference {
    EXTRINSIC, INTRINSIC;

    public AxesReference reverse() {
        switch (this) {
            default:
            case EXTRINSIC: return INTRINSIC;
            case INTRINSIC: return EXTRINSIC;
        }
    }
}
