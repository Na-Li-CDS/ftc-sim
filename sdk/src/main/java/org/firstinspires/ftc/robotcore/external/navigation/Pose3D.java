package org.firstinspires.ftc.robotcore.external.navigation;

/** Pose3D represents the position and orientation of an object in 3D space. */
public class Pose3D {
    protected final Position position;
    protected final YawPitchRollAngles orientation;

    public Pose3D(Position position, YawPitchRollAngles orientation) {
        this.position = position;
        this.orientation = orientation;
    }

    @Override
    public String toString() {
        return "position=" + position.toString() + " orientation=" + orientation.toString();
    }

    public YawPitchRollAngles getOrientation() {
        return orientation;
    }

    public Position getPosition() {
        return position;
    }
}
