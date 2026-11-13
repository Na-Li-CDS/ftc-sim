package com.qualcomm.robotcore.hardware;

/** {@link NormalizedRGBA} instances represent a set of normalized color values in [0, 1]. */
public class NormalizedRGBA {
    public float red;
    public float green;
    public float blue;
    public float alpha;

    public NormalizedRGBA() {
    }

    /** Packs the color into an Android-style ARGB int. */
    public int toColor() {
        int a = (int) (Math.max(0, Math.min(1, alpha)) * 255 + 0.5f);
        int r = (int) (Math.max(0, Math.min(1, red)) * 255 + 0.5f);
        int g = (int) (Math.max(0, Math.min(1, green)) * 255 + 0.5f);
        int b = (int) (Math.max(0, Math.min(1, blue)) * 255 + 0.5f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
