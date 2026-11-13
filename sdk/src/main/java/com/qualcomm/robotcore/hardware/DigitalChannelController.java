package com.qualcomm.robotcore.hardware;

/** Interface for working with Digital Channel Controllers. */
public interface DigitalChannelController extends HardwareDevice {

    @Deprecated
    enum Mode {
        INPUT, OUTPUT;

        public DigitalChannel.Mode migrate() {
            return this == INPUT ? DigitalChannel.Mode.INPUT : DigitalChannel.Mode.OUTPUT;
        }
    }

    DigitalChannel.Mode getDigitalChannelMode(int channel);

    void setDigitalChannelMode(int channel, DigitalChannel.Mode mode);

    boolean getDigitalChannelState(int channel);

    void setDigitalChannelState(int channel, boolean state);
}
