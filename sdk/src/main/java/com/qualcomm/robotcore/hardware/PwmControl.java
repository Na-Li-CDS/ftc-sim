package com.qualcomm.robotcore.hardware;

/** For hardware devices which can adjust the frequency and duty cycle of their PWM signal. */
public interface PwmControl {

    void setPwmRange(PwmRange range);

    PwmRange getPwmRange();

    void setPwmEnable();

    void setPwmDisable();

    boolean isPwmEnabled();

    /** Sets the pulse width directly, in microseconds. */
    void setPulseWidth(double usPulse);

    /** Returns the commanded pulse width in microseconds (0 when the output is disabled). */
    double getPulseWidth();

    class PwmRange {
        public static final double usFrameDefault = 20000;
        public static final double usPulseUpperDefault = 2400;
        public static final double usPulseLowerDefault = 600;
        public static final PwmRange defaultRange = new PwmRange(usPulseLowerDefault, usPulseUpperDefault);

        public final double usPulseLower;
        public final double usPulseUpper;
        public final double usFrame;

        public PwmRange(double usPulseLower, double usPulseUpper) {
            this(usPulseLower, usPulseUpper, usFrameDefault);
        }

        public PwmRange(double usPulseLower, double usPulseUpper, double usFrame) {
            this.usPulseLower = usPulseLower;
            this.usPulseUpper = usPulseUpper;
            this.usFrame = usFrame;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof PwmRange) {
                PwmRange him = (PwmRange) o;
                return this.usPulseLower == him.usPulseLower && this.usPulseUpper == him.usPulseUpper && this.usFrame == him.usFrame;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Double.valueOf(usPulseLower).hashCode() ^ Double.valueOf(usPulseUpper).hashCode() ^ Double.valueOf(usFrame).hashCode();
        }
    }
}
