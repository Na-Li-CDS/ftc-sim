package com.qualcomm.hardware.rev;

import org.firstinspires.ftc.robotcore.external.navigation.Quaternion;

import com.qualcomm.robotcore.hardware.ImuOrientationOnRobot;

import ftcsim.hal.Rotations;

/** Base class for describing how a REV hub's IMU is mounted on the robot. */
public abstract class RevImuOrientationOnRobot implements ImuOrientationOnRobot {
    private final Quaternion orientation;

    /** {@code hubToRobot}: rotation matrix whose columns are the hub's X, Y, Z axes in robot coordinates. */
    RevImuOrientationOnRobot(double[][] hubToRobot) {
        double[] q = Rotations.toQuaternion(hubToRobot);
        this.orientation = new Quaternion((float) q[0], (float) q[1], (float) q[2], (float) q[3], 0);
    }

    @Override
    public Quaternion imuCoordinateSystemOrientationFromPerspectiveOfRobot() {
        return new Quaternion(orientation.w, orientation.x, orientation.y, orientation.z, 0);
    }

    @Override
    public Quaternion imuRotationOffset() {
        return orientation.inverse();
    }

    @Override
    public Quaternion angularVelocityTransform() {
        return imuCoordinateSystemOrientationFromPerspectiveOfRobot();
    }
}
