package ftcsim;

import java.awt.Desktop;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import ftcsim.physics.FieldModel;
import ftcsim.runner.TeamCodeCompiler;
import ftcsim.server.WebServer;
import ftcsim.sim.Simulator;

/**
 * FTC simulator entry point.
 *
 * <pre>
 *   ftc-sim [--project DIR] [--robot FILE] [--port N] [--no-browser]
 * </pre>
 *
 * --project points at your FtcRobotController project; the simulator compiles its
 * TeamCode/src/main/java (and the SDK samples folder) and rebuilds whenever you save.
 * Defaults can be kept in sim.properties next to this program (project=..., robot=..., port=...).
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        Path home = Paths.get("").toAbsolutePath();
        Properties props = new Properties();
        Path propsFile = home.resolve("sim.properties");
        if (Files.isRegularFile(propsFile)) {
            try (Reader r = Files.newBufferedReader(propsFile, StandardCharsets.UTF_8)) {
                props.load(r);
            }
        }
        String project = props.getProperty("project");
        String robot = props.getProperty("robot", "robots/mecanum.json");
        int port = Integer.parseInt(props.getProperty("port", "8080"));
        boolean openBrowser = Boolean.parseBoolean(props.getProperty("open_browser", "true"));

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--project", "-p" -> project = args[++i];
                case "--robot", "-r" -> robot = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--no-browser" -> openBrowser = false;
                case "--help", "-h" -> {
                    usage();
                    return;
                }
                default -> {
                    System.err.println("Unknown option " + args[i]);
                    usage();
                    System.exit(2);
                }
            }
        }

        List<TeamCodeCompiler.SourceRoot> roots = new ArrayList<>();
        String label;
        if (project != null && !project.isBlank()) {
            Path p = Paths.get(project).toAbsolutePath().normalize();
            if (!Files.isDirectory(p)) {
                System.err.println("Project folder not found: " + p);
                System.err.println("Clone your team's FtcRobotController repository (or the official one,"
                        + " https://github.com/FIRST-Tech-Challenge/FtcRobotController) and pass its folder.");
                System.exit(2);
            }
            // Accept the TeamCode or FtcRobotController module folder too, not just the project root.
            if (!Files.isDirectory(p.resolve("TeamCode")) && p.getParent() != null
                    && Files.isDirectory(p.getParent().resolve("TeamCode/src/main/java"))) {
                p = p.getParent();
            }
            Path teamCode = p.resolve("TeamCode/src/main/java");
            if (!Files.isDirectory(teamCode)) {
                System.err.println("Not an FtcRobotController project (no TeamCode/src/main/java): " + p);
                System.err.println("Pass the folder that contains TeamCode and FtcRobotController.");
                System.exit(2);
            }
            roots.add(new TeamCodeCompiler.SourceRoot(teamCode, false));
            Path samples = p.resolve("FtcRobotController/src/main/java/org/firstinspires/ftc/robotcontroller/external/samples");
            if (Files.isDirectory(samples)) roots.add(new TeamCodeCompiler.SourceRoot(samples, true));
            label = p.toString();
        } else {
            roots.add(new TeamCodeCompiler.SourceRoot(home.resolve("examples/teamcode"), false));
            roots.add(new TeamCodeCompiler.SourceRoot(home.resolve("examples/samples"), true));
            label = "examples (no --project given)";
        }

        Path robotFile = home.resolve(robot).normalize();
        if (!Files.isRegularFile(robotFile)) {
            System.err.println("Robot file not found: " + robotFile);
            System.exit(2);
        }
        // Each running simulator compiles into its own folder; remove folders of simulators that have exited.
        Path workRoot = home.resolve("build/teamcode-classes");
        Files.createDirectories(workRoot);
        try (var dirs = Files.list(workRoot)) {
            for (Path d : dirs.toList()) {
                String name = d.getFileName().toString();
                if (!name.matches("\\d+")) continue;
                boolean exited = ProcessHandle.of(Long.parseLong(name)).map(p -> !p.isAlive()).orElse(true);
                if (exited) deleteTree(d);
            }
        }
        Path work = workRoot.resolve(Long.toString(ProcessHandle.current().pid()));
        Files.createDirectories(work);

        Simulator sim = new Simulator(FieldModel.loadBiobuzz(), robotFile, robotFile.getParent(),
                new TeamCodeCompiler(roots, work), label);
        WebServer server = new WebServer(sim);
        int bound = -1;
        for (int attempt = 0; attempt < 10 && bound < 0; attempt++) {
            try {
                bound = server.start(port + attempt);
            } catch (Exception e) {
                if (attempt == 9) throw e;
            }
        }
        sim.start();
        String url = "http://localhost:" + bound + "/";
        System.out.println();
        System.out.println("FTC simulator running at " + url);
        System.out.println("  TeamCode: " + label);
        System.out.println("  Robot:    " + robotFile.getFileName());
        System.out.println("Press Ctrl+C to quit.");
        if (openBrowser) open(url);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sim.shutdown();
            server.stop();
        }));
        Thread.currentThread().join();
    }

    private static void deleteTree(Path dir) {
        try (var s = Files.walk(dir)) {
            for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // in use or already gone
        }
    }

    private static void usage() {
        System.out.println("""
                Usage: ftc-sim [--project DIR] [--robot FILE] [--port N] [--no-browser]

                  --project DIR   your FtcRobotController project (uses DIR/TeamCode/src/main/java)
                  --robot FILE    robot description, default robots/mecanum.json
                  --port N        web port, default 8080
                  --no-browser    do not open the browser

                Defaults can also be set in sim.properties (project=, robot=, port=, open_browser=).""");
    }

    private static void open(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (IOException | UnsupportedOperationException ignored) {
        }
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            else if (os.contains("mac")) new ProcessBuilder("open", url).start();
            else new ProcessBuilder("xdg-open", url).start();
        } catch (IOException ignored) {
            System.out.println("Open " + url + " in your browser.");
        }
    }
}
