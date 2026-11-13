package com.qualcomm.hardware.rev;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.robotcore.external.navigation.Orientation;
import org.firstinspires.ftc.robotcore.external.navigation.Quaternion;

import ftcsim.hal.Rotations;

/**
 * Describes how a REV Control Hub or Expansion Hub is mounted on the robot.
 * Robot coordinates: +X right, +Y forward, +Z up. The hub's +Z axis comes out of the logo face,
 * its +Y axis points toward the USB ports, so "logo UP, USB FORWARD" is the identity.
 */
public class RevHubOrientationOnRobot extends RevImuOrientationOnRobot {

    public enum LogoFacingDirection {UP, DOWN, FORWARD, BACKWARD, LEFT, RIGHT}

    public enum UsbFacingDirection {UP, DOWN, FORWARD, BACKWARD, LEFT, RIGHT}

    public RevHubOrientationOnRobot(LogoFacingDirection logoFacingDirection, UsbFacingDirection usbFacingDirection) {
        super(fromFacing(logoFacingDirection, usbFacingDirection));
    }

    public RevHubOrientationOnRobot(Orientation rotation) {
        super(fromOrientation(rotation));
    }

    public RevHubOrientationOnRobot(Quaternion rotation) {
        super(Rotations.fromQuaternion(rotation.w, rotation.x, rotation.y, rotation.z));
    }

    /** Hub orientation as intrinsic rotations about the robot's Z, then Y, then X axes (degrees). */
    public static Orientation zyxOrientation(double z, double y, double x) {
        return new Orientation(AxesReference.INTRINSIC, AxesOrder.ZYX, AngleUnit.DEGREES, (float) z, (float) y, (float) x, 0);
    }

    /** Hub orientation as intrinsic rotations about the robot's X, then Y, then Z axes (degrees). */
    public static Orientation xyzOrientation(double x, double y, double z) {
        return new Orientation(AxesReference.INTRINSIC, AxesOrder.XYZ, AngleUnit.DEGREES, (float) x, (float) y, (float) z, 0);
    }

    private static double[][] fromOrientation(Orientation o) {
        double[][] m = Rotations.fromEuler(o.axesReference, o.axesOrder,
                o.angleUnit.toRadians((double) o.firstAngle),
                o.angleUnit.toRadians((double) o.secondAngle),
                o.angleUnit.toRadians((double) o.thirdAngle));
        return m;
    }

    static double[] vector(String direction) {
        if ("UP".equals(direction)) return new double[]{0, 0, 1};
        if ("DOWN".equals(direction)) return new double[]{0, 0, -1};
        if ("FORWARD".equals(direction)) return new double[]{0, 1, 0};
        if ("BACKWARD".equals(direction)) return new double[]{0, -1, 0};
        if ("LEFT".equals(direction)) return new double[]{-1, 0, 0};
        return new double[]{1, 0, 0}; // RIGHT
    }

    private static double[][] fromFacing(LogoFacingDirection logo, UsbFacingDirection usb) {
        double[] z = vector(logo.name());
        double[] y = vector(usb.name());
        double dot = z[0] * y[0] + z[1] * y[1] + z[2] * y[2];
        if (Math.abs(dot) > 0.5) {
            throw new IllegalArgumentException("The specified REV Hub orientation is physically impossible "
                    + "(the logo and the USB ports cannot face the same or opposite directions)");
        }
        double[] x = {y[1] * z[2] - y[2] * z[1], y[2] * z[0] - y[0] * z[2], y[0] * z[1] - y[1] * z[0]};
        return new double[][]{
                {x[0], y[0], z[0]},
                {x[1], y[1], z[1]},
                {x[2], y[2], z[2]}};
    }
}
