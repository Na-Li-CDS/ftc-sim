package com.qualcomm.robotcore.hardware;

/** NormalizedColorSensor returns color sensor data in standardized units in the range [0, 1]. */
public interface NormalizedColorSensor extends HardwareDevice {
    NormalizedRGBA getNormalizedColors();

    float getGain();

    void setGain(float newGain);
}
