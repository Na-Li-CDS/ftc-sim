package ftcsim.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ode4j.math.DVector3;
import org.ode4j.math.DVector3C;
import org.ode4j.ode.DBody;
import org.ode4j.ode.DGeom;
import org.ode4j.ode.DMass;
import org.ode4j.ode.OdeHelper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import ftcsim.config.RobotConfig;
import ftcsim.physics.FieldModel;
import ftcsim.physics.Mechanism;
import ftcsim.physics.PhysicsWorld;
import ftcsim.physics.ServoUnit;

/**
 * The BIOBUZZ SCORING ELEMENTS: 40 yellow POLLEN (7.1 cm) and 8 red + 8 blue NECTAR (9.1 cm),
 * staged as Competition Manual section 10.3.1 describes, plus how the robot's intake, feeder and
 * launcher handle them. Every method must be called on the physics thread.
 *
 * <p>A ball is in one of three states:
 * <ul>
 *   <li>{@code FREE}: a rigid sphere in the physics world. It rolls, bounces, and is pushed by
 *       the robot and other balls.</li>
 *   <li>{@code HELD}: inside the robot (pre-load or picked up by the intake). It rides along and
 *       does not collide. At most {@link #ROBOT_CAPACITY} at once (rule G407).</li>
 *   <li>{@code STAGED}: parked where the field holds it and the simulator does not model the holder
 *       as a container: stacked in a FLOWER, in an upward-facing HIVE CELL, or in the ALLIANCE
 *       AREA waiting for the human player.</li>
 * </ul>
 */
public final class GameElements {

    /** G407: a ROBOT may not simultaneously CONTROL more than 4 SCORING ELEMENTS. */
    public static final int ROBOT_CAPACITY = 4;
    /** 10.3.1: every ROBOT starts the MATCH with 4 pre-loaded POLLEN. */
    public static final int PRELOAD = 4;

    public enum State {FREE, HELD, STAGED}

    /** One POLLEN or NECTAR. */
    public static final class Ball {
        public final int id;
        public final String kind;      // pollen, nectar
        public final String color;     // yellow, red, blue
        public final double radius;
        final DBody body;
        final DGeom geom;
        State state = State.FREE;
        /** Where a STAGED ball sits (world frame). */
        final double[] stagedAt = new double[3];

        Ball(int id, String kind, String color, double radius, DBody body, DGeom geom) {
            this.id = id;
            this.kind = kind;
            this.color = color;
            this.radius = radius;
            this.body = body;
            this.geom = geom;
        }

        public State state() {
            return state;
        }

        public DVector3C position() {
            return body.getPosition();
        }

        public DVector3C velocity() {
            return body.getLinearVel();
        }
    }

    // Intake: a ball is pulled in when its center is in this box in front of the roller (robot frame).
    private static final double INTAKE_REACH_M = 0.07;
    private static final double INTAKE_MIN_OMEGA = 20;          // rad/s at the intake motor
    private static final double INTAKE_INTERVAL_S = 0.12;
    // Feeder: one ball per interval while the feeder turns forward.
    private static final double FEED_INTERVAL_S = 0.25;
    private static final double FEED_MIN_DEG_PER_SEC = 30;
    // Launcher: a single flywheel against a hood gives the ball about half the wheel's surface speed.
    private static final double LAUNCH_EFFICIENCY = 0.5;
    private static final double LAUNCH_MIN_SPEED = 0.5;          // m/s; below this the ball stalls at the wheel
    private static final double LAUNCH_ANGLE_AT_CENTER_DEG = 50; // hood servo at 0.5
    private static final double LAUNCH_ANGLE_PER_SERVO_DEG = 25.0 / 150.0;
    private static final double LAUNCH_ANGLE_MIN_DEG = 15, LAUNCH_ANGLE_MAX_DEG = 80;
    private static final double LAUNCHER_X_M = -0.08, LAUNCHER_ABOVE_TOP_M = 0.31;

