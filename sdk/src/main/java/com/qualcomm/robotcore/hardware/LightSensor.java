package com.qualcomm.robotcore.hardware;

/** Light Sensor. */
public interface LightSensor extends HardwareDevice {
    /** Get the amount of light detected by the sensor, scaled to [0, 1]. */
    double getLightDetected();

    double getRawLightDetected();

    double getRawLightDetectedMax();

    void enableLed(boolean enable);

    String status();
}
