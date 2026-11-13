package ftcsim.hal;

/** Routes gamepad rumble and LED requests from robot code to the browser Driver Station. */
public final class GamepadEffects {
    public interface Listener {
        void onRumble(int user, double[] strong, double[] weak, int[] durationsMs);

        void onLed(int user, double r, double g, double b, int durationMs);
    }

    private static volatile Listener listener;

    private GamepadEffects() {
    }

    public static void setListener(Listener l) {
        listener = l;
    }

    public static void rumble(int user, double[] strong, double[] weak, int[] durationsMs) {
        Listener l = listener;
        if (l != null) l.onRumble(user, strong, weak, durationsMs);
    }

    public static void led(int user, double r, double g, double b, int durationMs) {
        Listener l = listener;
        if (l != null) l.onLed(user, r, g, b, durationMs);
    }
}
