package com.qualcomm.robotcore.hardware;

import java.util.Locale;

/**
 * {@link PIDFCoefficients} conveys a set of configuration parameters for a PIDF algorithm,
 * a PID algorithm which includes an additional feed-forward term.
 */
public class PIDFCoefficients {
    public double p;
    public double i;
    public double d;
    public double f;
    public MotorControlAlgorithm algorithm;

    @Override
    public String toString() {
        return String.format(Locale.US, "%s(p=%f i=%f d=%f f=%f alg=%s)", getClass().getSimpleName(), p, i, d, f, algorithm);
    }

    public PIDFCoefficients() {
        this.algorithm = MotorControlAlgorithm.PIDF;
    }

    public PIDFCoefficients(double p, double i, double d, double f, MotorControlAlgorithm algorithm) {
        this.p = p;
        this.i = i;
        this.d = d;
        this.f = f;
        this.algorithm = algorithm;
    }

    public PIDFCoefficients(double p, double i, double d, double f) {
        this(p, i, d, f, MotorControlAlgorithm.PIDF);
    }

    public PIDFCoefficients(PIDFCoefficients other) {
        this(other.p, other.i, other.d, other.f, other.algorithm);
    }

    public PIDFCoefficients(PIDCoefficients other) {
        this(other.p, other.i, other.d, 0, MotorControlAlgorithm.LegacyPID);
    }
}
