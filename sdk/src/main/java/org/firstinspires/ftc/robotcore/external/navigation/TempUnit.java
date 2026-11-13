package org.firstinspires.ftc.robotcore.external.navigation;

/** Units of temperature. */
public enum TempUnit {
    CELSIUS(0), FARENHEIT(1), KELVIN(2);

    public final byte bVal;
    static final double zeroCelsiusK = 273.15;
    static final double zeroCelsiusF = 32;
    static final double CperF = 5.0 / 9.0;

    TempUnit(int i) {
        bVal = (byte) i;
    }

    public double fromCelsius(double celsius) {
        switch (this) {
            default:
            case CELSIUS: return celsius;
            case KELVIN: return celsius + zeroCelsiusK;
            case FARENHEIT: return celsius / CperF + zeroCelsiusF;
        }
    }

    public double fromKelvin(double kelvin) {
        return fromCelsius(kelvin - zeroCelsiusK);
    }

    public double fromFarenheit(double fahrenheit) {
        return fromCelsius((fahrenheit - zeroCelsiusF) * CperF);
    }

    public double fromUnit(TempUnit them, double theirs) {
        switch (them) {
            default:
            case CELSIUS: return this.fromCelsius(theirs);
            case KELVIN: return this.fromKelvin(theirs);
            case FARENHEIT: return this.fromFarenheit(theirs);
        }
    }
}