    private final FieldModel field;
    private final PhysicsWorld world;
    private final RobotRig rig;
    private final List<Ball> balls = new ArrayList<>();
    /** Balls inside the robot, in the order they will leave it (first = next to the launcher). */
    private final List<Ball> held = new ArrayList<>();
    private final Mechanism intake, launcher;
    private final ServoUnit feeder, hood;
    private final double launcherWheelRadius;
    private double sinceIntake = 1, sinceFeed = 1;
    private int launched = 0;

    public GameElements(FieldModel field, RobotRig rig) {
        this.field = field;
        this.world = rig.world;
        this.rig = rig;
        Mechanism in = null, out = null;
        for (Mechanism m : rig.mechanisms) {
            if ("intake".equals(m.device.role)) in = m;
            if ("launcher".equals(m.device.role)) out = m;
        }
        ServoUnit f = null, h = null;
        for (ServoUnit s : rig.servos) {
            if ("feeder".equals(s.device.role)) f = s;
            if ("hood".equals(s.device.role)) h = s;
        }
        intake = in;
        launcher = out;
        feeder = f;
        hood = h;
        launcherWheelRadius = out == null ? 0 : out.device.load.wheelDiameterM / 2;

        JsonObject se = field.json.getAsJsonObject("scoring_elements");
        JsonObject pollen = se.getAsJsonObject("pollen");
        JsonObject nectar = se.getAsJsonObject("nectar");
        double pr = pollen.get("diameter_m").getAsDouble() / 2, pm = pollen.get("mass_kg").getAsDouble();
        double nr = nectar.get("diameter_m").getAsDouble() / 2, nm = nectar.get("mass_kg").getAsDouble();
        int pollenCount = pollen.get("count").getAsInt();
        int nectarPerColor = nectar.get("count_per_color").getAsInt();
        for (int i = 0; i < pollenCount; i++) balls.add(create("pollen", "yellow", pr, pm));
        for (String c : new String[]{"red", "blue"}) {
            for (int i = 0; i < nectarPerColor; i++) balls.add(create("nectar", c, nr, nm));
        }
        restage();
    }

    private Ball create(String kind, String color, double r, double mass) {
        DBody body = OdeHelper.createBody(world.world);
        DMass m = OdeHelper.createMass();
        // Hollow polyethylene shell: I = 2/3 m r^2.
        double i = 2.0 / 3.0 * mass * r * r;
        m.setParameters(mass, 0, 0, 0, i, i, i, 0, 0, 0);
        body.setMass(m);
        // Foam tiles and the soft shell soak up rolling: balls coast to a stop in a few seconds.
        body.setLinearDamping(0.0008);
        body.setAngularDamping(0.0015);
        body.setAutoDisableFlag(true);
        body.setAutoDisableLinearThreshold(0.003);
        body.setAutoDisableAngularThreshold(0.03);
        body.setAutoDisableSteps(200);
        DGeom geom = OdeHelper.createSphere(world.space, r);
        geom.setBody(body);
        PhysicsWorld.Material mat = new PhysicsWorld.Material("ball");
        mat.mu = mat.mu2 = 0.6;
        mat.bounce = 0.35;
        Ball b = new Ball(balls.size(), kind, color, r, body, geom);
        mat.owner = b;
        world.register(geom, mat, PhysicsWorld.CAT_BALL, PhysicsWorld.CAT_GROUND | PhysicsWorld.CAT_STATIC
                | PhysicsWorld.CAT_CHASSIS | PhysicsWorld.CAT_WHEEL | PhysicsWorld.CAT_BALL);
        return b;
    }

    // ---- staging (Competition Manual 10.3.1) ---------------------------------------------------

