package org.firstinspires.ftc.teamcode;

import java.util.List;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.util.Range;

/**
 * Example TeleOp for the simulator's default mecanum robot (robots/mecanum.json). It uses only
 * the FTC SDK, so it runs unchanged on a real robot whose configuration uses the same names.
 *
 * Xbox controller (gamepad1):
 *   left stick      drive (field-centric; hold LB for robot-centric)
 *   right stick X   turn
 *   A               reset the IMU heading (point the robot away from you first)
 *   RT / LT         intake in / out
 *   Y               launcher on/off (rumbles when it reaches speed)
 *   RB              feed into the launcher
 *   d-pad up/down   raise/lower the hood
 */
@TeleOp(name = "BIOBUZZ TeleOp", group = "Examples")
public class BiobuzzTeleOp extends OpMode {

    static final double LAUNCHER_TICKS_PER_SECOND = 2000;   // goBILDA 6000 RPM motor: 28 ticks/rev
    static final double HOOD_STEP = 0.05;

    private DcMotor frontLeft, frontRight, backLeft, backRight, intake;
    private DcMotorEx launcher;
    private Servo hood;
    private CRServo feeder;
    private IMU imu;
    private GoBildaPinpointDriver pinpoint;
    private List<LynxModule> hubs;

    private boolean launcherOn = false;
    private boolean launcherWasReady = false;
    private double hoodPosition = 0.5;

    @Override
    public void init() {
        hubs = hardwareMap.getAll(LynxModule.class);
        for (LynxModule hub : hubs) hub.setBulkCachingMode(LynxModule.BulkCachingMode.MANUAL);

        frontLeft = hardwareMap.get(DcMotor.class, "front_left_drive");
        frontRight = hardwareMap.get(DcMotor.class, "front_right_drive");
        backLeft = hardwareMap.get(DcMotor.class, "back_left_drive");
        backRight = hardwareMap.get(DcMotor.class, "back_right_drive");
        frontLeft.setDirection(DcMotor.Direction.REVERSE);
        backLeft.setDirection(DcMotor.Direction.REVERSE);
        for (DcMotor m : new DcMotor[]{frontLeft, frontRight, backLeft, backRight}) {
            m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            m.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        }

        intake = hardwareMap.get(DcMotor.class, "intake");
        launcher = hardwareMap.get(DcMotorEx.class, "launcher");
        launcher.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        launcher.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        hood = hardwareMap.get(Servo.class, "hood");
        feeder = hardwareMap.get(CRServo.class, "feeder");

        imu = hardwareMap.get(IMU.class, "imu");
        imu.initialize(new IMU.Parameters(new RevHubOrientationOnRobot(
                RevHubOrientationOnRobot.LogoFacingDirection.UP, RevHubOrientationOnRobot.UsbFacingDirection.FORWARD)));
        imu.resetYaw();

        pinpoint = hardwareMap.get(GoBildaPinpointDriver.class, "pinpoint");
        pinpoint.setOffsets(-84.0, -168.0, DistanceUnit.MM);
        pinpoint.setEncoderResolution(GoBildaPinpointDriver.GoBildaOdometryPods.goBILDA_4_BAR_POD);
        pinpoint.setEncoderDirections(GoBildaPinpointDriver.EncoderDirection.FORWARD, GoBildaPinpointDriver.EncoderDirection.FORWARD);
        pinpoint.resetPosAndIMU();

        telemetry.addLine("Ready. Press START.");
    }

    @Override
    public void loop() {
        for (LynxModule hub : hubs) hub.clearBulkCache();
        pinpoint.update();

        // ---- drive ----
        if (gamepad1.aWasPressed()) imu.resetYaw();
        double forward = -gamepad1.left_stick_y;
        double right = gamepad1.left_stick_x;
        double turn = gamepad1.right_stick_x;
        if (!gamepad1.left_bumper) {
            double heading = imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.RADIANS);
            double rotX = right * Math.cos(-heading) - forward * Math.sin(-heading);
            double rotY = right * Math.sin(-heading) + forward * Math.cos(-heading);
            right = rotX;
            forward = rotY;
        }
        double fl = forward + right + turn;
        double fr = forward - right - turn;
        double bl = forward - right + turn;
        double br = forward + right - turn;
        double max = Math.max(1.0, Math.max(Math.max(Math.abs(fl), Math.abs(fr)), Math.max(Math.abs(bl), Math.abs(br))));
        frontLeft.setPower(fl / max);
        frontRight.setPower(fr / max);
        backLeft.setPower(bl / max);
        backRight.setPower(br / max);

        // ---- intake ----
        intake.setPower(gamepad1.right_trigger - gamepad1.left_trigger);

        // ---- launcher ----
        if (gamepad1.yWasPressed()) launcherOn = !launcherOn;
        launcher.setVelocity(launcherOn ? LAUNCHER_TICKS_PER_SECOND : 0);
        double speed = launcher.getVelocity();
        boolean ready = launcherOn && Math.abs(speed - LAUNCHER_TICKS_PER_SECOND) < 0.03 * LAUNCHER_TICKS_PER_SECOND;
        if (ready && !launcherWasReady) gamepad1.rumbleBlips(2);
        launcherWasReady = ready;

        if (gamepad1.dpadUpWasPressed()) hoodPosition += HOOD_STEP;
        if (gamepad1.dpadDownWasPressed()) hoodPosition -= HOOD_STEP;
        hoodPosition = Range.clip(hoodPosition, 0.0, 1.0);
        hood.setPosition(hoodPosition);
        feeder.setPower(gamepad1.right_bumper && ready ? 1.0 : 0.0);

        // ---- telemetry ----
        telemetry.addData("Drive", gamepad1.left_bumper ? "robot-centric" : "field-centric");
        telemetry.addData("Heading (IMU)", "%.1f°", imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.DEGREES));
        telemetry.addData("Pinpoint", "x %.1f in  y %.1f in  h %.1f°",
                pinpoint.getPosX(DistanceUnit.INCH), pinpoint.getPosY(DistanceUnit.INCH), pinpoint.getHeading(AngleUnit.DEGREES));
        telemetry.addData("Launcher", "%s  %.0f / %.0f ticks/s %s", launcherOn ? "ON " : "off", speed,
                LAUNCHER_TICKS_PER_SECOND, ready ? "READY" : "");
        telemetry.addData("Hood", "%.2f", hoodPosition);
        telemetry.addData("Intake", "%.2f", intake.getPower());
    }

    @Override
    public void stop() {
        launcherOn = false;
    }
}
