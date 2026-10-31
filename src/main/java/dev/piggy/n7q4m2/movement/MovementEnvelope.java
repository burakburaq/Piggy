package dev.piggy.n7q4m2.movement;

/** Server-clocked horizontal path budget. Incoming packets never create time or impulses. */
public final class MovementEnvelope {
    private static final int MAX_BUFFERED_TICKS = 20;
    private int lastTick = Integer.MIN_VALUE;
    private double speed;
    private double credit;

    public void reset() { lastTick = Integer.MIN_VALUE; speed = 0; credit = 0; }
    public void restartClock() { lastTick = Integer.MIN_VALUE; credit = 0; }
    public double speed() { return speed; }
    public double credit() { return credit; }
    public void forfeit() { credit = 0; }

    public void advance(int tick, double baseline, double cap, double airGain, boolean air) {
        if (lastTick == Integer.MIN_VALUE || tick < lastTick) {
            speed = Math.max(speed, baseline);
            credit = speed * 2; // One client tick of phase difference at connection/teleport.
            lastTick = tick;
            return;
        }
        int elapsed = Math.min(MAX_BUFFERED_TICKS, tick - lastTick);
        for (int i = 0; i < elapsed; i++) {
            if (air) speed = Math.max(baseline, Math.min(Math.max(cap, speed), speed + airGain));
            // Keep the previously authorized wall/ice momentum. Grounded walking
            // cannot grow it beyond vanilla baseline; a packet cannot raise it.
            else speed = Math.max(speed, baseline);
            credit += speed;
        }
        credit = Math.min(credit, speed * MAX_BUFFERED_TICKS);
        lastTick = tick;
    }

    public boolean consume(double distance) {
        if (!Double.isFinite(distance) || distance < 0 || distance > credit + 1.0e-6) return false;
        credit = Math.max(0, credit - distance);
        return true;
    }

    /** Call only for server-authored outbound motion; never for knownMovement. */
    public void motion(double absoluteSpeed) {
        if (!Double.isFinite(absoluteSpeed) || absoluteSpeed < 0) return;
        double increase = Math.max(0, absoluteSpeed - speed);
        speed = Math.max(speed, absoluteSpeed);
        credit += increase;
    }

    public void impulse(double additionalSpeed) {
        if (!Double.isFinite(additionalSpeed) || additionalSpeed < 0) return;
        speed += additionalSpeed;
        credit += additionalSpeed;
    }
}
