package com.FishHelper;

/** World-space steering translated to vanilla forward/strafe keys at the user's yaw. */
public final class MovementPlanner {
    public record Input(int forward, int left, double dx, double dz) { }
    public interface Safety { boolean safe(double dx, double dz); }
    private MovementPlanner() { }
    public static Input choose(double x, double z, double yaw, double targetX, double targetZ, Safety safety) {
        double radius = Math.hypot(x, z), best = -Double.MAX_VALUE;
        Input result = new Input(0, 0, 0, 0);
        double wantX = radius >= 1.6 ? -x : targetX - x;
        double wantZ = radius >= 1.6 ? -z : targetZ - z;
        if (Math.hypot(wantX, wantZ) < .12) return result;
        double sin = Math.sin(Math.toRadians(yaw)), cos = Math.cos(Math.toRadians(yaw));
        for (int forward = -1; forward <= 1; forward++) for (int left = -1; left <= 1; left++) {
            if (forward == 0 && left == 0) continue;
            double length = Math.hypot(forward, left);
            double dx = (-sin * forward + cos * left) / length;
            double dz = (cos * forward + sin * left) / length;
            double future = Math.hypot(x + dx * .35, z + dz * .35);
            if (radius >= 1.6 && future >= radius || radius < 1.6 && future > 1.95 || !safety.safe(dx, dz)) continue;
            double score = (wantX * dx + wantZ * dz) / Math.hypot(wantX, wantZ);
            if (score > best && score > .05) { best = score; result = new Input(forward, left, dx, dz); }
        }
        return result;
    }
}
