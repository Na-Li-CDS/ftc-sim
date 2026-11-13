package com.qualcomm.robotcore.hardware;

import com.qualcomm.robotcore.hardware.configuration.typecontainers.MotorConfigurationType;

/** Control a DC Motor attached to a DC Motor Controller. */
public class DcMotorImpl implements DcMotor {

    protected DcMotorController controller;
    protected int portNumber;
    protected Direction direction = Direction.FORWARD;

    public DcMotorImpl(DcMotorController controller, int portNumber) {
        this(controller, portNumber, Direction.FORWARD);
    }

    public DcMotorImpl(DcMotorController controller, int portNumber, Direction direction) {
        this.controller = controller;
        this.portNumber = portNumber;
        this.direction = direction;
    }

    @Override
    public Manufacturer getManufacturer() {
        return controller.getManufacturer();
    }

    @Override
    public String getDeviceName() {
        return "DC Motor";
    }

    @Override
    public String getConnectionInfo() {
        return controller.getConnectionInfo() + "; port " + portNumber;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void resetDeviceConfigurationForOpMode() {
        setDirection(Direction.FORWARD);
        controller.resetDeviceConfigurationForOpMode(portNumber);
    }

    @Override
    public void close() {
        setPowerFloat();
    }

    @Override
    public MotorConfigurationType getMotorType() {
        return controller.getMotorType(portNumber);
    }

    @Override
    public void setMotorType(MotorConfigurationType motorType) {
        controller.setMotorType(portNumber, motorType);
    }

    @Override
    public DcMotorController getController() {
        return controller;
    }

    @Override
    public synchronized void setDirection(Direction direction) {
        this.direction = direction;
    }

    @Override
    public Direction getDirection() {
        return direction;
    }

    @Override
    public int getPortNumber() {
        return portNumber;
    }

    @Override
    public void setZeroPowerBehavior(ZeroPowerBehavior zeroPowerBehavior) {
        controller.setMotorZeroPowerBehavior(portNumber, zeroPowerBehavior);
    }

    @Override
    public ZeroPowerBehavior getZeroPowerBehavior() {
        return controller.getMotorZeroPowerBehavior(portNumber);
    }

    @Override
    @Deprecated
    public synchronized void setPowerFloat() {
        setZeroPowerBehavior(ZeroPowerBehavior.FLOAT);
        setPower(0.0);
    }

    @Override
    public synchronized boolean getPowerFloat() {
        return getZeroPowerBehavior() == ZeroPowerBehavior.FLOAT && getPower() == 0.0;
    }

    /** Power must be positive when in RUN_TO_POSITION mode; direction is applied to the target instead. */
    @Override
    public synchronized void setPower(double power) {
        if (getMode() == RunMode.RUN_TO_POSITION) {
            power = Math.abs(power);
        } else {
            power = adjustPower(power);
        }
        internalSetPower(power);
    }

    protected void internalSetPower(double power) {
        controller.setMotorPower(portNumber, power);
    }

    @Override
    public synchronized double getPower() {
        double power = controller.getMotorPower(portNumber);
        if (getMode() == RunMode.RUN_TO_POSITION) {
            power = Math.abs(power);
        } else {
            power = adjustPower(power);
        }
        return power;
    }

    @Override
    public boolean isBusy() {
        return controller.isBusy(portNumber);
    }

    @Override
    public synchronized void setTargetPosition(int position) {
        position = adjustPosition(position);
        internalSetTargetPosition(position);
    }

    protected void internalSetTargetPosition(int position) {
        controller.setMotorTargetPosition(portNumber, position);
    }

    @Override
    public synchronized int getTargetPosition() {
        int position = controller.getMotorTargetPosition(portNumber);
        return adjustPosition(position);
    }

    @Override
    public synchronized int getCurrentPosition() {
        int position = controller.getMotorCurrentPosition(portNumber);
        return adjustPosition(position);
    }

    @Override
    public synchronized void setMode(RunMode mode) {
        mode = mode.migrate();
        controller.setMotorMode(portNumber, mode);
    }

    @Override
    public RunMode getMode() {
        return controller.getMotorMode(portNumber);
    }

    protected Direction getOperationalDirection() {
        return direction;
    }

    protected int adjustPosition(int position) {
        if (getOperationalDirection() == Direction.REVERSE) position = -position;
        return position;
    }

    protected double adjustPower(double power) {
        if (getOperationalDirection() == Direction.REVERSE) power = -power;
        return power;
    }
}
