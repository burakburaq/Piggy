package dev.piggy.n7q4m2.movement;

/** Pure physics, in blocks per tick. A game tick is 1/20 second. */
public final class HopPhysics {
    public static final double WALK_SPEED_BPS = 4.317;
    public static final double STOP_SPEED_BPS = WALK_SPEED_BPS * 1.1;
    public static final double FALL_REFERENCE_BPS = WALK_SPEED_BPS * 1.3;
    private HopPhysics() {}

    public record Horizontal(double x, double z) {
        public double speed() { return Math.hypot(x, z); }
        public Horizontal scale(double multiplier) { return new Horizontal(x * multiplier, z * multiplier); }
    }

    public static Horizontal accelerate(Horizontal velocity, double wishX, double wishZ,
                                        double wishSpeed, double projectionCap, double acceleration) {
        double length = Math.hypot(wishX, wishZ);
        if (length < 1.0e-6) return velocity;
        double nx = wishX / length, nz = wishZ / length;
        double remaining = projectionCap - (velocity.x * nx + velocity.z * nz);
        if (remaining <= 0) return velocity;
        double added = Math.min(remaining, acceleration * wishSpeed / 20.0);
        return new Horizontal(velocity.x + nx * added, velocity.z + nz * added);
    }

    public static Horizontal friction(Horizontal velocity, double coefficient) {
        return velocity.scale(Math.max(0, 1 - coefficient / 20.0));
    }

    /** Strong air control for the requested custom bhop feel, without reducing momentum. */
    public static Horizontal turnWithView(Horizontal velocity, double yawDegrees) {
        double angle = Math.toRadians(yawDegrees * .9);
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new Horizontal(velocity.x * cos - velocity.z * sin, velocity.z * cos + velocity.x * sin);
    }

    public static Horizontal cap(Horizontal velocity, double capBps) {
        double speed = velocity.speed();
        return speed > capBps / 20.0 ? velocity.scale(capBps / 20.0 / speed) : velocity;
    }

    public static float fallDamage(float vanillaDamage, double impactSpeedBps, boolean active) {
        if (!active || vanillaDamage <= 0) return vanillaDamage;
        double ratio = Math.max(1, impactSpeedBps / FALL_REFERENCE_BPS);
        float result = (float) (vanillaDamage / Math.sqrt(ratio));
        return result < 0.5f ? 0 : result;
    }
}
