package dev.piggy.n7q4m2.movement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MovementEnvelopeTest {
    @Test void packetsCannotMintTimeOrRaiseMomentum() {
        var e = new MovementEnvelope();
        e.advance(10, .25, 2, .08, true);
        assertFalse(e.consume(4));
        assertTrue(e.consume(.25));
        assertTrue(e.consume(.25));
        e.advance(10, .25, 2, .08, true);
        assertFalse(e.consume(.01));
        assertEquals(.25, e.speed());
    }
    @Test void tickClockLimitsAccelerationAndTotalPathIncludingReversals() {
        var e = new MovementEnvelope();
        e.advance(10, .25, 2, .04, true);
        e.consume(.5);
        e.advance(11, .25, 2, .04, true);
        assertEquals(.29, e.speed(), 1e-9);
        assertTrue(e.consume(.15));
        assertFalse(e.consume(.15));
        e.advance(10000, .25, 2, .04, true);
        assertTrue(e.credit() <= e.speed() * 20);
        assertTrue(e.speed() <= 1.09 + 1e-9);
    }
    @Test void serverImpulsesAndWallMomentumSurviveWithoutNewAccelerationAboveCap() {
        var e = new MovementEnvelope();
        e.advance(10, .25, 2, .08, true);
        e.motion(4);
        e.advance(11, .25, 2, .08, true);
        assertEquals(4, e.speed());
        assertTrue(e.consume(4));
        e.advance(12, .25, 2, .08, true);
        assertEquals(4, e.speed());
        e.impulse(1);
        assertEquals(5, e.speed());
        e.restartClock(); e.advance(1, .25, 2, .08, true);
        assertEquals(5, e.speed());
        e.reset(); e.advance(2, .25, 2, .08, true);
        assertEquals(.25, e.speed());
        assertFalse(e.consume(4));
    }
    @Test void rejectsInvalidDistancesAndMotion() {
        var e = new MovementEnvelope();
        e.advance(1, .25, 2, .08, true);
        e.motion(Double.NaN); e.impulse(Double.POSITIVE_INFINITY);
        assertFalse(e.consume(Double.NaN)); assertFalse(e.consume(-1));
        assertEquals(.25, e.speed());
    }
    @Test void correctionsAndRepeatedSameTickAcknowledgementsCannotMintCredit() {
        var e = new MovementEnvelope(); e.advance(10, .25, 2, .08, true);
        e.consume(.4);
        for (int i = 0; i < 100; i++) {
            e.forfeit(); e.advance(10, .25, 2, .08, true);
            assertFalse(e.consume(.1));
        }
        e.advance(11, .25, 2, .08, true);
        assertTrue(e.consume(.3));
    }
    @Test void sixTickDelayedBurstFitsWithoutPacketCountGrant() {
        var e = new MovementEnvelope(); e.advance(10, .25, 2, .08, true); e.motion(2);
        e.forfeit(); e.advance(16, .25, 2, .08, true);
        for (int i = 0; i < 6; i++) assertTrue(e.consume(2));
        assertFalse(e.consume(.01));
    }
}
