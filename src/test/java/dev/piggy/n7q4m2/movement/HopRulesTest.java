package dev.piggy.n7q4m2.movement;

import dev.piggy.n7q4m2.config.MovementSettings;
import dev.piggy.n7q4m2.config.ServerPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HopRulesTest {
    @Test void requiresTwoJumpsAtTheSameHeight() {
        HopState state = new HopState();
        state.applyRemote(false);
        state.jump(64, 1, true);
        assertFalse(state.active());
        state.jump(65, 13, true);
        assertFalse(state.active());
        state.jump(65, 25, true);
        assertTrue(state.active());
    }

    @Test void activationCannotComeFromRepeatedSameTickOrOldJump() {
        HopState state = new HopState();
        state.applyRemote(false);
        state.jump(64, 1, true); state.jump(64, 1, true);
        assertFalse(state.active());
        state.jump(64, 90, true);
        assertFalse(state.active());
    }

    @Test void aLateJumpLosesTheChainEvenWhenMovementIsHeld() {
        HopState state = new HopState();
        state.setActive(true);
        state.tick(true, true, 20, 100);
        state.tick(true, true, 20, 102);
        assertTrue(state.active());
        state.tick(true, true, 20, 103);
        assertFalse(state.active());
    }

    @Test void wellTimedJumpPreservesTheChainAcrossLanding() {
        HopState state = new HopState();
        state.setActive(true);
        state.tick(true, true, 20, 100);
        state.jump(64, 101, true);
        state.tick(false, true, 20, 103);
        assertTrue(state.active());
    }

    @Test void onlyStopsAtLowSpeedOnGroundWithoutMovementKeys() {
        HopState state = new HopState();
        state.setActive(true);
        state.tick(true, true, 0, 10);
        assertTrue(state.active());
        state.tick(false, false, 0, 11);
        assertTrue(state.active());
        state.tick(true, false, HopPhysics.STOP_SPEED_BPS, 12);
        assertTrue(state.active());
        state.tick(true, false, HopPhysics.STOP_SPEED_BPS - .01, 13);
        assertFalse(state.active());
    }

    @Test void damageMatchesTheRequestedInverseSquareRoot() {
        double reference = HopPhysics.FALL_REFERENCE_BPS;
        assertEquals(16, HopPhysics.fallDamage(16, reference, true));
        assertEquals(8, HopPhysics.fallDamage(16, reference * 4, true));
        assertEquals(4, HopPhysics.fallDamage(16, reference * 16, true));
        assertEquals(16, HopPhysics.fallDamage(16, reference * 16, false));
        assertEquals(16, HopPhysics.fallDamage(16, 0, true));
    }

    @Test void halfPointBoundaryIsStrictAndFractionalDamageIsPreserved() {
        double reference = HopPhysics.FALL_REFERENCE_BPS;
        assertEquals(0, HopPhysics.fallDamage(1, reference * 4.01, true));
        assertEquals(.5f, HopPhysics.fallDamage(1, reference * 4, true));
        assertEquals(.75f, HopPhysics.fallDamage(3, reference * 16, true));
    }

    @Test void AirStrafingGainsSpeedPerpendicularToMomentum() {
        var initial = new HopPhysics.Horizontal(.4, 0);
        var result = HopPhysics.accelerate(initial, 0, 1, .21585, .08, 8);
        assertEquals(.4, result.x());
        assertTrue(result.speed() > initial.speed());
        var saturated = HopPhysics.accelerate(result, 0, 1, .21585, .08, 8);
        assertEquals(result, saturated);
    }

    @Test void noInputFrictionSlowsAndCapDoesNotChangeDirection() {
        var velocity = new HopPhysics.Horizontal(3, 4);
        assertTrue(HopPhysics.friction(velocity, 1.5).speed() < velocity.speed());
        var capped = HopPhysics.cap(velocity, 40);
        assertEquals(2, capped.speed(), .00001);
        assertEquals(.75, capped.x() / capped.z(), .00001);
    }

    @Test void sharpViewTurnsPreserveSpeedAndFollowTheShortYawArc() {
        var initial = new HopPhysics.Horizontal(0, .5);
        var turned = HopPhysics.turnWithView(initial, 90);
        assertEquals(initial.speed(), turned.speed(), .000001);
        assertTrue(turned.x() < -.49);
        assertTrue(turned.z() < .08);
        var reversed = HopPhysics.turnWithView(turned, -90);
        assertEquals(initial.x(), reversed.x(), .000001);
        assertEquals(initial.z(), reversed.z(), .000001);
    }

    @Test void serverRejectsOutOfRangeAndNonFinitePreferences() {
        ServerPolicy policy = new ServerPolicy();
        var constrained = policy.constrain(new MovementSettings(Double.NaN, 999, -999,
                Double.POSITIVE_INFINITY, 99999, true, true));
        assertEquals(8, constrained.airAcceleration());
        assertEquals(20, constrained.groundAcceleration());
        assertEquals(2, constrained.groundFriction());
        assertEquals(1.5, constrained.airFriction());
        assertEquals(80, constrained.maxSpeedBps());
        policy.enabled = false;
        assertFalse(policy.constrain(MovementSettings.defaults()).enabled());
    }

    @Test void chainCanRestartAfterAnInterruptionWithoutAnyUnlockGesture() {
        var state = new HopState();
        for (int restart = 0; restart < 3; restart++) {
            state.reset();
            state.jump(64, restart * 60 + 1, true);
            assertFalse(state.active());
            state.jump(64, restart * 60 + 13, true);
            assertTrue(state.active());
        }
    }

    @Test void remoteChainResetClearsOldMomentumAndAllowsFreshJumps() {
        var state = new HopState();
        state.setActive(true); state.retain(.8, .4); state.impactSpeedBps(20);
        state.applyRemote(false);
        assertFalse(state.active()); assertEquals(0, state.retainedSpeed()); assertEquals(0, state.impactSpeedBps());
        state.jump(64, 1, true); state.jump(64, 13, true);
        assertTrue(state.active());
    }
}
