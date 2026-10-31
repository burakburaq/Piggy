package dev.piggy.n7q4m2.movement;

import dev.piggy.n7q4m2.config.MovementSettings;

public interface HopAccess {
    HopState piggy$state();
    MovementEnvelope piggy$envelope();
    MovementSettings piggy$settings();
    void piggy$settings(MovementSettings settings);
    boolean piggy$enabled();
    void piggy$enabled(boolean enabled);
    default boolean piggy$dimensionTransfer() { return false; }
}
