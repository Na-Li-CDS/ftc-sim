package ftcsim.runner;

import java.util.LinkedHashMap;
import java.util.Map;

/** An OpMode found in the compiled TeamCode, as the Driver Station lists it. */
public record OpModeInfo(String name, String group, Kind kind, String className, boolean disabled, boolean sample,
                         String preselectTeleOp, Class<?> type) {

    public enum Kind {TELEOP, AUTONOMOUS}

    /** JSON-friendly view for the browser. */
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("group", group);
        m.put("kind", kind.name());
        m.put("className", className);
        m.put("disabled", disabled);
        m.put("sample", sample);
        m.put("preselectTeleOp", preselectTeleOp);
        return m;
    }
}
