package ftcsim.physics;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The BIOBUZZ field layout (FTC 2026-27), read from field/biobuzz-field.json.
 * Frame: origin at field center on the tiles, +x toward the blue alliance wall, +y away from
 * the audience, +z up. Meters.
 */
public final class FieldModel {

    /** An axis-aligned floor-level obstacle the robot cannot drive through. */
    public record Box(String name, double cx, double cy, double cz, double sx, double sy, double sz) {
    }

    /** A vertical cylinder obstacle. */
    public record Post(String name, double cx, double cy, double radius, double height) {
    }

    /** A colored tape rectangle on the floor; outline=true for taped outlines, false for solid areas. */
    public record Tape(String name, double x0, double x1, double y0, double y1, String color, boolean outline) {
    }

    public final JsonObject json;
    public final double half;        // half the field's inner width
    public final double wallHeight;
    public final double tapeWidth;
    public final List<Box> boxes = new ArrayList<>();
    public final List<Post> posts = new ArrayList<>();
    public final List<Tape> tapes = new ArrayList<>();

    private FieldModel(JsonObject json) {
        this.json = json;
        this.half = json.getAsJsonArray("field_m").get(0).getAsDouble() / 2;
        this.wallHeight = json.get("wall_height_m").getAsDouble();
        JsonObject structure = json.has("structure") ? json.getAsJsonObject("structure") : new JsonObject();
        this.tapeWidth = structure.has("tape_m") ? structure.get("tape_m").getAsDouble() : 0.0254;

        // The HIVE structure stands on two A-frames at x = +-0.628 whose bases span |y| <= 0.5.
        for (double x : new double[]{-0.628, 0.628}) {
            boxes.add(new Box(x < 0 ? "A-frame (red side)" : "A-frame (blue side)", x, 0, 0.175, 0.06, 1.06, 0.35));
        }
        // FLOWERS stand against the walls.
        JsonObject flowers = json.getAsJsonObject("flowers");
        for (String name : flowers.keySet()) {
            JsonObject f = flowers.getAsJsonObject(name);
            JsonArray axis = f.getAsJsonArray("axis_m");
            double h = f.get("opening_height_m").getAsDouble();
            posts.add(new Post(name, axis.get(0).getAsDouble(), axis.get(1).getAsDouble(), 0.075, h));
        }
        // Taped areas: LOADING ZONES are outlined, GARDENS are solid strips along the walls.
        JsonObject zones = json.getAsJsonObject("loading_zones");
        for (String alliance : zones.keySet()) {
            JsonObject z = zones.getAsJsonObject(alliance);
            tapes.add(tape("loading zone " + alliance, z, alliance, true));
        }
        JsonObject gardens = json.getAsJsonObject("gardens");
        for (String alliance : gardens.keySet()) {
            JsonObject g = gardens.getAsJsonObject(alliance);
            tapes.add(tape("garden " + alliance, g, alliance, false));
        }
    }

    private static Tape tape(String name, JsonObject o, String color, boolean outline) {
        JsonArray xs = o.getAsJsonArray("x_m");
        JsonArray ys = o.getAsJsonArray("y_m");
        return new Tape(name, xs.get(0).getAsDouble(), xs.get(1).getAsDouble(),
                ys.get(0).getAsDouble(), ys.get(1).getAsDouble(), color, outline);
    }

    public static FieldModel loadBiobuzz() throws IOException {
        try (InputStream in = FieldModel.class.getResourceAsStream("/field/biobuzz-field.json")) {
            if (in == null) throw new IOException("field/biobuzz-field.json is missing from the classpath");
            JsonElement e = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return new FieldModel(e.getAsJsonObject());
        }
    }

    /**
     * Floor color under a point, as normalized RGBA reflectance (what a downward color sensor with
     * its LED on reads at gain 1). Gray foam tiles, red and blue tape.
     */
    public float[] floorColor(double x, double y) {
        if (Math.abs(x) > half || Math.abs(y) > half) return new float[]{0.02f, 0.02f, 0.02f, 0.02f};
        for (Tape t : tapes) {
            boolean inside = x >= t.x0() && x <= t.x1() && y >= t.y0() && y <= t.y1();
            if (!inside) continue;
            boolean onTape = !t.outline()
                    || x - t.x0() < tapeWidth || t.x1() - x < tapeWidth
                    || y - t.y0() < tapeWidth || t.y1() - y < tapeWidth;
            if (onTape) {
                return "red".equals(t.color())
                        ? new float[]{0.24f, 0.045f, 0.05f, 0.11f}
                        : new float[]{0.035f, 0.07f, 0.26f, 0.12f};
            }
        }
        // Tile seams every 24 inches are slightly darker.
        double tile = json.get("tile_m").getAsDouble();
        double fx = Math.abs(((x + half) % tile)), fy = Math.abs(((y + half) % tile));
        boolean seam = fx < 0.004 || tile - fx < 0.004 || fy < 0.004 || tile - fy < 0.004;
        float v = seam ? 0.025f : 0.045f;
        return new float[]{v, v * 1.05f, v * 1.1f, v * 1.05f};
    }
}
