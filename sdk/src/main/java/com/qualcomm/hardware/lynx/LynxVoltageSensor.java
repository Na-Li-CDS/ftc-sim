package com.qualcomm.hardware.lynx;

import com.qualcomm.robotcore.hardware.VoltageSensor;

import ftcsim.hal.Latency;
import ftcsim.hal.SimHub;

/** The battery-voltage sensor built into a REV hub. */
public class LynxVoltageSensor implements VoltageSensor {
    private final SimHub hub;

    public LynxVoltageSensor(SimHub hub) {
        this.hub = hub;
    }

    @Override
    public double getVoltage() {
        Latency.command();
        return hub.inputVoltage;
    }

    @Override
    public Manufacturer getManufacturer() {
        return Manufacturer.Lynx;
    }

    @Override
    public String getDeviceName() {
        return "Expansion Hub Voltage Sensor";
    }

    @Override
    public String getConnectionInfo() {
        return hub.connectionInfo();
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void resetDeviceConfigurationForOpMode() {
    }

    @Override
    public void close() {
    }
}
