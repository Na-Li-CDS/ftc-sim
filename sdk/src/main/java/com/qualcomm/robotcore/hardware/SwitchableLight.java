package com.qualcomm.robotcore.hardware;

/** SwitchableLight instances are lights whose on/off status can be programmatically manipulated. */
public interface SwitchableLight extends Light {
    void enableLight(boolean enable);
}
