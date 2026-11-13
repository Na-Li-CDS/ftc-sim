package ftcsim.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catalogue of common FTC motors, from the vendors' datasheets (12 V). Values are at the
 * gearbox output shaft. A robot JSON can name one of these or give its own numbers.
 */
public final class MotorModels {

    /** A DC gearmotor. */
    public record Spec(String name, double ticksPerRev, double gearing, double freeRpm, double stallTorqueNm,
                       double stallCurrentA, double freeCurrentA, double rotorInertia) {

        /** Free speed at 12 V, rad/s at the output shaft. */
        public double freeSpeedRadPerSec() {
            return freeRpm * 2 * Math.PI / 60.0;
        }

        /** Rotor inertia reflected to the output shaft, kg m^2. */
        public double reflectedInertia() {
            return rotorInertia * gearing * gearing;
        }

        public double ticksPerRadian() {
            return ticksPerRev / (2 * Math.PI);
        }
    }

    private static final Map<String, Spec> MODELS = new LinkedHashMap<>();

    // goBILDA 5203 Yellow Jacket: 6000 RPM motor, 28 counts/rev at the motor, 9.2 A stall.
    private static final double GOBILDA_ROTOR = 3.4e-6;

    static {
        gobilda("goBILDA 5203 6000rpm", 28, 1, 6000, 1.47);
        gobilda("goBILDA 5203 1620rpm", 103.8, 3.7, 1620, 5.4);
        gobilda("goBILDA 5203 1150rpm", 145.1, 5.2, 1150, 7.9);
        gobilda("goBILDA 5203 435rpm", 384.5, 13.7, 435, 18.7);
        gobilda("goBILDA 5203 312rpm", 537.7, 19.2, 312, 24.3);
        gobilda("goBILDA 5203 223rpm", 751.8, 26.9, 223, 33.9);
        gobilda("goBILDA 5203 117rpm", 1425.1, 50.9, 117, 68.4);
        gobilda("goBILDA 5203 84rpm", 1993.6, 71.2, 84, 93.6);
        gobilda("goBILDA 5203 60rpm", 2786.2, 99.5, 60, 133.2);
        gobilda("goBILDA 5203 43rpm", 3895.9, 139, 43, 182.8);
        gobilda("goBILDA 5203 30rpm", 5281.1, 188, 30, 250.0);
        // REV HD Hex: 6000 RPM, 0.105 N m stall, 8.5 A; 28 counts/rev at the motor.
        rev("REV HD Hex 20:1", 560, 20, 300);
        rev("REV HD Hex 40:1", 1120, 40, 150);
        rev("REV HD Hex UltraPlanetary 3:1", 28 * 2.89, 2.89, 6000 / 2.89);
        rev("REV HD Hex UltraPlanetary 4:1", 28 * 3.61, 3.61, 6000 / 3.61);
        rev("REV HD Hex UltraPlanetary 5:1", 28 * 5.23, 5.23, 6000 / 5.23);
        rev("REV HD Hex UltraPlanetary 12:1", 28 * 2.89 * 3.61, 2.89 * 3.61, 6000 / (2.89 * 3.61));
        rev("REV HD Hex UltraPlanetary 20:1", 28 * 3.61 * 5.23, 3.61 * 5.23, 6000 / (3.61 * 5.23));
        put(new Spec("REV Core Hex", 288, 72, 125, 3.2, 4.4, 0.18, 2.0e-6));
        put(new Spec("AndyMark NeveRest 40", 1120, 40, 160, 2.47, 11.5, 0.4, 4.0e-6));
        put(new Spec("AndyMark NeveRest 20 Orbital", 537.6, 19.2, 340, 1.24, 11.5, 0.4, 4.0e-6));
        put(new Spec("TETRIX TorqueNADO 60:1", 1440, 60, 100, 4.94, 11.5, 0.3, 4.0e-6));
    }

    private static void gobilda(String name, double ticks, double gearing, double rpm, double stallKgCm) {
        put(new Spec(name, ticks, gearing, rpm, stallKgCm * 0.0980665, 9.2, 0.25, GOBILDA_ROTOR));
    }

    private static void rev(String name, double ticks, double gearing, double rpm) {
        put(new Spec(name, ticks, gearing, rpm, 0.105 * gearing * 0.9, 8.5, 0.3, 3.0e-6));
    }

    private static void put(Spec s) {
        MODELS.put(s.name().toLowerCase(), s);
    }

    private MotorModels() {
    }

    public static Spec get(String name) {
        Spec s = MODELS.get(name.trim().toLowerCase());
        if (s == null) {
            throw new IllegalArgumentException("Unknown motor model \"" + name + "\". Known models: " + names());
        }
        return s;
    }

    public static String names() {
        StringBuilder sb = new StringBuilder();
        for (Spec s : MODELS.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.name());
        }
        return sb.toString();
    }
}
