package com.qualcomm.robotcore.hardware;

/** CRServo is the central interface supported by continuous rotation servos. */
public interface CRServo extends DcMotorSimple {

    ServoController getController();

    int getPortNumber();
}
