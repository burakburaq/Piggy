package dev.piggy.n7q4m2.client.quality.movement;

import java.util.random.RandomGenerator;

/** Monotonic press/release scheduler. Time is in nanoseconds; menus reset the sequence. */
public final class AntiAfkSequence {
    public enum Action { NONE, CROUCH, JUMP, FORWARD, LEFT, BACKWARD, RIGHT }
    private static final Action[] DIRECTIONS = {Action.FORWARD, Action.LEFT, Action.BACKWARD, Action.RIGHT};
    private final RandomGenerator random;
    private Action action = Action.NONE;
    private boolean started;
    private long releaseAt, nextAt;

    public AntiAfkSequence(RandomGenerator random) { this.random = random; }
    public void reset() { action = Action.NONE; started = false; }
    public Action sample(long now) { return sample(now, true, true); }
    public Action sample(long now, boolean allowJump, boolean allowCrouch) {
        if (action != Action.NONE) {
            if (now - releaseAt < 0) return action;
            action = Action.NONE;
            nextAt = now + (500L + random.nextInt(1501)) * 1_000_000;
            return action;
        }
        if (started && now - nextAt < 0) return Action.NONE;
        int choice = random.nextInt(100);
        action = choice < 25 && allowCrouch ? Action.CROUCH : choice >= 25 && choice < 50 && allowJump ? Action.JUMP : DIRECTIONS[random.nextInt(4)];
        // A jump is one ordinary game tick; directional and crouch holds last 100..300 ms.
        int holdMillis = action == Action.JUMP ? 50 : 100 + random.nextInt(201);
        releaseAt = now + holdMillis * 1_000_000L;
        started = true;
        return action;
    }
}
