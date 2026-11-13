package org.firstinspires.ftc.robotcore.external.hardware.camera;

/** {@link CameraName} identifies a camera. */
public interface CameraName {
    boolean isWebcam();

    boolean isCameraDirection();

    boolean isSwitchable();

    boolean isUnknown();
}
