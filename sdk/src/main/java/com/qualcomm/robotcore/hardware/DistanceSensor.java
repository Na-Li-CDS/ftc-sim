package com.qualcomm.robotcore.hardware;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

/** The {@link DistanceSensor} may be found on hardware sensors which measure distance. */
public interface DistanceSensor extends HardwareDevice {
    /** The value returned when a distance reading is not in fact available. */
    double distanceOutOfRange = DistanceUnit.infinity;

    double getDistance(DistanceUnit unit);
}
