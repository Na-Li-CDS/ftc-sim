package ftcsim.physics;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.ode4j.math.DMatrix3;
import org.ode4j.math.DVector3;
import org.ode4j.math.DVector3C;
import org.ode4j.ode.DBody;
import org.ode4j.ode.DContact;
import org.ode4j.ode.DContactBuffer;
import org.ode4j.ode.DContactJoint;
import org.ode4j.ode.DGeom;
import org.ode4j.ode.DJoint;
import org.ode4j.ode.DJointGroup;
import org.ode4j.ode.DRay;
import org.ode4j.ode.DSpace;
import org.ode4j.ode.DWorld;
import org.ode4j.ode.OdeConstants;
import org.ode4j.ode.OdeHelper;

/**
 * The rigid-body world (ODE4j): floor, perimeter walls, the HIVE structure (A-frames, axle, sign
 * board), the two HIVES on their pivots ({@link HiveBody}), the FLOWERS (open tubes, as panels),
 * plus whatever bodies are added. Every method must be called on the physics thread.
 */
public final class PhysicsWorld {

    /** Collision categories. */
    public static final long CAT_GROUND = 1, CAT_STATIC = 2, CAT_CHASSIS = 4, CAT_WHEEL = 8, CAT_BALL = 16;
    /** The intake roller: solid to balls only. */
    public static final long CAT_ROLLER = 32;
    /** Stops only the robot's chassis: keeps it from sliding under a FLOWER's rings. */
    public static final long CAT_ROBOT_STOP = 64;

    /** How a geom behaves in contacts. */
    public static final class Material {
        public final String kind;           // ground, wall, chassis, wheel, ball
        /** For wheels: traction direction in the robot frame (null = isotropic). */
        public double[] wheelTractionDir;
        public double mu = 0.5;
        public double mu2 = 0.5;
        public double bounce = 0.05;
        public Object owner;

        public Material(String kind) {
            this.kind = kind;
        }
    }

    /** A contact on the robot's chassis during the last step, in world coordinates. */
    public record ChassisContact(double x, double y, double z) {
    }

    private static boolean initialized = false;

    public final DWorld world;
    public final DSpace space;
    public final FieldModel field;
    private final DJointGroup contactGroup;
    private static final int MAX_CONTACTS = 8;
    /** Wheel creep per newton of traction force, (m/s)/N. */
    private static final double WHEEL_SLIP = 0.002;
    private final DContactBuffer contacts = new DContactBuffer(MAX_CONTACTS);
    private final Map<DGeom, Material> materials = new IdentityHashMap<>();
    private final List<ChassisContact> chassisContacts = new ArrayList<>();
    private DBody chassisBody;   // for contact bookkeeping
    private DMatrix3 robotRotation = new DMatrix3();
    /** The red and blue HIVES, in biobuzz-field.json order. */
    public final List<HiveBody> hives = new ArrayList<>();

    public PhysicsWorld(FieldModel field) {
        synchronized (PhysicsWorld.class) {
            if (!initialized) {
                OdeHelper.initODE2(0);
                initialized = true;
            }
        }
        this.field = field;
        world = OdeHelper.createWorld();
        world.setGravity(0, 0, -9.81);
        world.setERP(0.2);
        world.setCFM(1e-6);
        world.setQuickStepNumIterations(40);
        world.setContactSurfaceLayer(0.0005);
        world.setContactMaxCorrectingVel(1.0);
        space = OdeHelper.createHashSpace();
        contactGroup = OdeHelper.createJointGroup();
        buildField();
    }

