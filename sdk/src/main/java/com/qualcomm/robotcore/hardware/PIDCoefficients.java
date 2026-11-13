package com.qualcomm.robotcore.hardware;

import java.util.Locale;

/** {@link PIDCoefficients} conveys a set of configuration parameters for a PID algorithm. */
public class PIDCoefficients {
    public double p;
    public double i;
    public double d;

    @Override
    public String toString() {
        return String.format(Locale.US, "%s(p=%f i=%f d=%f)", getClass().getSimpleName(), p, i, d);
    }

    public PIDCoefficients() {
        p = i = d = 0;
    }

    public PIDCoefficients(double p, double i, double d) {
        this.p = p;
        this.i = i;
        this.d = d;
    }
}
