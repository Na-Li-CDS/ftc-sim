package org.firstinspires.ftc.robotcore.external.navigation;

/** Units of electrical potential. */
public enum VoltageUnit {
    VOLTS {
        @Override public double toVolts(double v) { return v; }
        @Override public double toMilliVolts(double v) { return v * 1000; }
        @Override public double convert(double estimate, VoltageUnit unit) { return unit.toVolts(estimate); }
    },
    MILLIVOLTS {
        @Override public double toVolts(double v) { return v / 1000; }
        @Override public double toMilliVolts(double v) { return v; }
        @Override public double convert(double estimate, VoltageUnit unit) { return unit.toMilliVolts(estimate); }
    };

    public abstract double toVolts(double voltage);

    public abstract double toMilliVolts(double voltage);

    public abstract double convert(double estimate, VoltageUnit unit);
}