    private void buildField() {
        DGeom ground = OdeHelper.createPlane(space, 0, 0, 1, 0);
        register(ground, new Material("ground"), CAT_GROUND, CAT_CHASSIS | CAT_WHEEL | CAT_BALL);

        double h = field.half, t = 0.05, wh = field.wallHeight, len = 2 * h + 2 * t;
        addStaticBox("wall", h + t / 2, 0, wh / 2, t, len, wh);
        addStaticBox("wall", -h - t / 2, 0, wh / 2, t, len, wh);
        addStaticBox("wall", 0, h + t / 2, wh / 2, len, t, wh);
        addStaticBox("wall", 0, -h - t / 2, wh / 2, len, t, wh);
        for (FieldModel.Box b : field.boxes) {
            addStaticBox("structure", b.cx(), b.cy(), b.cz(), b.sx(), b.sy(), b.sz());
        }
        for (FieldModel.Panel p : field.panels) {
            DGeom box = OdeHelper.createBox(space, p.size()[0], p.size()[1], p.size()[2]);
            box.setPosition(p.center()[0], p.center()[1], p.center()[2]);
            double[] m = p.rot();
            box.setRotation(new DMatrix3(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8]));
            // Aluminum and polycarbonate: smoother than the tiles. The ball sets the bounce.
            Material mat = new Material("structure");
            mat.mu = mat.mu2 = 0.25;
            register(box, mat, CAT_STATIC, CAT_CHASSIS | CAT_WHEEL | CAT_BALL);
        }
        for (FieldModel.Hive hive : field.hives) hives.add(new HiveBody(this, hive, field.hivePanels));
        // A FLOWER's rings are thin, and a chassis taller than the Retrieval Opening could slide
        // under them. On the field the FLOWER's frame stops the robot; this invisible post around
        // each one (square, facing the field) does that here. It stops only the chassis: balls and the intake roller, low
        // enough to reach into the Retrieval Opening, pass it.
        for (FieldModel.Flower f : field.flowers) {
            double z0 = 0.03, postLen = f.height() - z0, side = 2 * (f.innerRadius() + 0.008);
            DGeom post = OdeHelper.createBox(space, side, side, postLen);   // square: box-box contacts are reliable
            post.setPosition(f.x(), f.y(), z0 + postLen / 2);
            double c = Math.cos(f.yaw()), sn = Math.sin(f.yaw());
            post.setRotation(new DMatrix3(c, -sn, 0, sn, c, 0, 0, 0, 1));
            Material m = new Material("structure");
            m.mu = m.mu2 = 0.25;
            register(post, m, CAT_ROBOT_STOP, CAT_CHASSIS);
        }
    }

    private void addStaticBox(String kind, double x, double y, double z, double sx, double sy, double sz) {
        DGeom box = OdeHelper.createBox(space, sx, sy, sz);
        box.setPosition(x, y, z);
        Material m = new Material(kind);
        m.mu = m.mu2 = 0.3;
        register(box, m, CAT_STATIC, CAT_CHASSIS | CAT_WHEEL | CAT_BALL);
    }

    public void register(DGeom geom, Material material, long category, long collide) {
        materials.put(geom, material);
        geom.setCategoryBits(category);
        geom.setCollideBits(collide);
    }

    public void unregister(DGeom geom) {
        materials.remove(geom);
    }

    public void setChassis(DBody chassis) {
        this.chassisBody = chassis;
    }

    /** Both HIVES back at their start position. */
    public void resetHives() {
        for (HiveBody h : hives) h.reset();
    }

    /** Both HIVES at their current tilt, for CELL geometry. */
    public List<FieldModel.Hive> currentHives() {
        List<FieldModel.Hive> out = new ArrayList<>();
        for (HiveBody h : hives) out.add(h.current());
        return out;
    }

    public List<ChassisContact> chassisContacts() {
        return chassisContacts;
    }

    public void step(double dt) {
        chassisContacts.clear();
        if (chassisBody != null) robotRotation = new DMatrix3(chassisBody.getRotation());
        for (HiveBody h : hives) h.beforeStep();
        space.collide(null, this::nearCallback);
        world.quickStep(dt);
        contactGroup.empty();
    }

    private void nearCallback(Object data, DGeom g1, DGeom g2) {
        DBody b1 = g1.getBody(), b2 = g2.getBody();
        if (b1 == null && b2 == null) return;
        // Skip bodies joined by a real joint (a wheel and its chassis). Contact joints made earlier in
        // this step don't count: a ball touching a HIVE's floor must still hit the same HIVE's walls.
        if (b1 != null && b2 != null && joinedByRealJoint(b1, b2)) return;
        int n = OdeHelper.collide(g1, g2, MAX_CONTACTS, contacts.getGeomBuffer());
        if (n == 0) return;
        Material m1 = materials.get(g1), m2 = materials.get(g2);
        if (m1 == null || m2 == null) return;
        for (int i = 0; i < n; i++) {
            DContact c = contacts.get(i);
            configureSurface(c, g1, m1, m2);
            DContactJoint joint = OdeHelper.createContactJoint(world, contactGroup, c);
            joint.attach(b1, b2);
            if ((b1 != null && b1 == chassisBody && "chassis".equals(m1.kind))
                    || (b2 != null && b2 == chassisBody && "chassis".equals(m2.kind))) {
                DVector3C p = c.geom.pos;
                chassisContacts.add(new ChassisContact(p.get0(), p.get1(), p.get2()));
            }
        }
    }

    private static boolean joinedByRealJoint(DBody a, DBody b) {
        for (int i = 0; i < a.getNumJoints(); i++) {
            DJoint j = a.getJoint(i);
            if (j instanceof DContactJoint) continue;
            if (j.getBody(0) == b || j.getBody(1) == b) return true;
        }
        return false;
    }

    private void configureSurface(DContact c, DGeom g1, Material m1, Material m2) {
        Material wheel = "wheel".equals(m1.kind) ? m1 : ("wheel".equals(m2.kind) ? m2 : null);
        Material other = wheel == m1 ? m2 : m1;
        if (wheel != null && ("ground".equals(other.kind))) {
            // Force-dependent slip models tread/roller compliance: wheels creep slightly under load,
            // as real wheels do, instead of locking rigidly against each other.
            c.surface.mode = OdeConstants.dContactApprox1 | OdeConstants.dContactSoftCFM | OdeConstants.dContactSoftERP
                    | OdeConstants.dContactSlip1 | OdeConstants.dContactSlip2;
            c.surface.soft_cfm = 1e-5;
            c.surface.soft_erp = 0.3;
            c.surface.slip1 = WHEEL_SLIP;
            c.surface.slip2 = WHEEL_SLIP;
            c.surface.mu = wheel.mu;
            if (wheel.wheelTractionDir != null) {
                // Mecanum/omni rollers: grip along the roller axis (fdir1), roll freely across it.
                double rx = wheel.wheelTractionDir[0], ry = wheel.wheelTractionDir[1];
                DMatrix3 r = robotRotation;
                double wx = r.get00() * rx + r.get01() * ry;
                double wy = r.get10() * rx + r.get11() * ry;
                double wz = r.get20() * rx + r.get21() * ry;
                DVector3C nrm = c.geom.normal;
                double d = wx * nrm.get0() + wy * nrm.get1() + wz * nrm.get2();
                wx -= d * nrm.get0();
                wy -= d * nrm.get1();
                wz -= d * nrm.get2();
                double len = Math.sqrt(wx * wx + wy * wy + wz * wz);
                if (len > 1e-6) {
                    c.fdir1.set(wx / len, wy / len, wz / len);
                    c.surface.mode |= OdeConstants.dContactFDir1 | OdeConstants.dContactMu2;
                    c.surface.mu2 = wheel.mu2;
                }
            }
            return;
        }
        c.surface.mode = OdeConstants.dContactApprox1 | OdeConstants.dContactBounce
                | OdeConstants.dContactSoftCFM | OdeConstants.dContactSoftERP;
        c.surface.mu = Math.sqrt(m1.mu * m2.mu);
        // A ball meeting the intake roller doesn't bounce off it: the roller pushes it along.
        boolean roller = "roller".equals(m1.kind) || "roller".equals(m2.kind);
        c.surface.bounce = roller ? 0 : Math.max(m1.bounce, m2.bounce);
        c.surface.bounce_vel = 0.1;
        c.surface.soft_cfm = 1e-5;
        c.surface.soft_erp = 0.3;
    }

    /**
     * Casts a ray and returns the distance to the first surface hit, ignoring geoms owned by
     * {@code ignoreOwner}; {@link Double#MAX_VALUE} if nothing is within {@code maxRange}.
     */
    public double raycast(double ox, double oy, double oz, double dx, double dy, double dz,
                          double maxRange, Object ignoreOwner) {
        DRay ray = OdeHelper.createRay(null, maxRange);
        try {
            ray.set(ox, oy, oz, dx, dy, dz);
            final double[] best = {Double.MAX_VALUE};
            final DContactBuffer buf = new DContactBuffer(4);
            ray.collide2(space, null, (d, a, b) -> {
                DGeom other = a == ray ? b : a;
                Material m = materials.get(other);
                if (m == null || (ignoreOwner != null && m.owner == ignoreOwner)) return;
                int n = OdeHelper.collide(ray, other, 4, buf.getGeomBuffer());
                for (int i = 0; i < n; i++) {
                    double depth = buf.get(i).geom.depth;
                    if (depth < best[0]) best[0] = depth;
                }
            });
            return best[0] <= maxRange ? best[0] : Double.MAX_VALUE;
        } finally {
            ray.destroy();
        }
    }

    /** Body-frame point to world (helper). */
    public static DVector3 toWorld(DBody body, double x, double y, double z) {
        DVector3 out = new DVector3();
        body.getRelPointPos(x, y, z, out);
        return out;
    }
}
