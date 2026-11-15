package ftcsim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import ftcsim.physics.FieldModel;
import ftcsim.runner.TeamCodeCompiler;
import ftcsim.sim.GameElements;
import ftcsim.sim.Simulator;

/** Runs the bundled example TeamCode on the default mecanum robot. */
class ExampleOpModesTest {
    private final List<String> messages = new CopyOnWriteArrayList<>();

    private Simulator start() throws Exception {
        Path home = Path.of("").toAbsolutePath();
        TeamCodeCompiler compiler = new TeamCodeCompiler(List.of(
                new TeamCodeCompiler.SourceRoot(home.resolve("examples/teamcode"), false)), Files.createTempDirectory("ftcsim-ex"));
        Path robot = home.resolve("robots/mecanum.json");
        Simulator sim = new Simulator(FieldModel.loadBiobuzz(), robot, robot.getParent(), compiler, "test");
        sim.setOutput(new Simulator.Output() {
            @Override
            public void broadcast(String json) {
                messages.add(json);
            }

            @Override
            public void broadcastState(String json) {
            }
        });
        sim.start();
        long deadline = System.currentTimeMillis() + 60000;
        while ((sim.build() == null || sim.isBuilding()) && System.currentTimeMillis() < deadline) Thread.sleep(50);
        assertNotNull(sim.build());
        assertTrue(sim.build().ok() && sim.build().skipped().isEmpty(), sim.build().message());
        return sim;
    }

    private static void waitFor(Simulator sim, Simulator.DsState state, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (sim.dsState() != state && System.currentTimeMillis() < deadline) Thread.sleep(20);
        assertEquals(state, sim.dsState(), "Driver Station state (error: " + sim.lastError() + ")");
    }

    private static String buttons(int pressed) {
        return gamepad(0, 0, 0, pressed);
    }

    /** gamepad1 with the left stick (x right, y down as in FTC), right stick x, and one button held (-1 for none). */
    private static String gamepad(double lx, double ly, double rx, int pressed) {
        StringBuilder sb = new StringBuilder("{\"type\":\"gamepad\",\"gamepad\":1,\"kind\":\"XBOX_360\",\"axes\":[")
                .append(lx).append(',').append(ly).append(',').append(rx).append(",0,0,0],\"buttons\":[");
        for (int i = 0; i < 18; i++) sb.append(i > 0 ? "," : "").append(i == pressed);
        return sb.append("]}").toString();
    }

    /** Pushes the left stick forward for a moment and returns how far the robot moved in x and y (m). */
    private static double[] driveForward(Simulator sim) throws InterruptedException {
        double x0 = sim.rig().body.position().get0(), y0 = sim.rig().body.position().get1();
        sim.handle(gamepad(0, -0.5, 0, -1));
        Thread.sleep(500);
        sim.handle(gamepad(0, 0, 0, -1));
        Thread.sleep(500);
        return new double[]{sim.rig().body.position().get0() - x0, sim.rig().body.position().get1() - y0};
    }

    @Test
    void driveIsRobotCentricAndBackSwitchesToFieldCentric() throws Exception {
        Simulator sim = start();
        sim.handle("{\"type\":\"place\",\"x\":-1.0,\"y\":-1.3,\"h\":0}");   // open floor, facing the blue wall (+x)
        sim.handle("{\"type\":\"init\",\"opmode\":\"BIOBUZZ TeleOp\"}");
        waitFor(sim, Simulator.DsState.INIT, 2000);
        sim.handle("{\"type\":\"start\"}");
        waitFor(sim, Simulator.DsState.RUNNING, 2000);
        Thread.sleep(200);

        // Turn left about 90 degrees: the robot now faces the rear wall (+y).
        sim.handle(gamepad(0, 0, -0.4, -1));
        long deadline = System.currentTimeMillis() + 4000;
        while (Math.toDegrees(sim.rig().body.heading()) < 85 && System.currentTimeMillis() < deadline) Thread.sleep(2);
        sim.handle(gamepad(0, 0, 0, -1));
        Thread.sleep(400);
        System.out.printf("turned to %.0f deg%n", Math.toDegrees(sim.rig().body.heading()));

        double[] robot = driveForward(sim);
        System.out.printf("robot-centric forward: dx %.2f m, dy %.2f m%n", robot[0], robot[1]);
        assertTrue(robot[1] > 0.2 && Math.abs(robot[0]) < 0.1, "robot-centric: forward is toward the robot's front (+y)");

        sim.handle(buttons(8));   // BACK: field-centric
        Thread.sleep(150);
        sim.handle(buttons(-1));
        Thread.sleep(150);
        double[] field = driveForward(sim);
        System.out.printf("field-centric forward: dx %.2f m, dy %.2f m%n", field[0], field[1]);
        assertTrue(field[0] > 0.2 && Math.abs(field[1]) < 0.1, "field-centric: forward is away from the red wall (+x)");

        sim.handle("{\"type\":\"stop\"}");
        waitFor(sim, Simulator.DsState.IDLE, 3000);
        assertNull(sim.lastError());
        sim.shutdown();
    }

