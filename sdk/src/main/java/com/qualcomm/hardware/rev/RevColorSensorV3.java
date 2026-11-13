package com.qualcomm.hardware.rev;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

import com.qualcomm.robotcore.hardware.ColorRangeSensor;
import com.qualcomm.robotcore.hardware.I2cAddr;
import com.qualcomm.robotcore.hardware.Light;
import com.qualcomm.robotcore.hardware.NormalizedRGBA;

import ftcsim.hal.Latency;
import ftcsim.hal.SensorBackends;

/**
 * REV Color Sensor V3: color and proximity. Like the real sensor it is a {@link ColorRangeSensor}
 * and a {@link Light}, but not a SwitchableLight.
 */
public class RevColorSensorV3 implements ColorRangeSensor, Light {
    private final SensorBackends.Color backend;
    private volatile float gain = 1.0f;
    private I2cAddr address = I2cAddr.create7bit(0x52);

    public RevColorSensorV3(SensorBackends.Color backend) {
        this.backend = backend;
    }

    private float lightFactor() {
        return backend.ledOn ? 1.0f : 0.25f;
    }

    public byte getDeviceID() {
        return (byte) 0xC2;
    }

    @Override
    public NormalizedRGBA getNormalizedColors() {
        Latency.i2c();
        NormalizedRGBA c = new NormalizedRGBA();
        float k = gain * lightFactor();
        c.red = Math.min(1f, backend.red * k);
        c.green = Math.min(1f, backend.green * k);
        c.blue = Math.min(1f, backend.blue * k);
        c.alpha = Math.min(1f, backend.alpha * k);
        return c;
    }

    @Override
    public float getGain() {
        return gain;
    }

    @Override
    public void setGain(float newGain) {
        gain = newGain;
    }

    private int raw(float normalized) {
        Latency.i2c();
        return (int) Math.min(65535, Math.max(0, normalized * lightFactor() * 65535));
    }

    @Override
    public synchronized int red() {
        return raw(backend.red);
    }

    @Override
    public synchronized int green() {
        return raw(backend.green);
    }

    @Override
    public synchronized int blue() {
        return raw(backend.blue);
    }

    @Override
    public synchronized int alpha() {
        return raw(backend.alpha);
    }

    @Override
    public synchronized int argb() {
        return getNormalizedColors().toColor();
    }

    @Override
    public synchronized void enableLed(boolean enable) {
        backend.ledOn = enable;
    }

    @Override
    public boolean isLightOn() {
        return backend.ledOn;
    }

    @Override
    public synchronized void setI2cAddress(I2cAddr newAddress) {
        address = newAddress;
    }

    @Override
    public synchronized I2cAddr getI2cAddress() {
        return address;
    }

    @Override
    public double getDistance(DistanceUnit unit) {
        Latency.i2c();
        double m = backend.distanceMeters;
        if (m == Double.MAX_VALUE || m > 0.10) {
            return unit.fromCm(12.0);   // the V3's proximity reading saturates a little past 10 cm
        }
        return unit.fromMeters(m);
    }

    /** Proximity as raw optical counts (0..2047, larger is closer). */
    public int rawOptical() {
        Latency.i2c();
        double m = backend.distanceMeters;
        if (m == Double.MAX_VALUE || m > 0.10) return 0;
        return (int) Math.round(2047 * Math.max(0, 1 - m / 0.10));
    }

    @Override
    public double getLightDetected() {
        return Math.min(1.0, getRawLightDetected() / getRawLightDetectedMax());
    }

    @Override
    public double getRawLightDetected() {
        return rawOptical();
    }

    @Override
    public double getRawLightDetectedMax() {
        return 2047;
    }

    @Override
    public String status() {
        return String.format("%s on %s", getDeviceName(), getConnectionInfo());
    }

    @Override
    public Manufacturer getManufacturer() {
        return Manufacturer.Broadcom;
    }

    @Override
    public String getDeviceName() {
        return "REV Color Sensor v3";
    }

    @Override
    public String getConnectionInfo() {
        return "I2C bus 1, address " + address;
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
