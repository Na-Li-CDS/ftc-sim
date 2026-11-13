package ftcsim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import ftcsim.config.RobotConfig;
import ftcsim.hal.Latency;
import ftcsim.physics.FieldModel;
import ftcsim.physics.PhysicsWorld;
import ftcsim.sim.RobotRig;

/** A robot with no power must stay exactly where it was put. */
class RestDriftTest {

    private static void checkRest(String robot, double heading) throws Exception {
        RobotConfig cfg = RobotConfig.load(Path.of("robots/" + robot));
        RobotRig rig = new RobotRig(cfg, new PhysicsWorld(FieldModel.loadBiobuzz()));
        Latency.commandNanos = 0;
        rig.place(-1.0, -1.0, heading);
        for (int i = 0; i < 60000; i++) rig.step(0.001);   // one simulated minute
        double dh = Math.toDegrees(rig.body.heading() - heading);
        double dx = rig.body.position().get0() + 1.0, dy = rig.body.position().get1() + 1.0;
        System.out.printf("%s at %.0f deg: after 60 s drifted %.2f mm, %.3f deg%n", robot, Math.toDegrees(heading),
                Math.hypot(dx, dy) * 1000, dh);
        assertEquals(0, Math.hypot(dx, dy), 0.002, "position drift (m)");
        assertEquals(0, dh, 0.2, "heading drift (deg)");
    }

    @Test
    void mecanumStaysPut() throws Exception {
        checkRest("mecanum.json", 0);
        checkRest("mecanum.json", Math.toRadians(-72));
    }

    @Test
    void tankStaysPut() throws Exception {
        checkRest("tank.json", Math.toRadians(30));
    }
}
