package com.qualcomm.robotcore.hardware.configuration.typecontainers;

import com.qualcomm.robotcore.hardware.PwmControl;

/** Describes a servo model; the standard type uses the default 600-2400 µs pulse range. */
public final class ServoConfigurationType {
    private static final ServoConfigurationType STANDARD = new ServoConfigurationType();

    public ServoConfigurationType() {
    }

    public static ServoConfigurationType getStandardServoType() {
        return STANDARD;
    }

    public double getUsPulseLower() {
        return PwmControl.PwmRange.usPulseLowerDefault;
    }

    public double getUsPulseUpper() {
        return PwmControl.PwmRange.usPulseUpperDefault;
    }

    public double getUsFrame() {
        return PwmControl.PwmRange.usFrameDefault;
    }
}
