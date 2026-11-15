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
import ftcsim.physics.RobotBody;
import ftcsim.physics.ServoUnit;

/**
 * The BIOBUZZ SCORING ELEMENTS: 40 yellow POLLEN (7.1 cm) and 8 red + 8 blue NECTAR (9.1 cm),
 * staged as Competition Manual section 10.3.1 describes, plus how the robot's intake, feeder and
 * launcher handle them. Every method must be called on the physics thread.
 *
 * <p>A ball is in one of three states:
 * <ul>
 *   <li>{@code FREE}: a rigid sphere in the physics world. It rolls, bounces, is pushed by the
 *       robot and other balls, and can land in (or bounce off) a HIVE CELL.</li>
 *   <li>{@code HELD}: inside the robot (pre-load or picked up by the intake). It rides along and
 *       does not collide. At most {@link #ROBOT_CAPACITY} at once (rule G407).</li>
 *   <li>{@code STAGED}: waiting off the field, in the ALLIANCE AREA for the human player.</li>
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
        final double mass;
        /** 1/2 rho Cd A: air drag force = dragFactor * speed^2. */
        final double dragFactor;
        final DBody body;
        final DGeom geom;
        State state = State.FREE;
        /** Rolling out of a FLOWER's Retrieval Opening: direction (radians) and time left, s. */
        double flowerExitDir, flowerExitTime;
        boolean flowerBounced;
        /** Where a STAGED ball sits (world frame). */
        final double[] stagedAt = new double[3];

        Ball(int id, String kind, String color, double radius, double mass, DBody body, DGeom geom) {
            this.id = id;
            this.kind = kind;
            this.color = color;
            this.radius = radius;
            this.mass = mass;
            this.dragFactor = 0.5 * AIR_DENSITY * DRAG_COEFFICIENT * Math.PI * radius * radius;
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

    // In the air: quadratic drag. POLLEN and NECTAR are perforated plastic balls; Cd ~0.5 is an estimate.
    private static final double AIR_DENSITY = 1.2;               // kg/m^3
    private static final double DRAG_COEFFICIENT = 0.5;
    // On the foam tiles: rolling resistance, a force of ROLLING_RESISTANCE * m * g against the motion.
    // Perforated plastic balls on soft foam slow down quickly: a POLLEN rolling at 1 m/s stops in
    // about 40 cm. (At 0.04 one bumped by a robot at full speed rolled about 5 m.)
    private static final double ROLLING_RESISTANCE = 0.12;
    private static final double GRAVITY = 9.81;
    // Intake: the spinning roller pulls in a ball that touches it (within this gap of its surface),
    // across most of the robot's width. The roller is solid (RobotBody), so balls in front of it get
    // pushed until they touch; through a FLOWER's Retrieval Opening it reaches the bottom POLLEN,
    // and each one above as it drops, but not balls higher up the tube.
    private static final double INTAKE_REACH_M = 0.05;
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
        // No velocity damping: it would also slow balls in flight. Air drag and rolling resistance
        // are applied as forces in applyAirAndRolling(); only spin decays here.
        body.setLinearDamping(0);
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
        Ball b = new Ball(balls.size(), kind, color, r, mass, body, geom);
        mat.owner = b;
        world.register(geom, mat, PhysicsWorld.CAT_BALL, PhysicsWorld.CAT_GROUND | PhysicsWorld.CAT_STATIC
                | PhysicsWorld.CAT_CHASSIS | PhysicsWorld.CAT_WHEEL | PhysicsWorld.CAT_BALL);
        return b;
    }

    // ---- staging (Competition Manual 10.3.1) ---------------------------------------------------

    /**
     * Puts every SCORING ELEMENT where it starts a MATCH:
     * <ul>
     *   <li>POLLEN: 4 stacked in each FLOWER (free balls, held by its tube), 4 in each GARDEN, 4
     *       pre-loaded in this robot. The other three
     *       ROBOTS are absent, so their pre-loads go in the center of their LOADING ZONE against
     *       the perimeter wall (10.3.4).</li>
     *   <li>NECTAR: 3 in each upward-facing CELL of its color (free balls, held by the CELL's
 *       walls), 5 in each ALLIANCE AREA.</li>
     * </ul>
     */
    public void restage() {
        world.resetHives();
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
        // FLOWERS: 4 stacked in each. The lowest sits in the 2.79 in hole of the bottom ring, on the
        // tiles (9.7); the others rest on top of it. (The 1.25 in backstop is the guide at the top.)
        for (FieldModel.Flower f : field.flowers) {
            for (int i = 0; i < 4; i++) {
                Ball b = pollen.get(p++);
                free(b, f.x(), f.y(), b.radius + i * (2 * b.radius + 0.001), 0, 0, 0);
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
            FieldModel.Hive hive = field.hives.stream().filter(h -> h.alliance().equals(alliance)).findFirst().orElseThrow();
            // Side by side against the closed inner end, resting on the CELL floor.
            double sign = "rear".equals(hive.upCell()) ? 1 : -1;
            for (int i = 0; i < 3 && n < nec.size(); i++) {
                Ball b = nec.get(n++);
                double[] w = hive.toWorld((i - 1) * (2 * b.radius + 0.01), sign * (field.cellInner + b.radius + 0.002),
                        field.cellFloor + b.radius + 0.001);
                free(b, w[0], w[1], w[2], 0, 0, 0);
            }
            double wall = "red".equals(alliance) ? -1 : 1;
            for (int i = 0; n < nec.size(); i++) {
                Ball b = nec.get(n++);
                stage(b, wall * (field.half + 0.35), wall * (-0.4 + i * 0.12), b.radius);
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
        wakeNeighbors(b);
        b.state = State.HELD;
        b.body.setLinearVel(0, 0, 0);
        b.body.setAngularVel(0, 0, 0);
        b.body.disable();
        b.geom.disable();
        held.add(b);
    }

    /**
     * Wakes resting balls near this one before it leaves. The physics engine puts still balls to
     * sleep and does not wake them when what they rest on disappears: without this, taking the
     * bottom POLLEN out of a FLOWER would leave the stack above it floating.
     */
    private void wakeNeighbors(Ball b) {
        DVector3C p = b.body.getPosition();
        for (Ball o : balls) {
            if (o == b || o.state != State.FREE || o.body.isEnabled()) continue;
            DVector3C q = o.body.getPosition();
            double dx = q.get0() - p.get0(), dy = q.get1() - p.get1(), dz = q.get2() - p.get2();
            if (dx * dx + dy * dy + dz * dz < 0.5 * 0.5) o.body.enable();
        }
    }

    private void free(Ball b, double x, double y, double z, double vx, double vy, double vz) {
        b.state = State.FREE;
        b.flowerBounced = false;
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
        applyAirAndRolling();
        bounceOutOfFlowers(dt);
        if (intake != null) runIntake();
        if (feeder != null && launcher != null) runFeeder();
    }

    // A POLLEN that drops out of a FLOWER's middle ring rolls out of the Retrieval Opening.
    private static final double FLOWER_DROP_SPEED = 0.2;       // m/s downward: it is falling, not resting
    private static final double FLOWER_ROLL_OUT_SPEED = 0.5;  // m/s
    private static final double FLOWER_EXIT_TIME_S = 0.5;
    private final java.util.Random bounceRandom = new java.util.Random(7);

    /**
     * On the field, when the bottom POLLEN of a FLOWER is taken or knocked out, the ones above drop
     * through the tall Retrieval Opening and roll out on their own, so the whole stack comes out;
     * only the weight of the stack keeps the bottom one in place at the start. Simulated POLLEN are
     * perfect spheres and fall straight, so each lands back in the lower ring and the next one,
     * falling right behind it, pins it there. Real ones are not quite round (Competition Manual
     * 9.8), come off the middle ring at an angle and keep rolling: a POLLEN that falls clear of the
     * middle ring rolls toward the open front or sides (never the wall) at a walking pace until it
     * is out of the lower ring, even with the next one landing on it. One resting in place, like
     * the bottom one at the start, does not move.
     */
    private void bounceOutOfFlowers(double dt) {
        for (Ball b : balls) {
            if (b.state != State.FREE || !b.body.isEnabled()) continue;
            DVector3C p = b.body.getPosition(), v = b.body.getLinearVel();
            FieldModel.Flower f = flowerHolding(p.get0(), p.get1(), 0.075);
            if (f == null) {
                b.flowerExitTime = 0;
                b.flowerBounced = false;
                continue;
            }
            double clear = f.retrievalTop() - b.radius;   // its top has left the middle ring
            if (p.get2() > clear) {
                b.flowerBounced = false;
                continue;
            }
            if (!b.flowerBounced && v.get2() < -FLOWER_DROP_SPEED) {
                b.flowerBounced = true;
                b.flowerExitTime = FLOWER_EXIT_TIME_S;
                b.flowerExitDir = f.yaw() + (bounceRandom.nextDouble() * 2 - 1) * Math.toRadians(100);   // front or sides
            }
            if (b.flowerExitTime > 0) {
                b.flowerExitTime -= dt;
                double cx = Math.cos(b.flowerExitDir), cy = Math.sin(b.flowerExitDir);
                double along = v.get0() * cx + v.get1() * cy;
                if (along < FLOWER_ROLL_OUT_SPEED) {
                    double dv = FLOWER_ROLL_OUT_SPEED - along;
                    b.body.setLinearVel(v.get0() + dv * cx, v.get1() + dv * cy, v.get2());
                }
            }
        }
    }

    /** The FLOWER within this distance (m, horizontally) of the point, or null. */
    private FieldModel.Flower flowerHolding(double x, double y, double within) {
        for (FieldModel.Flower f : field.flowers) {
            if (Math.hypot(x - f.x(), y - f.y()) < within) return f;
        }
        return null;
    }

    /** Air drag on every moving free ball, plus rolling resistance while it is on the tiles. */
    private void applyAirAndRolling() {
        for (Ball b : balls) {
            if (b.state != State.FREE || !b.body.isEnabled()) continue;
            DVector3C v = b.body.getLinearVel();
            double vx = v.get0(), vy = v.get1(), vz = v.get2();
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (speed > 1e-4) {
                double k = b.dragFactor * speed;
                b.body.addForce(-k * vx, -k * vy, -k * vz);
            }
            double horizontal = Math.hypot(vx, vy);
            if (b.body.getPosition().get2() < b.radius + 0.003 && horizontal > 1e-4) {
                // Full force above 2 cm/s, fading to zero below so a ball comes to rest without jitter.
                double k = ROLLING_RESISTANCE * b.mass * GRAVITY / Math.max(horizontal, 0.02);
                b.body.addForce(-k * vx, -k * vy, 0);
            }
        }
    }

    private void runIntake() {
        double omega = intake.motorOmega();
        if (sinceIntake < INTAKE_INTERVAL_S || Math.abs(omega) < INTAKE_MIN_OMEGA) return;
        RobotConfig.Chassis c = rig.config.chassis;
        double front = c.lengthM / 2 + RobotBody.ROLLER_AHEAD_M;   // the roller's axis
        if (omega > 0) {
            if (held.size() >= ROBOT_CAPACITY) return;
            for (Ball b : balls) {
                if (b.state != State.FREE) continue;
                DVector3 local = new DVector3();
                rig.body.chassis.getPosRelPoint(b.body.getPosition(), local);
                double dx = local.get0() - front, dz = b.body.getPosition().get2() - RobotBody.ROLLER_HEIGHT_M;
                boolean touching = Math.hypot(dx, dz) < RobotBody.ROLLER_RADIUS_M + b.radius + INTAKE_REACH_M;
                if (touching && local.get0() > c.lengthM / 2 - 0.02 && Math.abs(local.get1()) < c.widthM * 0.41) {
                    hold(b);
                    sinceIntake = 0;
                    return;
                }
            }
        } else if (!held.isEmpty()) {
            // Reversed: spit the last ball in back out over the roller.
            Ball b = held.remove(held.size() - 1);
            double x = front + RobotBody.ROLLER_RADIUS_M + b.radius + 0.01;   // just clear of the roller
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
        if (b.state == State.FREE) wakeNeighbors(b);
        held.remove(b);
        free(b, x, y, b.radius, 0, 0, 0);
    }

    // ---- the human player ------------------------------------------------------------------------

    /** NECTAR of this color still waiting in its ALLIANCE AREA (outside the field). */
    public int waitingNectar(String color) {
        int n = 0;
        for (Ball b : balls) if ("nectar".equals(b.kind) && color.equals(b.color) && b.state == State.STAGED) n++;
        return n;
    }

    /**
     * The human player puts one NECTAR of this color ("red" or "blue") from its ALLIANCE AREA onto
     * the field, at rest in that alliance's LOADING ZONE: the first free spot along the alliance
     * wall, clear of other balls and the robot; past the zone's inner edge if the zone is full.
     *
     * @return false if none of that color is waiting or there is no free spot
     */
    public boolean introduceNectar(String color) {
        Ball b = null;
        for (Ball x : balls) {
            if ("nectar".equals(x.kind) && color.equals(x.color) && x.state == State.STAGED) {
                b = x;
                break;
            }
        }
        if (b == null) return false;
        JsonObject z = field.json.getAsJsonObject("loading_zones").getAsJsonObject(color);
        if (z == null) return false;
        double x0 = z.getAsJsonArray("x_m").get(0).getAsDouble(), x1 = z.getAsJsonArray("x_m").get(1).getAsDouble();
        double y0 = z.getAsJsonArray("y_m").get(0).getAsDouble(), y1 = z.getAsJsonArray("y_m").get(1).getAsDouble();
        double wallSign = Math.abs(x0 + field.half) < Math.abs(x1 - field.half) ? -1 : 1;
        double yc = (y0 + y1) / 2, halfLen = (y1 - y0) / 2, r = b.radius, step = 2 * r + 0.01;
        // Columns from the wall inward; in each, spots from the zone's middle outward.
        for (int col = 0; col < 8; col++) {
            double x = wallSign * (field.half - r - 0.003 - col * step);
            for (int k = 0; k <= 2 * (int) (halfLen / step) + 1; k++) {
                double y = yc + (k % 2 == 0 ? 1 : -1) * ((k + 1) / 2) * step;
                if (Math.abs(y - yc) > halfLen - r) continue;
                if (spotIsFree(x, y, r)) {
                    free(b, x, y, r, 0, 0, 0);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean spotIsFree(double x, double y, double r) {
        for (Ball o : balls) {
            if (o.state != State.FREE) continue;
            DVector3C p = o.body.getPosition();
            if (p.get2() < 0.3 && Math.hypot(p.get0() - x, p.get1() - y) < r + o.radius + 0.005) return false;
        }
        // The robot: test the spot in the chassis frame against its footprint plus a margin.
        DVector3 local = new DVector3();
        rig.body.chassis.getPosRelPoint(x, y, r, local);
        RobotConfig.Chassis c = rig.config.chassis;
        return Math.abs(local.get0()) > c.lengthM / 2 + 0.06 + r || Math.abs(local.get1()) > c.widthM / 2 + 0.03 + r;
    }

    /**
     * Puts a ball anywhere with any velocity, as if thrown: out of the robot or its staging spot.
     * For tests and experiments (e.g. checking a shot into a CELL without driving there).
     */
    public void throwBall(Ball b, double x, double y, double z, double vx, double vy, double vz) {
        if (b.state == State.FREE) wakeNeighbors(b);
        held.remove(b);
        free(b, x, y, z, vx, vy, vz);
    }

    /**
     * Free balls inside each CELL, keyed "red rear", "red audience", "blue rear", "blue audience".
     * This is just where the balls are, not a MATCH score.
     */
    public Map<String, Integer> cellCounts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (FieldModel.Hive h : field.hives) {
            out.put(h.alliance() + " rear", 0);
            out.put(h.alliance() + " audience", 0);
        }
        List<FieldModel.Hive> hives = world.currentHives();
        for (Ball b : balls) {
            if (b.state != State.FREE) continue;
            DVector3C p = b.body.getPosition();
            String cell = field.cellAt(p.get0(), p.get1(), p.get2(), hives);
            if (cell != null) out.merge(cell, 1, Integer::sum);
        }
        return out;
    }

    /** Free balls inside each FLOWER, keyed by FLOWER name (flower_red, flower_rear...). */
    public Map<String, Integer> flowerCounts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (FieldModel.Flower f : field.flowers) out.put(f.name(), 0);
        for (Ball b : balls) {
            String f = flowerOf(b);
            if (f != null) out.merge(f, 1, Integer::sum);
        }
        return out;
    }

    /** The FLOWER a ball is in, or null. */
    public String flowerOf(Ball b) {
        if (b.state != State.FREE) return null;
        DVector3C p = b.body.getPosition();
        return field.flowerAt(p.get0(), p.get1(), p.get2());
    }

    /** The CELL a ball is in ("red audience"...), or null. */
    public String cellOf(Ball b) {
        if (b.state != State.FREE) return null;
        DVector3C p = b.body.getPosition();
        List<FieldModel.Hive> hives = world.currentHives();
        return field.cellAt(p.get0(), p.get1(), p.get2(), hives);
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
