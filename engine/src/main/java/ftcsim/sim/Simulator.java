package ftcsim.sim;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ode4j.math.DQuaternionC;
import org.ode4j.math.DVector3C;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.RobotLog;

import ftcsim.config.RobotConfig;
import ftcsim.hal.GamepadAccess;
import ftcsim.hal.GamepadEffects;
import ftcsim.hal.HalSession;
import ftcsim.hal.MotorPort;
import ftcsim.hal.OpModeSession;
import ftcsim.hal.ServoPort;
import ftcsim.hal.SimHub;
import ftcsim.hal.SimLog;
import ftcsim.hal.SimTelemetry;
import ftcsim.physics.FieldModel;
import ftcsim.physics.Mechanism;
import ftcsim.physics.PhysicsWorld;
import ftcsim.physics.RobotBody;
import ftcsim.physics.ServoUnit;
import ftcsim.runner.OpModeInfo;
import ftcsim.runner.TeamCodeCompiler;

/**
 * The simulated Robot Controller: physics at 1 kHz in real time, the OpMode lifecycle as the
 * Driver Station drives it, and the state stream for the browser.
 */
public final class Simulator {

    /** Where outgoing messages go (the web server). */
    public interface Output {
        void broadcast(String json);

        void broadcastState(String json);
    }

    public enum DsState {IDLE, INIT, RUNNING, STOPPING}

    private static final double DT = 0.001;
    private static final double AUTO_PERIOD_S = 30.0;

    private final Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
    private final FieldModel field;
    private final Path robotsDir;
    private final TeamCodeCompiler compiler;
    private final String projectLabel;
    private Output output = new Output() {
        @Override
        public void broadcast(String json) {
        }

        @Override
        public void broadcastState(String json) {
        }
    };

    // physics (physics thread only, except construction)
    private volatile RobotRig rig;
    private volatile Path robotFile;
    private final ConcurrentLinkedQueue<Runnable> physicsTasks = new ConcurrentLinkedQueue<>();
    private volatile boolean running = false;
    private volatile double realTimeFactor = 1.0;
    private double simTime = 0;

    // driver station
    public final Gamepad gamepad1 = new Gamepad();
    public final Gamepad gamepad2 = new Gamepad();
    private final Object lock = new Object();
    private DsState state = DsState.IDLE;
    private OpModeSession session;
    private HalSession hal;
    private SimTelemetry telemetry;
    private OpModeInfo active;
    private String selected;
    private long initNanos, startNanos;
    private boolean autoTimerEnabled = true;
    private String lastError;
    /** A robot file that has the device the last error was missing; offered as a one-click switch. */
    private String fixRobot;
    private String globalWarning = "";

    // builds
    private final ExecutorService buildExecutor = Executors.newSingleThreadExecutor(r -> daemon(r, "teamcode-build"));
    private volatile TeamCodeCompiler.Result build;
    private volatile boolean building = false;
    private volatile boolean buildPending = false;

    private final ConcurrentLinkedQueue<Map<String, Object>> logQueue = new ConcurrentLinkedQueue<>();

    public Simulator(FieldModel field, Path robotFile, Path robotsDir, TeamCodeCompiler compiler, String projectLabel) throws IOException {
        this.field = field;
        this.robotsDir = robotsDir;
        this.compiler = compiler;
        this.projectLabel = projectLabel;
        this.robotFile = robotFile;
        this.rig = new RobotRig(RobotConfig.load(robotFile), new PhysicsWorld(field));
        GamepadAccess.get().setUser(gamepad1, 1);
        GamepadAccess.get().setUser(gamepad2, 2);
        installListeners();
    }

    public void setOutput(Output output) {
        this.output = output;
    }

