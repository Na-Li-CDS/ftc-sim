package ftcsim.physics;

import ftcsim.config.RobotConfig;

/**
 * A one-degree-of-freedom mechanism on a non-drivetrain motor: a roller, flywheel, arm (with
 * gravity and hard stops) or linear slide (with gravity and hard stops). It is integrated on its
 * own rather than in the rigid-body world because it only moves relative to the robot.
 */
public final class Mechanism {
    private static final double G = 9.81;

    public final RobotConfig.Device device;
    public final MotorUnit motor;
    private final RobotConfig.Load load;
    private final double gear;          // external reduction, motor output : mechanism
    private final double inertiaAtMotor;

    private double omegaMotor = 0;      // rad/s at the motor output, FORWARD direction
    private double position;            // mechanism coordinate: rad (rotary/arm) or m (slide)

    public Mechanism(RobotConfig.Device device, MotorUnit motor) {
        this.device = device;
        this.motor = motor;
        this.load = device.load;
        this.gear = device.externalGearRatio <= 0 ? 1 : device.externalGearRatio;
        double jLoad;
        switch (load.kind) {
            case "arm":
                jLoad = load.massKg * load.lengthM * load.lengthM / 3.0;   // uniform rod about its end
                position = Math.toRadians(load.startDeg);
                break;
            case "slide": {
                double r = load.spoolDiameterM / 2;
                jLoad = load.massKg * r * r;
                position = 0;
                break;
            }
            default:
                jLoad = load.inertia;
                position = 0;
        }
        this.inertiaAtMotor = motor.spec.reflectedInertia() + jLoad / (gear * gear);
    }

    public void step(double batteryVolts, double dt) {
        double tauMotor = motor.torque(omegaMotor, batteryVolts, dt);
        double tauLoad = 0;     // load torque at the motor output, opposing positive motion
        switch (load.kind) {
            case "arm":
                // Arm angle measured up from horizontal; gravity pulls it down.
                tauLoad = load.massKg * G * (load.lengthM / 2) * Math.cos(position) / gear;
                break;
            case "slide":
                tauLoad = load.massKg * G * (load.spoolDiameterM / 2) / gear;
                break;
            default:
                tauLoad = 0;   // rollers and flywheels: only the gearbox friction in MotorUnit
        }
        omegaMotor += (tauMotor - tauLoad) / inertiaAtMotor * dt;
        double mechRate = omegaMotor / gear * ("slide".equals(load.kind) ? load.spoolDiameterM / 2 : 1);
        double next = position + mechRate * dt;
        double lo, hi;
        if ("slide".equals(load.kind)) {
            lo = 0;
            hi = load.maxExtensionM;
        } else {
            lo = Math.toRadians(load.minDeg);
            hi = Math.toRadians(load.maxDeg);
        }
        if (next < lo || next > hi) {
            // Hard stop: the mechanism stops dead and the motor stalls against it.
            next = Math.max(lo, Math.min(hi, next));
            omegaMotor = 0;
        }
        position = next;
        motor.integrate(omegaMotor, dt);
    }

    /** Mechanism coordinate: radians (roller/flywheel/arm) or meters (slide). */
    public double position() {
        return position;
    }

    public double motorOmega() {
        return omegaMotor;
    }

    public String kind() {
        return load.kind;
    }
}
