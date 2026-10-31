package dev.piggy.n7q4m2.movement;

/** State is owned by the player, never by the world, so dimension changes preserve it. */
public final class HopState {
    private boolean active;
    private double firstJumpY = Double.NaN;
    private int firstJumpTick;
    private double retainedSpeed;
    private HopPhysics.Horizontal retainedMovement = new HopPhysics.Horizontal(0, 0);
    private double impactSpeedBps;
    private int groundedSince = -1;

    public boolean active() { return active; }
    /** Server corrections affect the jump chain, never the player's saved movement preference. */
    public void applyRemote(boolean chainActive) {
        if (!chainActive) { resetChain(); return; }
        active = true;
    }
    public void pauseLandingTiming() { groundedSince = -1; }
    public double retainedSpeed() { return retainedSpeed; }
    public HopPhysics.Horizontal retainedMovement() { return retainedMovement; }
    public void retain(double x, double z) {
        retainedMovement = new HopPhysics.Horizontal(x, z);
        retainedSpeed = retainedMovement.speed();
    }
    public double impactSpeedBps() { return impactSpeedBps; }
    public void impactSpeedBps(double speed) { impactSpeedBps = Math.max(0, speed); }

    public void jump(double feetY, int tick, boolean movementHeld) {
        groundedSince = -1;
        if (active) return;
        if (!movementHeld) { resetChain(); return; }
        if (!Double.isNaN(firstJumpY) && tick > firstJumpTick
                && tick - firstJumpTick <= 40 && Math.abs(feetY - firstJumpY) <= 0.02) {
            active = true;
            firstJumpY = Double.NaN;
        } else {
            firstJumpY = feetY;
            firstJumpTick = tick;
        }
    }

    public void tick(boolean onGround, boolean movementHeld, double speedBps, int tick) {
        if (onGround) {
            if (groundedSince < 0) groundedSince = tick;
            // A missed landing window ends only the chain; the saved movement setting stays unchanged.
            if (tick - groundedSince >= 3) resetChain();
        } else groundedSince = -1;
        if (!active && !Double.isNaN(firstJumpY) && tick - firstJumpTick > 40) firstJumpY = Double.NaN;
        if (onGround && !movementHeld && speedBps < HopPhysics.STOP_SPEED_BPS) reset();
    }

    public void setActive(boolean value) {
        active = value;
        if (!value) { firstJumpY = Double.NaN; retain(0, 0); }
    }

    private void resetChain() {
        active = false;
        firstJumpY = Double.NaN;
        retain(0, 0);
        impactSpeedBps = 0;
        groundedSince = -1;
    }
    public void reset() { resetChain(); }
}
