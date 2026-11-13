package com.qualcomm.robotcore.util;

/** Identifies a USB device. In the simulator every device gets a stable fake serial number. */
public class SerialNumber {
    protected final String serialNumberString;

    protected SerialNumber(String serialNumberString) {
        this.serialNumberString = serialNumberString;
    }

    public static SerialNumber fromString(String serialNumberString) {
        return new SerialNumber(serialNumberString);
    }

    public static SerialNumber createFake() {
        return new SerialNumber("SIM-" + Long.toHexString(System.nanoTime()));
    }

    public String getString() {
        return serialNumberString;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SerialNumber && ((SerialNumber) o).serialNumberString.equals(serialNumberString);
    }

    @Override
    public int hashCode() {
        return serialNumberString.hashCode();
    }

    @Override
    public String toString() {
        return serialNumberString;
    }
}