    @Test
    void pinpointSquareReturnsToStart() throws Exception {
        Simulator sim = start();
        double x0 = sim.rig().body.position().get0(), y0 = sim.rig().body.position().get1();
        sim.handle("{\"type\":\"init\",\"opmode\":\"Pinpoint Square\"}");
        waitFor(sim, Simulator.DsState.INIT, 2000);
        Thread.sleep(400);   // Pinpoint IMU calibration after resetPosAndIMU()
        sim.handle("{\"type\":\"start\"}");
        long deadline = System.currentTimeMillis() + 25000;
        while (messages.stream().noneMatch(m -> m.contains("ended at")) && System.currentTimeMillis() < deadline) Thread.sleep(50);
        double x = sim.rig().body.position().get0(), y = sim.rig().body.position().get1();
        double err = Math.hypot(x - x0, y - y0) / 0.0254;
        double heading = Math.toDegrees(sim.rig().body.heading());
        System.out.printf("Pinpoint Square: true end %.2f in from start, heading %.2f deg%n", err, heading);
        assertTrue(messages.stream().anyMatch(m -> m.contains("ended at")), "autonomous finished");
        assertTrue(err < 1.5, "returned to start, off by " + err + " in");
        assertEquals(0, heading, 3.0);
        sim.handle("{\"type\":\"stop\"}");
        waitFor(sim, Simulator.DsState.IDLE, 3000);
        assertNull(sim.lastError());
        assertTrue(sim.dsJson().contains("\"selected\":\"BIOBUZZ TeleOp\""), "preselectTeleOp applied: " + sim.dsJson());
        sim.shutdown();
    }

    @Test
    void launcherSpinsUpRumblesAndShootsToHiveHeight() throws Exception {
        Simulator sim = start();
        // A clear lane: facing the rear wall, left of the HIVE frame.
        sim.handle("{\"type\":\"place\",\"x\":-1.2,\"y\":-1.5,\"h\":90}");
        sim.handle("{\"type\":\"init\",\"opmode\":\"BIOBUZZ TeleOp\"}");
        waitFor(sim, Simulator.DsState.INIT, 2000);
        sim.handle("{\"type\":\"start\"}");
        waitFor(sim, Simulator.DsState.RUNNING, 2000);
        Thread.sleep(200);
        long yPressed = System.currentTimeMillis();
        sim.handle(buttons(3));   // Y toggles the launcher
        Thread.sleep(150);
        sim.handle(buttons(-1));
        // The controller rumbles when the launcher reaches speed; a driver presses RB right then.
        long deadline = System.currentTimeMillis() + 4000;
        while (messages.stream().noneMatch(m -> m.contains("\"type\":\"rumble\"")) && System.currentTimeMillis() < deadline) Thread.sleep(10);
        assertTrue(messages.stream().anyMatch(m -> m.contains("\"type\":\"rumble\"")), "rumble sent to the controller");
        System.out.printf("launcher ready %.1f s after Y%n", (System.currentTimeMillis() - yPressed) / 1000.0);
        assertTrue(System.currentTimeMillis() - yPressed < 2000, "launcher reaches speed within 2 s");

        // RB feeds the 4 pre-loaded POLLEN. Follow them until they land.
        List<GameElements.Ball> preload = new ArrayList<>();
        sim.onPhysicsThread(() -> sim.elements().balls().stream()
                .filter(b -> b.state() == GameElements.State.HELD).forEach(preload::add));
        assertEquals(4, preload.size());
        double startY = sim.rig().body.position().get1();
        sim.handle(buttons(5));
        double[] apex = new double[preload.size()], apexAt = new double[preload.size()];
        long until = System.currentTimeMillis() + 3500;
        while (System.currentTimeMillis() < until) {
            sim.onPhysicsThread(() -> {
                for (int i = 0; i < preload.size(); i++) {
                    GameElements.Ball b = preload.get(i);
                    if (b.state() != GameElements.State.FREE || b.position().get2() <= apex[i]) continue;
                    apex[i] = b.position().get2();
                    apexAt[i] = b.position().get1() - startY;
                }
            });
            Thread.sleep(5);
        }
        sim.handle(buttons(-1));
        double tps = sim.rig().motors.get("launcher").port.velocityTicksPerSecond;
        System.out.printf("launcher: %.0f ticks/s (target 1200)%n", tps);
        assertEquals(1200, tps, 36, "launcher holds speed within 3%");
        sim.onPhysicsThread(() -> assertEquals(0, sim.elements().heldCount(), "RB right after the rumble shoots all 4"));
        for (int i = 0; i < preload.size(); i++) {
            System.out.printf("shot %d: apex %.2f m high, %.2f m out%n", i + 1, apex[i], apexAt[i]);
            // HIVE CELL openings are 1.36-1.67 m above the tiles (Competition Manual 9.6.2).
            assertTrue(apex[i] > 1.4 && apex[i] < 1.9, "shot " + (i + 1) + " peaks near HIVE CELL height: " + apex[i]);
            assertTrue(apexAt[i] > 1.2 && apexAt[i] < 2.2, "shot " + (i + 1) + " peaks within the field: " + apexAt[i]);
        }
        sim.handle("{\"type\":\"stop\"}");
        waitFor(sim, Simulator.DsState.IDLE, 3000);
        assertNull(sim.lastError());
        sim.shutdown();
    }
}
