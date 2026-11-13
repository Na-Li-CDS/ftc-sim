package org.firstinspires.ftc.robotcore.external.navigation;

/** Units of electrical current. */
public enum CurrentUnit {
    AMPS {
        @Override public double toAmps(double current) { return current; }
        @Override public double toMilliAmps(double current) { return current * 1000; }
        @Override public double convert(double estimate, CurrentUnit unit) { return unit.toAmps(estimate); }
    },
    MILLIAMPS {
        @Override public double toAmps(double current) { return current / 1000; }
        @Override public double toMilliAmps(double current) { return current; }
        @Override public double convert(double estimate, CurrentUnit unit) { return unit.toMilliAmps(estimate); }
    };

    public abstract double toAmps(double current);

    public abstract double toMilliAmps(double current);

    /** Converts {@code estimate}, expressed in {@code unit}, into this unit. */
    public abstract double convert(double estimate, CurrentUnit unit);
}
