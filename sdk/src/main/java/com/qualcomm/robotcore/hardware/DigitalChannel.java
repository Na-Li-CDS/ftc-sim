package com.qualcomm.robotcore.hardware;

/** {@link DigitalChannel} is an interface by which digital channels can be controlled. */
public interface DigitalChannel extends HardwareDevice {

    enum Mode {INPUT, OUTPUT}

    Mode getMode();

    void setMode(Mode mode);

    boolean getState();

    void setState(boolean state);

    @Deprecated
    void setMode(DigitalChannelController.Mode mode);
}
