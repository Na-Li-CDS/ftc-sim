package org.firstinspires.ftc.robotcore.external.hardware.camera;

import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.util.SerialNumber;

/** {@link WebcamName} identifies a USB webcam in the robot configuration. */
public interface WebcamName extends CameraName, HardwareDevice {
    SerialNumber getSerialNumber();

    String getUsbDeviceNameIfAttached();

    boolean isAttached();
}