    /**
     * Puts every SCORING ELEMENT where it starts a MATCH:
     * <ul>
     *   <li>POLLEN: 4 in each FLOWER, 4 in each GARDEN, 4 pre-loaded in this robot. The other three
     *       ROBOTS are absent, so their pre-loads go in the center of their LOADING ZONE against
     *       the perimeter wall (10.3.4).</li>
     *   <li>NECTAR: 3 in each upward-facing CELL of its color, 5 in each ALLIANCE AREA.</li>
     * </ul>
     */
    public void restage() {
        held.clear();
        launched = 0;
        sinceIntake = sinceFeed = 1;
        List<Ball> pollen = new ArrayList<>(), red = new ArrayList<>(), blue = new ArrayList<>();
        for (Ball b : balls) {
            if ("pollen".equals(b.kind)) pollen.add(b);
            else if ("red".equals(b.color)) red.add(b);
            else blue.add(b);
        }
        int p = 0;
        // FLOWERS: 4 stacked in each, the lowest resting on the backstop above the retrieval opening.
        JsonObject flowers = field.json.getAsJsonObject("flowers");
        for (String name : flowers.keySet()) {
            JsonObject f = flowers.getAsJsonObject(name);
            JsonArray axis = f.getAsJsonArray("axis_m");
            double backstop = f.get("backstop_height_m").getAsDouble();
            for (int i = 0; i < 4; i++) {
                Ball b = pollen.get(p++);
                stage(b, axis.get(0).getAsDouble(), axis.get(1).getAsDouble(), backstop + b.radius + i * 2 * b.radius);
            }
        }
        // GARDENS: 4 in a row along the wall.
        JsonObject gardens = field.json.getAsJsonObject("gardens");
        for (String alliance : gardens.keySet()) {
            JsonObject g = gardens.getAsJsonObject(alliance);
            double x0 = g.getAsJsonArray("x_m").get(0).getAsDouble(), x1 = g.getAsJsonArray("x_m").get(1).getAsDouble();
            double y0 = g.getAsJsonArray("y_m").get(0).getAsDouble(), y1 = g.getAsJsonArray("y_m").get(1).getAsDouble();
            boolean wallAtLowY = Math.abs(y0 + field.half) < Math.abs(y1 - field.half);
            for (int i = 0; i < 4; i++) {
                Ball b = pollen.get(p++);
                double x = x0 + (x1 - x0) * (i + 0.5) / 4;
                double y = wallAtLowY ? -field.half + b.radius + 0.002 : field.half - b.radius - 0.002;
                free(b, x, y, b.radius, 0, 0, 0);
            }
        }
        // This robot's pre-load.
        for (int i = 0; i < PRELOAD; i++) hold(pollen.get(p++));
        // Absent robots' pre-loads: 1 partner on this robot's alliance, 2 opponents.
        String mine = field.json.has("alliance") ? field.json.get("alliance").getAsString() : "red";
        JsonObject zones = field.json.getAsJsonObject("loading_zones");
        for (String alliance : zones.keySet()) {
            int robots = alliance.equals(mine) ? 1 : 2;
            JsonObject z = zones.getAsJsonObject(alliance);
            double x0 = z.getAsJsonArray("x_m").get(0).getAsDouble(), x1 = z.getAsJsonArray("x_m").get(1).getAsDouble();
            double yc = (z.getAsJsonArray("y_m").get(0).getAsDouble() + z.getAsJsonArray("y_m").get(1).getAsDouble()) / 2;
            double wallSign = Math.abs(x0 + field.half) < Math.abs(x1 - field.half) ? -1 : 1;
            int n = robots * PRELOAD, rows = n / 2;
            for (int i = 0; i < n; i++) {
                Ball b = pollen.get(p++);
                double d = 2 * b.radius + 0.002;
                double x = wallSign * (field.half - b.radius - 0.002 - (i % 2) * d);
                double y = yc + (i / 2 - (rows - 1) / 2.0) * d;
                free(b, x, y, b.radius, 0, 0, 0);
            }
        }
        // Any POLLEN left over (a field file with a different count) waits beside the field.
        for (int k = 0; p < pollen.size(); k++) stage(pollen.get(p++), 0, -field.half - 0.25 - 0.08 * k, 0.04);

        // NECTAR: 3 in each upward-facing CELL of its color, the rest in the ALLIANCE AREA.
        for (String alliance : new String[]{"red", "blue"}) {
            List<Ball> nec = "red".equals(alliance) ? red : blue;
            int n = 0;
            JsonObject hive = field.json.getAsJsonObject("hives").getAsJsonObject(alliance);
            JsonObject cells = field.json.getAsJsonObject("cells");
            String up = hive.get("start_up").getAsString();
            double phi = hive.getAsJsonObject("stops_rad").get(up + "_up").getAsDouble();
            JsonArray pivot = hive.getAsJsonArray("pivot_m");
            double along = (cells.getAsJsonArray("along_arm_m").get(0).getAsDouble()
                    + cells.getAsJsonArray("along_arm_m").get(1).getAsDouble()) / 2 * ("rear".equals(up) ? 1 : -1);
            double floor = cells.get("floor_z_m").getAsDouble();
            for (int i = 0; i < 3 && n < nec.size(); i++) {
                Ball b = nec.get(n++);
                double hx = (i - 1) * (2 * b.radius + 0.01), hy = along, hz = floor + b.radius;
                double c = Math.cos(phi), s = Math.sin(phi);
                stage(b, pivot.get(0).getAsDouble() + hx,
                        pivot.get(1).getAsDouble() + hy * c - hz * s,
                        pivot.get(2).getAsDouble() + hy * s + hz * c);
            }
            double sign = "red".equals(alliance) ? -1 : 1;
            for (int i = 0; n < nec.size(); i++) {
                Ball b = nec.get(n++);
                stage(b, sign * (field.half + 0.35), sign * (-0.4 + i * 0.12), b.radius);
            }
        }
    }

