package dev.piggy.n7q4m2.config;

public record MovementSettings(double airAcceleration, double groundAcceleration,
                               double groundFriction, double airFriction, double maxSpeedBps,
                               boolean autoJump, boolean enabled) {
    public static MovementSettings defaults() { return new MovementSettings(8, 10, 6, 1.5, 40, true, false); }
}