    private void installListeners() {
        SimLog.setListener(new SimLog.Listener() {
            @Override
            public void onLog(char level, String tag, String message) {
                System.out.println(level + "/" + tag + ": " + message);
                if (level == 'V' || level == 'D') return;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "log");
                m.put("level", String.valueOf(level));
                m.put("tag", tag);
                m.put("msg", message);
                if (logQueue.size() < 500) logQueue.add(m);
            }

            @Override
            public void onGlobalError(String message) {
                if (message != null && !message.isEmpty()) setError(message);
            }

            @Override
            public void onGlobalWarning(String message) {
                globalWarning = message == null ? "" : message;
                broadcastDs();
            }
        });
        GamepadEffects.setListener(new GamepadEffects.Listener() {
            @Override
            public void onRumble(int user, double[] strong, double[] weak, int[] durationsMs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "rumble");
                m.put("gamepad", user);
                m.put("strong", strong);
                m.put("weak", weak);
                m.put("durations", durationsMs);
                output.broadcast(gson.toJson(m));
            }

            @Override
            public void onLed(int user, double r, double g, double b, int durationMs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "led");
                m.put("gamepad", user);
                m.put("rgb", new double[]{r, g, b});
                output.broadcast(gson.toJson(m));
            }
        });
    }

    // ---- lifecycle -------------------------------------------------------------------------------

    public void start() {
        running = true;
        Thread physics = new Thread(this::physicsLoop, "physics");
        physics.setDaemon(true);
        physics.setPriority(Thread.MAX_PRIORITY);
        physics.start();
        Thread watcher = new Thread(this::watchSources, "source-watcher");
        watcher.setDaemon(true);
        watcher.start();
        requestBuild();
    }

    public void shutdown() {
        stopOpMode();
        running = false;
    }

    private void physicsLoop() {
        long t0 = System.nanoTime();
        long steps = 0;
        long lastPublish = 0, lastDevices = 0;
        long rtfWindowStart = t0;
        long rtfSteps = 0;
        while (running) {
            Runnable task;
            while ((task = physicsTasks.poll()) != null) {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    RobotLog.ee("Simulator", e, "physics task failed");
                }
            }
            long now = System.nanoTime();
            long due = (now - t0) / 1_000_000L;
            if (due - steps > 250) steps = due - 250;     // badly behind: let simulated time slip
            int n = 0;
            RobotRig r = rig;
            while (steps < due && n < 100) {
                r.step(DT);
                simTime += DT;
                steps++;
                n++;
                rtfSteps++;
            }
            if (now - rtfWindowStart > 1_000_000_000L) {
                realTimeFactor = rtfSteps * DT / ((now - rtfWindowStart) / 1e9);
                rtfWindowStart = now;
                rtfSteps = 0;
            }
            if (now - lastPublish >= 20_000_000L) {
                lastPublish = now;
                publishState(r, now);
                checkAutoTimer(now);
            }
            if (now - lastDevices >= 100_000_000L) {
                lastDevices = now;
                publishDevices(r);
            }
            LockSupport.parkNanos(400_000L);
        }
    }

    // ---- builds ----------------------------------------------------------------------------------

    public void requestBuild() {
        synchronized (lock) {
            if (state != DsState.IDLE) {
                buildPending = true;
                broadcastBuild();
                return;
            }
            buildPending = false;
        }
        building = true;
        broadcastBuild();
        buildExecutor.submit(() -> {
            try {
                TeamCodeCompiler.Result r = compiler.compile();
                build = r;
                System.out.println("TeamCode: " + r.message());
                for (TeamCodeCompiler.Skipped s : r.skipped()) System.out.println("  skipped " + s.file() + " - " + s.reason());
                synchronized (lock) {
                    if (selected == null || r.opModes().stream().noneMatch(o -> o.name().equals(selected))) {
                        selected = r.opModes().stream().filter(o -> !o.disabled()).map(OpModeInfo::name).findFirst().orElse(null);
                    }
                }
            } catch (Throwable t) {
                build = new TeamCodeCompiler.Result(false, "Build crashed: " + t, null, List.of(), List.of(), 0, 0);
            } finally {
                building = false;
                broadcastBuild();
                broadcastDs();
            }
        });
    }

    private void watchSources() {
        String last = compiler.fingerprint();
        while (running) {
            LockSupport.parkNanos(1_000_000_000L);
            String now = compiler.fingerprint();
            if (!now.equals(last)) {
                LockSupport.parkNanos(400_000_000L);    // let the editor finish writing
                last = compiler.fingerprint();
                RobotLog.ii("Simulator", "TeamCode changed; rebuilding");
                requestBuild();
            }
        }
    }

    // ---- driver station commands ------------------------------------------------------------------

    /** Handles one message from a browser Driver Station. */
    public void handle(String text) {
        JsonObject msg;
        try {
            msg = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        String type = msg.has("type") ? msg.get("type").getAsString() : "";
        switch (type) {
            case "gamepad" -> applyGamepad(msg);
            case "select" -> select(msg.get("opmode").getAsString());
            case "init" -> initOpMode(msg.has("opmode") ? msg.get("opmode").getAsString() : selected);
            case "start" -> startOpMode();
            case "stop" -> stopOpMode();
            case "build" -> requestBuild();
            case "timer" -> {
                synchronized (lock) {
                    autoTimerEnabled = msg.get("enabled").getAsBoolean();
                }
                broadcastDs();
            }
            case "place" -> {
                double x = msg.get("x").getAsDouble(), y = msg.get("y").getAsDouble(), h = Math.toRadians(msg.get("h").getAsDouble());
                physicsTasks.add(() -> rig.place(clamp(x, field.half - 0.23), clamp(y, field.half - 0.23), h));
            }
            case "reset" -> resetRobot();
            case "robot" -> switchRobot(msg.get("file").getAsString());
            case "clearError" -> {
                synchronized (lock) {
                    lastError = null;
                    fixRobot = null;
                }
                RobotLog.clearGlobalErrorMsg();
                broadcastDs();
            }
            default -> {
            }
        }
    }

    private static double clamp(double v, double max) {
        return Math.max(-max, Math.min(max, v));
    }

    private void applyGamepad(JsonObject msg) {
        int slot = msg.get("gamepad").getAsInt();
        Gamepad gp = slot == 2 ? gamepad2 : gamepad1;
        JsonArray axes = msg.getAsJsonArray("axes");
        JsonArray buttons = msg.getAsJsonArray("buttons");
        boolean[] b = new boolean[18];
        for (int i = 0; i < b.length && i < buttons.size(); i++) b[i] = buttons.get(i).getAsBoolean();
        Gamepad.Type t;
        try {
            t = Gamepad.Type.valueOf(msg.has("kind") ? msg.get("kind").getAsString() : "UNKNOWN");
        } catch (IllegalArgumentException e) {
            t = Gamepad.Type.UNKNOWN;
        }
        GamepadAccess.get().update(gp, t, axes.get(0).getAsFloat(), axes.get(1).getAsFloat(),
                axes.get(2).getAsFloat(), axes.get(3).getAsFloat(), axes.get(4).getAsFloat(), axes.get(5).getAsFloat(), b);
    }

    /** All Driver Stations disconnected: like losing the DS on a real robot, stop the OpMode. */
    public void onAllClientsDisconnected() {
        boolean[] none = new boolean[18];
        GamepadAccess.get().update(gamepad1, gamepad1.type, 0, 0, 0, 0, 0, 0, none);
        GamepadAccess.get().update(gamepad2, gamepad2.type, 0, 0, 0, 0, 0, 0, none);
        stopOpMode();
    }

    private void select(String name) {
        synchronized (lock) {
            if (state == DsState.IDLE) selected = name;
        }
        broadcastDs();
    }

    private void initOpMode(String name) {
        synchronized (lock) {
            if (state != DsState.IDLE || name == null) return;
            TeamCodeCompiler.Result b = build;
            OpModeInfo info = b == null ? null : b.opModes().stream().filter(o -> o.name().equals(name)).findFirst().orElse(null);
            if (info == null) {
                lastError = "OpMode \"" + name + "\" is not available. Check the build output.";
                broadcastDsLocked();
                return;
            }
            OpMode opMode;
            try {
                opMode = (OpMode) info.type().getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                Throwable cause = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
                lastError = "Could not create " + info.className() + ": " + describe(cause);
                RobotLog.ee("Simulator", cause, "OpMode construction failed");
                broadcastDsLocked();
                return;
            }
            RobotRig r = rig;
            r.resetForOpMode();
            hal = new HalSession();
            HardwareMap hw = r.createHardwareMap(hal);
            telemetry = new SimTelemetry();
            gamepad1.resetEdgeDetection();
            gamepad2.resetEdgeDetection();
            active = info;
            selected = info.name();
            lastError = null;
            fixRobot = null;
            RobotLog.clearGlobalErrorMsg();
            state = DsState.INIT;
            initNanos = System.nanoTime();
            final OpModeSession s = new OpModeSession(info.name(), opMode, hw, gamepad1, gamepad2, telemetry, sessionListener);
            session = s;
            RobotLog.i(RobotLog.OPMODE_START_TAG);
            RobotLog.ii("Simulator", "INIT " + info.name() + " (" + info.className() + ")");
            s.init();
            broadcastDsLocked();
        }
    }

    private void startOpMode() {
        synchronized (lock) {
            if (state != DsState.INIT || session == null) return;
            state = DsState.RUNNING;
            startNanos = System.nanoTime();
            RobotLog.ii("Simulator", "START " + active.name());
            session.start();
            broadcastDsLocked();
        }
    }

    public void stopOpMode() {
        final OpModeSession s;
        synchronized (lock) {
            if ((state != DsState.INIT && state != DsState.RUNNING) || session == null) return;
            state = DsState.STOPPING;
            s = session;
            RobotLog.ii("Simulator", "STOP " + active.name());
            broadcastDsLocked();
        }
        Thread t = new Thread(() -> {
            boolean ended = s.stop(1000);
            if (!ended) {
                setError("The OpMode \"" + s.getName() + "\" did not stop within a second (stuck in a loop that ignores "
                        + "isStopRequested()/opModeIsActive()?). On a robot the app would restart; here its thread "
                        + "is abandoned and disconnected from the hardware.");
            }
            finishSession(s);
        }, "opmode-stopper");
        t.setDaemon(true);
        t.start();
    }

    private final OpModeSession.Listener sessionListener = new OpModeSession.Listener() {
        @Override
        public void onStateChanged(OpModeSession s, OpModeSession.State st) {
            if (st == OpModeSession.State.STOPPED) finishSession(s);
        }

        @Override
        public void onStopRequestedByOpMode(OpModeSession s) {
            Thread t = new Thread(Simulator.this::stopOpMode, "opmode-stopper");
            t.setDaemon(true);
            t.start();
        }

        @Override
        public void onError(OpModeSession s, Throwable error) {
            String msg = "User code threw an uncaught exception: " + describe(error);
            RobotLog.ee("Simulator", error, msg);
            DeviceHint hint = missingDeviceHint(error);
            if (hint != null) System.err.println(hint.text());
            synchronized (lock) {
                lastError = hint == null ? msg : msg + "\n\n" + hint.text();
                fixRobot = hint == null ? null : hint.robot();
                broadcastDsLocked();
            }
        }
    };

    /** Advice for a missing hardware device; robot is a robot file that has it, or null. */
    record DeviceHint(String text, String robot) {
    }

    private static final java.util.regex.Pattern MISSING_DEVICE =
            java.util.regex.Pattern.compile("Unable to find a hardware device with name \"(.*)\" and type");

    /**
     * Simulator-only advice after the Driver Station's own message, when code asks for a device the
     * simulated robot doesn't have: which robot file does have it, or where to add it.
     */
    DeviceHint missingDeviceHint(Throwable error) {
        if (!(error instanceof IllegalArgumentException) || error.getMessage() == null) return null;
        java.util.regex.Matcher m = MISSING_DEVICE.matcher(error.getMessage());
        if (!m.find()) return null;
        String device = m.group(1);
        String current = robotFile.getFileName().toString();
        List<String> others = new ArrayList<>();
        for (String f : robotFiles()) {
            if (f.equals(current)) continue;
            try {
                if (RobotConfig.load(robotsDir.resolve(f)).deviceNames().contains(device)) others.add(f);
            } catch (Exception ignored) {
                // a broken robot file is reported when it is selected
            }
        }
        String start = "Simulator: the simulated robot (" + current + ") has no device named \"" + device + "\"";
        if (!others.isEmpty()) {
            return new DeviceHint(start + ", but " + String.join(" and ", others) + (others.size() == 1 ? " does" : " do")
                    + ". Switch to it with the button below or the Robot menu in the top bar (not the OpMode list),"
                    + " then INIT again.", others.get(0));
        }
        return new DeviceHint(start + ". Add it to robots/" + current
                + " with the same name as in your Driver Station configuration.", null);
    }

    private void finishSession(OpModeSession s) {
        boolean rebuild;
        synchronized (lock) {
            if (s != session) return;
            if (hal != null) hal.close();
            rig.failsafe();
            SimTelemetry.Frame f = telemetry == null ? null : telemetry.flush();
            if (f != null) output.broadcast(telemetryJson(f));
            String preselect = active != null && active.kind() == OpModeInfo.Kind.AUTONOMOUS ? active.preselectTeleOp() : "";
            if (preselect != null && !preselect.isEmpty() && build != null
                    && build.opModes().stream().anyMatch(o -> o.name().equals(preselect))) {
                selected = preselect;
            }
            RobotLog.i(RobotLog.OPMODE_STOP_TAG);
            session = null;
            hal = null;
            active = null;
            state = DsState.IDLE;
            rebuild = buildPending;
            broadcastDsLocked();
        }
        if (rebuild) requestBuild();
    }

    private void checkAutoTimer(long now) {
        boolean stop;
        synchronized (lock) {
            stop = state == DsState.RUNNING && active != null && active.kind() == OpModeInfo.Kind.AUTONOMOUS
                    && autoTimerEnabled && (now - startNanos) / 1e9 >= AUTO_PERIOD_S;
        }
        if (stop) stopOpMode();
    }

    private void resetRobot() {
        synchronized (lock) {
            if (state != DsState.IDLE) return;
        }
        physicsTasks.add(() -> {
            RobotConfig c = rig.config;
            rig.powerCycle();
            rig.place(c.start[0], c.start[1], Math.toRadians(c.start[2]));
        });
    }

    private void switchRobot(String fileName) {
        boolean idle;
        synchronized (lock) {
            idle = state == DsState.IDLE;
        }
        Path file = robotsDir.resolve(fileName).normalize();
        if (!idle || !file.startsWith(robotsDir.normalize()) || !Files.isRegularFile(file)) {
            output.broadcast(helloJson());   // puts the page's Robot menu back on the robot in use
            if (!idle) setError("Stop the OpMode before changing the robot.");
            return;
        }
        physicsTasks.add(() -> {
            try {
                RobotConfig cfg = RobotConfig.load(file);
                rig = new RobotRig(cfg, new PhysicsWorld(field));
                robotFile = file;
                output.broadcast(helloJson());
                synchronized (lock) {
                    if (fixRobot != null) {   // the error was about the previous robot's devices
                        lastError = null;
                        fixRobot = null;
                    }
                    broadcastDsLocked();
                }
            } catch (Exception e) {
                output.broadcast(helloJson());
                setError("Could not load robot " + fileName + ": " + e.getMessage());
            }
        });
    }

    private void setError(String message) {
        synchronized (lock) {
            lastError = message;
            fixRobot = null;
            broadcastDsLocked();
        }
    }

    static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder(t.getClass().getSimpleName());
        if (t.getMessage() != null) sb.append(" - ").append(t.getMessage());
        for (StackTraceElement e : t.getStackTrace()) {
            String c = e.getClassName();
            if (c.startsWith("ftcsim.") || c.startsWith("java.") || c.startsWith("jdk.") || c.startsWith("com.qualcomm.")
                    || c.startsWith("org.firstinspires.ftc.robotcore.")) continue;
            sb.append(" (at ").append(e.getClassName().substring(e.getClassName().lastIndexOf('.') + 1))
                    .append('.').append(e.getMethodName()).append(':').append(e.getLineNumber()).append(')');
            break;
        }
        return sb.toString();
    }

    // ---- outgoing messages ----------------------------------------------------------------------

    public String helloJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "hello");
        m.put("field", field.json);
        RobotRig r = rig;
        m.put("robot", JsonParser.parseString(RobotConfig.gson().toJson(r.config)));
        m.put("robotFile", robotFile.getFileName().toString());
        m.put("robots", robotFiles());
        m.put("project", projectLabel);
        m.put("wheels", r.body.wheels.stream().map(w -> Map.of("key", w.key, "x", w.x, "y", w.y)).collect(Collectors.toList()));
        m.put("wheelRadius", r.body.wheelRadius);
        m.put("chassisZ", r.body.chassisRestZ());
        return gson.toJson(m);
    }

    private List<String> robotFiles() {
        try (Stream<Path> s = Files.list(robotsDir)) {
            return s.filter(p -> p.toString().endsWith(".json")).map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    public String buildJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "build");
        m.put("building", building);
        m.put("pending", buildPending);
        TeamCodeCompiler.Result b = build;
        if (b != null) {
            m.put("ok", b.ok());
            m.put("message", b.message());
            m.put("skipped", b.skipped());
            m.put("opmodes", b.opModes().stream().map(OpModeInfo::toJson).collect(Collectors.toList()));
        }
        m.put("roots", compiler.roots().stream().map(r -> r.dir().toString()).collect(Collectors.toList()));
        return gson.toJson(m);
    }

    private void broadcastBuild() {
        output.broadcast(buildJson());
    }

    public String dsJson() {
        synchronized (lock) {
            return dsJsonLocked();
        }
    }

    private String dsJsonLocked() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "ds");
        m.put("state", state.name());
        m.put("selected", selected);
        m.put("active", active == null ? null : active.name());
        m.put("activeKind", active == null ? null : active.kind().name());
        m.put("timer", autoTimerEnabled);
        m.put("error", lastError);
        m.put("fixRobot", fixRobot);
        m.put("robotFile", robotFile.getFileName().toString());
        m.put("warning", globalWarning);
        return gson.toJson(m);
    }

    private void broadcastDs() {
        synchronized (lock) {
            broadcastDsLocked();
        }
    }

    private void broadcastDsLocked() {
        output.broadcast(dsJsonLocked());
    }

    private String telemetryJson(SimTelemetry.Frame f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "telemetry");
        m.put("lines", f.lines);
        m.put("log", f.log);
        m.put("format", f.format.name());
        return gson.toJson(m);
    }

    private void publishState(RobotRig r, long now) {
        SimTelemetry t;
        Map<String, Object> ds = new LinkedHashMap<>();
        synchronized (lock) {
            t = telemetry;
            ds.put("state", state.name());
            if (state == DsState.RUNNING) {
                double elapsed = (now - startNanos) / 1e9;
                ds.put("elapsed", elapsed);
                if (active != null && active.kind() == OpModeInfo.Kind.AUTONOMOUS && autoTimerEnabled) {
                    ds.put("remaining", Math.max(0, AUTO_PERIOD_S - elapsed));
                }
            } else if (state == DsState.INIT) {
                ds.put("elapsed", (now - initNanos) / 1e9);
            }
        }
        if (t != null) {
            SimTelemetry.Frame f = t.takeFrameIfDue(now);
            if (f != null) output.broadcast(telemetryJson(f));
        }
        Map<String, Object> log;
        int n = 0;
        while (n++ < 40 && (log = logQueue.poll()) != null) output.broadcast(gson.toJson(log));

        RobotBody body = r.body;
        DVector3C p = body.position();
        DQuaternionC q = body.chassis.getQuaternion();
        double[] wheels = new double[body.wheels.size()];
        for (int i = 0; i < wheels.length; i++) wheels[i] = body.wheels.get(i).angle();
        Map<String, Object> mech = new LinkedHashMap<>();
        for (Mechanism m : r.mechanisms) mech.put(m.device.name, m.position());
        for (ServoUnit s : r.servos) mech.put(s.device.name, Math.toRadians(s.angleDeg()));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "state");
        m.put("t", simTime);
        m.put("rtf", realTimeFactor);
        m.put("p", new double[]{p.get0(), p.get1(), p.get2()});
        m.put("q", new double[]{q.get0(), q.get1(), q.get2(), q.get3()});
        m.put("wheels", wheels);
        m.put("mech", mech);
        m.put("ds", ds);
        m.put("battery", r.batteryVolts());
        output.broadcastState(gson.toJson(m));
    }

    private void publishDevices(RobotRig r) {
        List<Map<String, Object>> motors = new ArrayList<>();
        List<Map<String, Object>> servos = new ArrayList<>();
        List<Map<String, Object>> hubs = new ArrayList<>();
        for (SimHub h : r.hubs) {
            hubs.add(Map.of("name", h.name, "led", String.format("#%06x", h.ledColor), "cache", h.getBulkCachingMode().name()));
            for (MotorPort mp : h.motors) {
                if (mp == null) continue;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("name", mp.name);
                d.put("hub", h.name);
                d.put("port", mp.port);
                d.put("mode", mp.mode.name());
                d.put("power", mp.velocityCommand ? null : mp.power);
                d.put("velocityTarget", mp.velocityCommand ? mp.velocityTarget : null);
                d.put("target", mp.targetPositionSet ? mp.targetPosition : null);
                d.put("pos", mp.encoderPosition());
                d.put("vel", Math.round(mp.velocityTicksPerSecond));
                d.put("amps", Math.round(mp.currentAmps * 100) / 100.0);
                d.put("duty", Math.round(mp.appliedDuty * 1000) / 1000.0);
                d.put("zpb", mp.zeroPowerBehavior.name());
                motors.add(d);
            }
            for (ServoPort sp : h.servos) {
                if (sp == null) continue;
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("name", sp.name);
                d.put("hub", h.name);
                d.put("port", sp.port);
                d.put("continuous", sp.continuous);
                d.put("pos", Double.isNaN(sp.position) ? null : sp.position);
                d.put("pwm", sp.pwmEnabled);
                d.put("angle", Math.round(sp.angleDeg * 10) / 10.0);
                servos.add(d);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "devices");
        m.put("hubs", hubs);
        m.put("motors", motors);
        m.put("servos", servos);
        m.put("sensors", r.sensorReadings());
        output.broadcastState(gson.toJson(m));
    }

    // ---- read-only views (tests and tools) ---------------------------------------------------------

    public DsState dsState() {
        synchronized (lock) {
            return state;
        }
    }

    public String lastError() {
        synchronized (lock) {
            return lastError;
        }
    }

    public TeamCodeCompiler.Result build() {
        return build;
    }

    public boolean isBuilding() {
        return building;
    }

    public RobotRig rig() {
        return rig;
    }

    /** Runs a task on the physics thread and waits for it. */
    public void onPhysicsThread(Runnable task) throws InterruptedException {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        physicsTasks.add(() -> {
            try {
                task.run();
            } finally {
                done.countDown();
            }
        });
        done.await();
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    @SuppressWarnings("unused")
    private static String fmt(String f, Object... a) {
        return String.format(Locale.US, f, a);
    }

    @SuppressWarnings("unused")
    private static JsonElement none() {
        return null;
    }
}