    private void stage(Ball b, double x, double y, double z) {
        b.state = State.STAGED;
        b.stagedAt[0] = x;
        b.stagedAt[1] = y;
        b.stagedAt[2] = z;
        b.body.setPosition(x, y, z);
        b.body.setLinearVel(0, 0, 0);
        b.body.setAngularVel(0, 0, 0);
        b.body.disable();
        b.geom.disable();
    }

    private void hold(Ball b) {
        b.state = State.HELD;
        b.body.setLinearVel(0, 0, 0);
        b.body.setAngularVel(0, 0, 0);
        b.body.disable();
        b.geom.disable();
        held.add(b);
    }

    private void free(Ball b, double x, double y, double z, double vx, double vy, double vz) {
        b.state = State.FREE;
        b.body.setPosition(x, y, z);
        b.body.setLinearVel(vx, vy, vz);
        b.body.setAngularVel(0, 0, 0);
        b.geom.enable();
        b.body.enable();
    }

    // ---- the robot's intake, feeder and launcher ----------------------------------------------

    /** Runs before each physics step: intake pick-up and eject, feeding the launcher. */
    public void beforeStep(double dt) {
        sinceIntake += dt;
        sinceFeed += dt;
        if (intake != null) runIntake();
        if (feeder != null && launcher != null) runFeeder();
    }

    private void runIntake() {
        double omega = intake.motorOmega();
        if (sinceIntake < INTAKE_INTERVAL_S || Math.abs(omega) < INTAKE_MIN_OMEGA) return;
        RobotConfig.Chassis c = rig.config.chassis;
        double front = c.lengthM / 2 + 0.04;   // the roller, as drawn in the 3D view
        if (omega > 0) {
            if (held.size() >= ROBOT_CAPACITY) return;
            for (Ball b : balls) {
                if (b.state != State.FREE) continue;
                DVector3 local = new DVector3();
                rig.body.chassis.getPosRelPoint(b.body.getPosition(), local);
                double heightAboveFloor = b.body.getPosition().get2();
                if (local.get0() > c.lengthM / 2 - 0.02 && local.get0() < front + b.radius + INTAKE_REACH_M
                        && Math.abs(local.get1()) < c.widthM * 0.41 && heightAboveFloor < 0.15) {
                    hold(b);
                    sinceIntake = 0;
                    return;
                }
            }
        } else if (!held.isEmpty()) {
            // Reversed: spit the last ball in back out over the roller.
            Ball b = held.remove(held.size() - 1);
            double x = front + b.radius + 0.03;
            DVector3 p = rig.body.pointToWorld(x, 0, b.radius + 0.005);
            DVector3 v = rig.body.vectorToWorld(1.0, 0, 0);
            DVector3C rv = rig.body.chassis.getLinearVel();
            free(b, p.get0(), p.get1(), p.get2(), v.get0() + rv.get0(), v.get1() + rv.get1(), 0);
            sinceIntake = 0;
        }
    }

