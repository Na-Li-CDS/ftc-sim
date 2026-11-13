package com.qualcomm.robotcore.hardware;

/** DcMotorSimple provides a minimal interface to a DC motor (or a continuous-rotation servo). */
public interface DcMotorSimple extends HardwareDevice {

    enum Direction {
        FORWARD, REVERSE;

        public Direction inverted() {
            return this == FORWARD ? REVERSE : FORWARD;
        }
    }

    void setDirection(Direction direction);

    Direction getDirection();

    void setPower(double power);

    double getPower();
}
