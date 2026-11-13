package com.qualcomm.robotcore.hardware;

/** Voltage Sensor. */
public interface VoltageSensor extends HardwareDevice {
    /** Get the current voltage, in volts. */
    double getVoltage();
}