    private void runFeeder() {
        if (held.isEmpty() || sinceFeed < FEED_INTERVAL_S || feeder.port.speedDegPerSec < FEED_MIN_DEG_PER_SEC) return;
        double gear = launcher.device.externalGearRatio <= 0 ? 1 : launcher.device.externalGearRatio;
        double speed = Math.abs(launcher.motorOmega()) / gear * launcherWheelRadius * LAUNCH_EFFICIENCY;
        if (speed < LAUNCH_MIN_SPEED) return;   // the ball sits against a stopped flywheel
        Ball b = held.remove(0);
        double elev = Math.toRadians(launchAngleDeg());
        RobotConfig.Chassis c = rig.config.chassis;
        double top = c.groundClearanceM + c.frameHeightM;
        double ex = LAUNCHER_X_M + 0.07 * Math.cos(elev), ez = top + LAUNCHER_ABOVE_TOP_M + 0.07 * Math.sin(elev);
        DVector3 p = rig.body.pointToWorld(ex, 0, ez);
        DVector3 v = rig.body.vectorToWorld(speed * Math.cos(elev), 0, speed * Math.sin(elev));
        DVector3C rv = rig.body.chassis.getLinearVel();
        free(b, p.get0(), p.get1(), p.get2(), v.get0() + rv.get0(), v.get1() + rv.get1(), v.get2() + rv.get2());
        sinceFeed = 0;
        launched++;
    }

    /** Launch elevation above horizontal set by the hood servo (50° at position 0.5). */
    public double launchAngleDeg() {
        double a = LAUNCH_ANGLE_AT_CENTER_DEG + (hood == null ? 0 : hood.angleDeg() * LAUNCH_ANGLE_PER_SERVO_DEG);
        return Math.max(LAUNCH_ANGLE_MIN_DEG, Math.min(LAUNCH_ANGLE_MAX_DEG, a));
    }

    /** Puts one ball at rest on the tiles at (x, y), out of the robot or its staging spot. */
    public void placeBall(Ball b, double x, double y) {
        held.remove(b);
        free(b, x, y, b.radius, 0, 0, 0);
    }

    // ---- views ---------------------------------------------------------------------------------

    public List<Ball> balls() {
        return balls;
    }

    public int heldCount() {
        return held.size();
    }

    public int launchedCount() {
        return launched;
    }

    /** Ball kinds for the browser, once per connection. */
    public List<Map<String, Object>> describe() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Ball b : balls) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", b.kind);
            m.put("color", b.color);
            m.put("r", b.radius);
            out.add(m);
        }
        return out;
    }

    /** x, y, z of every ball (mm resolution), in {@link #balls()} order. HELD balls ride on the robot. */
    public double[] positions() {
        double[] out = new double[balls.size() * 3];
        RobotConfig.Chassis c = rig.config.chassis;
        double top = c.groundClearanceM + c.frameHeightM;
        for (Ball b : balls) {
            double x, y, z;
            if (b.state == State.HELD) {
                int i = held.indexOf(b);
                DVector3 p = rig.body.pointToWorld(0.12 - i * 0.075, 0, top + b.radius + 0.01 + i * 0.03);
                x = p.get0();
                y = p.get1();
                z = p.get2();
            } else if (b.state == State.STAGED) {
                x = b.stagedAt[0];
                y = b.stagedAt[1];
                z = b.stagedAt[2];
            } else {
                DVector3C p = b.body.getPosition();
                x = p.get0();
                y = p.get1();
                z = p.get2();
            }
            out[b.id * 3] = Math.round(x * 1000) / 1000.0;
            out[b.id * 3 + 1] = Math.round(y * 1000) / 1000.0;
            out[b.id * 3 + 2] = Math.round(z * 1000) / 1000.0;
        }
        return out;
    }
}
