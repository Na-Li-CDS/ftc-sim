package com.qualcomm.hardware.gobilda;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D;
import org.firstinspires.ftc.robotcore.external.navigation.Quaternion;
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit;

import com.qualcomm.robotcore.hardware.HardwareDevice;

import ftcsim.hal.Latency;
import ftcsim.hal.PinpointBackend;

/**
 * Driver for the goBILDA Pinpoint odometry computer. As on the robot, the getters return the
 * values read by the most recent call to {@link #update()}.
 */
public class GoBildaPinpointDriver implements HardwareDevice {

    public enum Register {
        DEVICE_ID, DEVICE_VERSION, DEVICE_STATUS, DEVICE_CONTROL, LOOP_TIME, X_ENCODER_VALUE, Y_ENCODER_VALUE,
        X_POSITION, Y_POSITION, H_ORIENTATION, X_VELOCITY, Y_VELOCITY, H_VELOCITY, MM_PER_TICK,
        X_POD_OFFSET, Y_POD_OFFSET, YAW_SCALAR, BULK_READ, QUATERNION_W, QUATERNION_X,
        QUATERNION_Y, QUATERNION_Z, PITCH, ROLL, SET_BULK_READ
    }

    public enum DeviceStatus {
        NOT_READY, READY, CALIBRATING, FAULT_X_POD_NOT_DETECTED, FAULT_Y_POD_NOT_DETECTED,
        FAULT_NO_PODS_DETECTED, FAULT_IMU_RUNAWAY, FAULT_BAD_READ
    }

    public enum EncoderDirection {FORWARD, REVERSED}

    public enum GoBildaOdometryPods {goBILDA_SWINGARM_POD, goBILDA_4_BAR_POD}

    public enum ReadData {ONLY_UPDATE_HEADING}

    public enum ErrorDetectionType {NONE, LOCAL_TEST, CRC}

    private final PinpointBackend backend;
    private PinpointBackend.Snapshot data;
    private long lastUpdateNanos = System.nanoTime();
    private double frequency = 0;

    public GoBildaPinpointDriver(PinpointBackend backend) {
        this.backend = backend;
        this.data = backend.read();
    }

    /** Reads position, velocity, encoder and status data from the device. */
    public void update() {
        Latency.i2c();
        data = backend.read();
        long now = System.nanoTime();
        double dt = (now - lastUpdateNanos) / 1e9;
        frequency = dt > 0 ? 1.0 / dt : 0;
        lastUpdateNanos = now;
    }

    /** Reads only the heading (faster). */
    public void update(ReadData data) {
        Latency.command();
        PinpointBackend.Snapshot fresh = backend.read();
        this.data = fresh;
    }

    public void setBulkReadScope(Register... registers) {
    }

    public void setErrorDetectionType(ErrorDetectionType errorDetectionType) {
    }

    /**
     * Sets the pod offsets from the tracking center. xOffset: how far sideways the X (forward)
     * pod is; left is positive. yOffset: how far forward the Y (strafe) pod is; forward is positive.
     */
    public void setOffsets(double xOffset, double yOffset, DistanceUnit distanceUnit) {
        Latency.i2c();
        backend.setOffsets(distanceUnit.toMm(xOffset), distanceUnit.toMm(yOffset));
    }

    public void recalibrateIMU() {
        Latency.i2c();
        backend.recalibrateImu();
    }

    public void resetPosAndIMU() {
        Latency.i2c();
        backend.resetPosAndImu();
    }

    public void setEncoderDirections(EncoderDirection xEncoder, EncoderDirection yEncoder) {
        Latency.i2c();
        backend.setDirections(xEncoder == EncoderDirection.REVERSED, yEncoder == EncoderDirection.REVERSED);
    }

    public void setEncoderResolution(GoBildaOdometryPods pods) {
        Latency.i2c();
        backend.setTicksPerMm(pods == GoBildaOdometryPods.goBILDA_SWINGARM_POD
                ? PinpointBackend.SWINGARM_TICKS_PER_MM : PinpointBackend.FOUR_BAR_TICKS_PER_MM);
    }

