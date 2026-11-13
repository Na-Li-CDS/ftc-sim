package ftcsim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import ftcsim.physics.FieldModel;
import ftcsim.runner.TeamCodeCompiler;
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
        StringBuilder sb = new StringBuilder("{\"type\":\"gamepad\",\"gamepad\":1,\"kind\":\"XBOX_360\",\"axes\":[0,0,0,0,0,0],\"buttons\":[");
        for (int i = 0; i < 18; i++) sb.append(i > 0 ? "," : "").append(i == pressed);
        return sb.append("]}").toString();
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
    void launcherSpinsUpAndRumbles() throws Exception {
        Simulator sim = start();
        sim.handle("{\"type\":\"init\",\"opmode\":\"BIOBUZZ TeleOp\"}");
        waitFor(sim, Simulator.DsState.INIT, 2000);
        sim.handle("{\"type\":\"start\"}");
        waitFor(sim, Simulator.DsState.RUNNING, 2000);
        Thread.sleep(200);
        sim.handle(buttons(3));   // Y toggles the launcher
        Thread.sleep(150);
        sim.handle(buttons(-1));
        Thread.sleep(4500);
        double tps = sim.rig().motors.get("launcher").port.velocityTicksPerSecond;
        System.out.printf("launcher: %.0f ticks/s after 4.5 s (target 2000)%n", tps);
        assertEquals(2000, tps, 60);
        assertTrue(messages.stream().anyMatch(m -> m.contains("\"type\":\"rumble\"")), "rumble sent to the controller");
        sim.handle("{\"type\":\"stop\"}");
        waitFor(sim, Simulator.DsState.IDLE, 3000);
        assertNull(sim.lastError());
        sim.shutdown();
    }
}
