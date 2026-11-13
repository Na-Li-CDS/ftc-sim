package com.qualcomm.robotcore.hardware.configuration.typecontainers;

import java.util.Locale;

import org.firstinspires.ftc.robotcore.external.navigation.Rotation;

/** Describes a motor model: encoder resolution, gearing and free speed. */
public final class MotorConfigurationType implements Cloneable {
    private double ticksPerRev = 28;
    private double gearing = 1;
    private double maxRPM = 6000;
    private double achieveableMaxRPMFraction = 0.85;
    private Rotation orientation = Rotation.CCW;

    public MotorConfigurationType() {
    }

    public String getName() {
        return String.format(Locale.US, "%.0f RPM motor (%.1f ticks/rev)", maxRPM, ticksPerRev);
    }

    public double getTicksPerRev() {
        return ticksPerRev;
    }

    public double getAchieveableMaxTicksPerSecond() {
        return ticksPerRev * maxRPM * achieveableMaxRPMFraction / 60.0;
    }

    public int getAchieveableMaxTicksPerSecondRounded() {
        return (int) Math.round(getAchieveableMaxTicksPerSecond());
    }

    public void setTicksPerRev(double ticksPerRev) {
        this.ticksPerRev = ticksPerRev;
    }

    public double getGearing() {
        return gearing;
    }

    public void setGearing(double gearing) {
        this.gearing = gearing;
    }

    public double getMaxRPM() {
        return maxRPM;
    }

    public void setMaxRPM(double maxRPM) {
        this.maxRPM = maxRPM;
    }

    public double getAchieveableMaxRPMFraction() {
        return achieveableMaxRPMFraction;
    }

    public void setAchieveableMaxRPMFraction(double fraction) {
        this.achieveableMaxRPMFraction = fraction;
    }

    public Rotation getOrientation() {
        return orientation;
    }

    public void setOrientation(Rotation orientation) {
        this.orientation = orientation;
    }

    public static MotorConfigurationType getUnspecifiedMotorType() {
        return new MotorConfigurationType();
    }

    @Override
    public MotorConfigurationType clone() {
        try {
            return (MotorConfigurationType) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return getName();
    }
}
