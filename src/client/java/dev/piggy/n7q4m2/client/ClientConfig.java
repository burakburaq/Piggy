package dev.piggy.n7q4m2.client;

import dev.piggy.n7q4m2.config.MovementSettings;
import dev.piggy.n7q4m2.config.ServerPolicy;

public final class ClientConfig {
    // The old combo-era "enabled" field must not auto-enable the new always-on mode.
    public boolean bhopEnabled = false;
    public boolean autoJump = true;
    public double airAcceleration = 8;
    public double groundAcceleration = 10;
    public double groundFriction = 6;
    public double airFriction = 1.5;
    public double maxSpeedBps = 40;
    public double freeCameraSpeedBps = 12;
    public boolean showHud = true;
    public double hudX = 0.5;
    public double hudY = 0.75;
    private transient MovementSettings cachedMovement;

    public MovementSettings movement() {
        var previous = cachedMovement;
        if (previous == null || previous.airAcceleration() != airAcceleration || previous.groundAcceleration() != groundAcceleration
                || previous.groundFriction() != groundFriction || previous.airFriction() != airFriction
                || previous.maxSpeedBps() != maxSpeedBps || previous.autoJump() != autoJump || previous.enabled() != bhopEnabled)
            cachedMovement = new MovementSettings(airAcceleration, groundAcceleration, groundFriction, airFriction, maxSpeedBps, autoJump, bhopEnabled);
        return cachedMovement;
    }
    /** Without a server policy, use the saved client preference rather than disabling movement. */
    public MovementSettings movement(ServerPolicy serverPolicy) {
        var requested = movement();
        return serverPolicy == null ? requested : serverPolicy.constrain(requested);
    }

    public void sanitize() {
        // Persist preferences within supported absolute bounds. The connected server
        // constrains effective settings separately; defaults are not a server policy.
        airAcceleration = ServerPolicy.clamp(airAcceleration, 1, 40, 8);
        groundAcceleration = ServerPolicy.clamp(groundAcceleration, 1, 40, 10);
        groundFriction = ServerPolicy.clamp(groundFriction, .1, 20, 6);
        airFriction = ServerPolicy.clamp(airFriction, .1, 20, 1.5);
        maxSpeedBps = ServerPolicy.clamp(maxSpeedBps, 5, 200, 40);
        freeCameraSpeedBps = ServerPolicy.clamp(freeCameraSpeedBps, 1, 100, 12);
        hudX = ServerPolicy.clamp(hudX, 0, 1, .5); hudY = ServerPolicy.clamp(hudY, 0, 1, .75);
    }
    public ClientConfig copy() {
        ClientConfig c = new ClientConfig();
        c.bhopEnabled = bhopEnabled; c.autoJump = autoJump;
        c.airAcceleration = airAcceleration; c.groundAcceleration = groundAcceleration;
        c.groundFriction = groundFriction; c.airFriction = airFriction; c.maxSpeedBps = maxSpeedBps;
        c.freeCameraSpeedBps = freeCameraSpeedBps;
        c.showHud = showHud; c.hudX = hudX; c.hudY = hudY;
        return c;
    }
}
