package com.qualcomm.robotcore.hardware;

/** Interface for working with Servo Controllers. Ports are 0-based. */
public interface ServoController extends HardwareDevice {

    enum PwmStatus {ENABLED, DISABLED, MIXED}

    void pwmEnable();

    void pwmDisable();

    PwmStatus getPwmStatus();

    void setServoPosition(int servo, double position);

    double getServoPosition(int servo);

    void forgetLastKnownPosition(int servo);
}
