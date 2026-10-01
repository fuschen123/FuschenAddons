package com.FishHelper;

/** Normalized travel distance, not raw pixels: both edges remain reachable after resizing. */
public record HudPosition(double x, double y) {
    public HudPosition { x = clamp(x, .5); y = clamp(y, .12); }
    private static double clamp(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : fallback;
    }
    public int pixelX(int screenWidth, int barWidth) { return (int) Math.round(x * Math.max(0, screenWidth - barWidth)); }
    public int pixelY(int screenHeight, int barHeight) { return (int) Math.round(y * Math.max(0, screenHeight - barHeight)); }
    public static HudPosition fromPixels(double x, double y, int screenWidth, int screenHeight, int barWidth, int barHeight) {
        return new HudPosition(x / Math.max(1, screenWidth - barWidth), y / Math.max(1, screenHeight - barHeight));
    }
}
