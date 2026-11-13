package org.firstinspires.ftc.robotcore.external.navigation;

/**
 * An {@link AngleUnit} represents angles in different units of measure and provides utility
 * methods to convert across units. Angles are normalized to [-180, 180) degrees or [-pi, pi) radians.
 */
public enum AngleUnit {
    DEGREES(0), RADIANS(1);

    public final byte bVal;
    public static final float Pif = (float) Math.PI;
    static final double TwoPi = 2 * Math.PI;
    static final float TwoPif = 2 * Pif;

    AngleUnit(int i) {
        bVal = (byte) i;
    }

    public double fromDegrees(double degrees) {
        switch (this) {
            default:
            case RADIANS: return this.normalize(degrees / 180.0 * Math.PI);
            case DEGREES: return this.normalize(degrees);
        }
    }

    public float fromDegrees(float degrees) {
        switch (this) {
            default:
            case RADIANS: return this.normalize(degrees / 180.0f * Pif);
            case DEGREES: return this.normalize(degrees);
        }
    }

    public double fromRadians(double radians) {
        switch (this) {
            default:
            case RADIANS: return this.normalize(radians);
            case DEGREES: return this.normalize(radians / Math.PI * 180.0);
        }
    }

    public float fromRadians(float radians) {
        switch (this) {
            default:
            case RADIANS: return this.normalize(radians);
            case DEGREES: return this.normalize(radians / Pif * 180.0f);
        }
    }

    public double fromUnit(AngleUnit them, double theirs) {
        switch (them) {
            default:
            case RADIANS: return this.fromRadians(theirs);
            case DEGREES: return this.fromDegrees(theirs);
        }
    }

    public float fromUnit(AngleUnit them, float theirs) {
        switch (them) {
            default:
            case RADIANS: return this.fromRadians(theirs);
            case DEGREES: return this.fromDegrees(theirs);
        }
    }

    public double toDegrees(double inOurUnits) {
        switch (this) {
            default:
            case RADIANS: return DEGREES.fromRadians(inOurUnits);
            case DEGREES: return DEGREES.fromDegrees(inOurUnits);
        }
    }

    public float toDegrees(float inOurUnits) {
        switch (this) {
            default:
            case RADIANS: return DEGREES.fromRadians(inOurUnits);
            case DEGREES: return DEGREES.fromDegrees(inOurUnits);
        }
    }

    public double toRadians(double inOurUnits) {
        switch (this) {
            default:
            case RADIANS: return RADIANS.fromRadians(inOurUnits);
            case DEGREES: return RADIANS.fromDegrees(inOurUnits);
        }
    }

    public float toRadians(float inOurUnits) {
        switch (this) {
            default:
            case RADIANS: return RADIANS.fromRadians(inOurUnits);
            case DEGREES: return RADIANS.fromDegrees(inOurUnits);
        }
    }

    public double normalize(double mine) {
        switch (this) {
            default:
            case RADIANS: return normalizeRadians(mine);
            case DEGREES: return normalizeDegrees(mine);
        }
    }

    public float normalize(float mine) {
        switch (this) {
            default:
            case RADIANS: return normalizeRadians(mine);
            case DEGREES: return normalizeDegrees(mine);
        }
    }

    public static double normalizeDegrees(double degrees) {
        if (Double.isNaN(degrees) || Double.isInfinite(degrees)) return degrees;
        if (degrees >= 180.0 || degrees < -180.0) degrees = degrees % 360.0;
        while (degrees >= 180.0) degrees -= 360.0;
        while (degrees < -180.0) degrees += 360.0;
        return degrees;
    }

    public static float normalizeDegrees(float degrees) {
        return (float) normalizeDegrees((double) degrees);
    }

    public static double normalizeRadians(double radians) {
        if (Double.isNaN(radians) || Double.isInfinite(radians)) return radians;
        if (radians >= Math.PI || radians < -Math.PI) radians = radians % TwoPi;
        while (radians >= Math.PI) radians -= TwoPi;
        while (radians < -Math.PI) radians += TwoPi;
        return radians;
    }

    public static float normalizeRadians(float radians) {
        return (float) normalizeRadians((double) radians);
    }

    public UnnormalizedAngleUnit getUnnormalized() {
        switch (this) {
            default:
            case RADIANS: return UnnormalizedAngleUnit.RADIANS;
            case DEGREES: return UnnormalizedAngleUnit.DEGREES;
        }
    }
}
