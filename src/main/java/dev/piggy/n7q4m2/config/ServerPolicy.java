package dev.piggy.n7q4m2.config;

public final class ServerPolicy {
    public boolean enabled = true;
    public double maxAirAcceleration = 20;
    public double maxGroundAcceleration = 20;
    public double minGroundFriction = 2;
    public double maxGroundFriction = 12;
    public double minAirFriction = 0.5;
    public double maxAirFriction = 8;
    public double minSpeedCapBps = 8;
    public double maxSpeedCapBps = 80;

    public void sanitize() {
        maxAirAcceleration = clamp(maxAirAcceleration, 1, 40, 20);
        maxGroundAcceleration = clamp(maxGroundAcceleration, 1, 40, 20);
        minGroundFriction = clamp(minGroundFriction, 0.1, 20, 2);
        maxGroundFriction = clamp(maxGroundFriction, minGroundFriction, 20, 12);
        minAirFriction = clamp(minAirFriction, 0.1, 20, 0.5);
        maxAirFriction = clamp(maxAirFriction, minAirFriction, 20, 8);
        minSpeedCapBps = clamp(minSpeedCapBps, 5, 200, 8);
        maxSpeedCapBps = clamp(maxSpeedCapBps, minSpeedCapBps, 200, 80);
    }

    public MovementSettings constrain(MovementSettings s) {
        double air = clamp(s.airAcceleration(), 1, maxAirAcceleration, 8);
        double ground = clamp(s.groundAcceleration(), 1, maxGroundAcceleration, 10);
        double groundFriction = clamp(s.groundFriction(), minGroundFriction, maxGroundFriction, 6);
        double airFriction = clamp(s.airFriction(), minAirFriction, maxAirFriction, 1.5);
        double speed = clamp(s.maxSpeedBps(), minSpeedCapBps, maxSpeedCapBps, 40);
        boolean allowed = enabled && s.enabled();
        if (air == s.airAcceleration() && ground == s.groundAcceleration() && groundFriction == s.groundFriction()
                && airFriction == s.airFriction() && speed == s.maxSpeedBps() && allowed == s.enabled()) return s;
        return new MovementSettings(air, ground, groundFriction, airFriction, speed, s.autoJump(), allowed);
    }

    public static double clamp(double value, double min, double max, double fallback) {
        return Math.max(min, Math.min(max, Double.isFinite(value) ? value : fallback));
    }
}
