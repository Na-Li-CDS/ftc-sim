package com.qualcomm.hardware.rev;

import com.qualcomm.robotcore.hardware.TouchSensor;

import ftcsim.hal.DigitalPort;

/** REV Touch Sensor on a digital port (the line reads low when pressed). */
public class RevTouchSensor implements TouchSensor {
    private final DigitalPort port;

    public RevTouchSensor(DigitalPort port) {
        this.port = port;
    }

    @Override
    public double getValue() {
        return isPressed() ? 1 : 0;
    }

    @Override
    public boolean isPressed() {
        return !port.hub.readDigital(port.channel);
    }

    @Override
    public Manufacturer getManufacturer() {
        return Manufacturer.Lynx;
    }

    @Override
    public String getDeviceName() {
        return "REV Touch Sensor";
    }

    @Override
    public String getConnectionInfo() {
        return port.hub.connectionInfo() + "; digital port " + port.channel;
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
