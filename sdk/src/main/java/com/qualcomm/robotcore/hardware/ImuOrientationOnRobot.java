package com.qualcomm.robotcore.hardware;

import org.firstinspires.ftc.robotcore.external.navigation.Quaternion;

/** Describes how an IMU is mounted on the robot. */
public interface ImuOrientationOnRobot {
    /** Orientation of the IMU's coordinate system, expressed in the robot's coordinate system. */
    Quaternion imuCoordinateSystemOrientationFromPerspectiveOfRobot();

    Quaternion imuRotationOffset();

    Quaternion angularVelocityTransform();
}
