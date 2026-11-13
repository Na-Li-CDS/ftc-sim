package com.qualcomm.robotcore.hardware;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit;

/** DcMotorEx is an extended motor interface: velocity control, PIDF tuning and current sensing. */
public interface DcMotorEx extends DcMotor {

    void setMotorEnable();

    void setMotorDisable();

    boolean isMotorEnabled();

    /** Sets the velocity of the motor, in ticks per second. */
    void setVelocity(double angularRate);

    void setVelocity(double angularRate, AngleUnit unit);

    /** Returns the current velocity of the motor, in ticks per second. */
    double getVelocity();

    double getVelocity(AngleUnit unit);

    @Deprecated
    void setPIDCoefficients(RunMode mode, PIDCoefficients pidCoefficients);

    void setPIDFCoefficients(RunMode mode, PIDFCoefficients pidfCoefficients) throws UnsupportedOperationException;

    void setVelocityPIDFCoefficients(double p, double i, double d, double f);

    void setPositionPIDFCoefficients(double p);

    @Deprecated
    PIDCoefficients getPIDCoefficients(RunMode mode);

    PIDFCoefficients getPIDFCoefficients(RunMode mode);

    void setTargetPositionTolerance(int tolerance);

    int getTargetPositionTolerance();

    double getCurrent(CurrentUnit unit);

    double getCurrentAlert(CurrentUnit unit);

    void setCurrentAlert(double current, CurrentUnit unit);

    boolean isOverCurrent();
}
