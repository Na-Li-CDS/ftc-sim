package ftcsim.hal;

/** Destination for RobotLog output; the engine installs a listener that forwards lines to the browser. */
public final class SimLog {
    public interface Listener {
        void onLog(char level, String tag, String message);

        void onGlobalError(String message);

        void onGlobalWarning(String message);
    }

    private static volatile Listener listener;

    private SimLog() {
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    public static void log(char level, String tag, String message) {
        Listener l = listener;
        if (l != null) {
            l.onLog(level, tag, message);
        } else {
            System.out.println(level + "/" + tag + ": " + message);
        }
    }

    public static void globalError(String message) {
        Listener l = listener;
        if (l != null) l.onGlobalError(message);
    }

    public static void globalWarning(String message) {
        Listener l = listener;
        if (l != null) l.onGlobalWarning(message);
    }
}