    public void setEncoderResolution(double ticksPerUnit, DistanceUnit distanceUnit) {
        Latency.i2c();
        // ticks per unit -> ticks per mm
        backend.setTicksPerMm(ticksPerUnit / distanceUnit.toMm(1.0));
    }

    public void setYawScalar(double yawOffset) {
        Latency.i2c();
        backend.setYawScalar(yawOffset);
    }

    public void setPosition(Pose2D pos) {
        Latency.i2c();
        backend.setPose(pos.getX(DistanceUnit.MM), pos.getY(DistanceUnit.MM), pos.getHeading(AngleUnit.RADIANS));
    }

    public void setPosX(double posX, DistanceUnit distanceUnit) {
        Latency.i2c();
        backend.setX(distanceUnit.toMm(posX));
    }

    public void setPosY(double posY, DistanceUnit distanceUnit) {
        Latency.i2c();
        backend.setY(distanceUnit.toMm(posY));
    }

    public void setHeading(double heading, AngleUnit angleUnit) {
        Latency.i2c();
        backend.setHeading(angleUnit.toRadians(heading));
    }

    public int getDeviceID() {
        return 2;
    }

    public int getDeviceVersion() {
        return 3;
    }

    public float getYawScalar() {
        return (float) data.yawScalar;
    }

    public DeviceStatus getDeviceStatus() {
        return data.calibrating ? DeviceStatus.CALIBRATING : DeviceStatus.READY;
    }

    /** Microseconds per internal update loop. */
    public int getLoopTime() {
        return 667;
    }

    /** Rate at which update() is being called, in Hz. */
    public double getFrequency() {
        return frequency;
    }

    public int getEncoderX() {
        return data.encoderX;
    }

    public int getEncoderY() {
        return data.encoderY;
    }

    public double getPosX(DistanceUnit distanceUnit) {
        return distanceUnit.fromMm(data.xMm);
    }

    public double getPosY(DistanceUnit distanceUnit) {
        return distanceUnit.fromMm(data.yMm);
    }

    public double getHeading(AngleUnit angleUnit) {
        return angleUnit.fromRadians(data.headingRad);
    }

    public double getHeading(UnnormalizedAngleUnit unnormalizedAngleUnit) {
        return unnormalizedAngleUnit.fromRadians(data.headingRad);
    }

    public double getVelX(DistanceUnit distanceUnit) {
        return distanceUnit.fromMm(data.velXMmPerSec);
    }

    public double getVelY(DistanceUnit distanceUnit) {
        return distanceUnit.fromMm(data.velYMmPerSec);
    }

    public double getHeadingVelocity(UnnormalizedAngleUnit unnormalizedAngleUnit) {
        return unnormalizedAngleUnit.fromRadians(data.velHeadingRadPerSec);
    }

    public float getXOffset(DistanceUnit distanceUnit) {
        return (float) distanceUnit.fromMm(data.xOffsetMm);
    }

    public float getYOffset(DistanceUnit distanceUnit) {
        return (float) distanceUnit.fromMm(data.yOffsetMm);
    }

    public Pose2D getPosition() {
        return new Pose2D(DistanceUnit.MM, data.xMm, data.yMm, AngleUnit.RADIANS, data.headingRad);
    }

    public Quaternion getQuaternion() {
        double h = data.headingRad / 2;
        return new Quaternion((float) Math.cos(h), 0, 0, (float) Math.sin(h), System.nanoTime());
    }

    public double getPitch(AngleUnit angleUnit) {
        return 0;
    }

    public double getRoll(AngleUnit angleUnit) {
        return 0;
    }

    @Override
    public Manufacturer getManufacturer() {
        return Manufacturer.GoBilda;
    }

    @Override
    public String getDeviceName() {
        return "goBILDA® Pinpoint Odometry Computer";
    }

    @Override
    public String getConnectionInfo() {
        return "I2C bus 1, address 0x31";
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
