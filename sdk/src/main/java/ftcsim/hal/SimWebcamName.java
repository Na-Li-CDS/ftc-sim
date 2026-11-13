package ftcsim.hal;

import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;

import com.qualcomm.robotcore.util.SerialNumber;

/** A configured webcam. (Simulated vision processing is not available yet.) */
public final class SimWebcamName implements WebcamName {
    private final String name;
    private final SerialNumber serialNumber;

    public SimWebcamName(String name) {
        this.name = name;
        this.serialNumber = SerialNumber.fromString("SIM-CAM-" + Integer.toHexString(name.hashCode()));
    }

    @Override
    public SerialNumber getSerialNumber() {
        return serialNumber;
    }

    @Override
    public String getUsbDeviceNameIfAttached() {
        return "/dev/bus/usb/sim/" + name;
    }

    @Override
    public boolean isAttached() {
        return true;
    }

    @Override
    public boolean isWebcam() {
        return true;
    }

    @Override
    public boolean isCameraDirection() {
        return false;
    }

    @Override
    public boolean isSwitchable() {
        return false;
    }

    @Override
    public boolean isUnknown() {
        return false;
    }

    @Override
    public Manufacturer getManufacturer() {
        return Manufacturer.Other;
    }

    @Override
    public String getDeviceName() {
        return name;
    }

    @Override
    public String getConnectionInfo() {
        return "USB " + serialNumber;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void resetDeviceConfigurationForOpMode() {
    }

    @Override
    public void close() {
    }
}
