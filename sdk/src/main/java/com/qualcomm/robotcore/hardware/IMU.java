package com.qualcomm.robotcore.hardware;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AngularVelocity;
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.robotcore.external.navigation.Orientation;
import org.firstinspires.ftc.robotcore.external.navigation.Quaternion;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;

/**
 * Universal interface for an Inertial Measurement Unit. Angles are reported in the robot's
 * coordinate system: +X to the right, +Y forward, +Z up; counter-clockwise rotation is positive.
 */
public interface IMU extends HardwareDevice {

    boolean initialize(Parameters parameters);

    void resetYaw();

    YawPitchRollAngles getRobotYawPitchRollAngles();

    Orientation getRobotOrientation(AxesReference reference, AxesOrder order, AngleUnit angleUnit);

    Quaternion getRobotOrientationAsQuaternion();

    AngularVelocity getRobotAngularVelocity(AngleUnit angleUnit);

    class Parameters {
        public ImuOrientationOnRobot imuOrientationOnRobot;

        public Parameters(ImuOrientationOnRobot imuOrientationOnRobot) {
            this.imuOrientationOnRobot = imuOrientationOnRobot;
        }

        public Parameters copy() {
            return new Parameters(imuOrientationOnRobot);
        }
    }
}
