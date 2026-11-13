package com.qualcomm.robotcore.hardware;

/** Touch Sensor. */
public interface TouchSensor extends HardwareDevice {
    /** Represents how much force is applied to the touch sensor; for some sensors this value will only ever be 0 or 1. */
    double getValue();

    boolean isPressed();
}
