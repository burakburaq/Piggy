package dev.piggy.n7q4m2.client;

import dev.piggy.n7q4m2.client.quality.movement.AntiAfkSequence;
import dev.piggy.n7q4m2.client.quality.QualityConfig;
import org.junit.jupiter.api.Test;
import java.util.Random;
import java.util.ArrayDeque;
import static dev.piggy.n7q4m2.client.quality.movement.AntiAfkSequence.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class AntiAfkSequenceTest {
    private static final long MS = 1_000_000;
    private static final class Scripted extends Random {
        final ArrayDeque<Integer> values = new ArrayDeque<>();
        Scripted(int... choices) { for (int n : choices) values.add(n); }
        @Override public int nextInt(int bound) {
            int value = values.remove();
            assertTrue(value >= 0 && value < bound);
            return value;
        }
    }
    @Test void probabilityTicketsSplitIntoTwentyFiveTwentyFiveAndFifty() {
        int crouch = 0, jump = 0, movement = 0;
        for (int ticket = 0; ticket < 100; ticket++) {
            var seq = new AntiAfkSequence(new Scripted(ticket, 0, 0));
            var action = seq.sample(0);
            if (action == CROUCH) crouch++;
            else if (action == JUMP) jump++;
            else { assertEquals(FORWARD, action); movement++; }
        }
        assertEquals(25, crouch); assertEquals(25, jump); assertEquals(50, movement);
    }
    @Test void allFourDirectionsCanBeChosenIndependently() {
        var directions = new AntiAfkSequence.Action[]{FORWARD, LEFT, BACKWARD, RIGHT};
        for (int i = 0; i < 4; i++) assertEquals(directions[i], new AntiAfkSequence(new Scripted(50, i, 0)).sample(0));
    }
    @Test void shortestCrouchAndWaitReleaseBeforeChoosingTheNextAction() {
        var seq = new AntiAfkSequence(new Scripted(0, 0, 0, 25));
        assertEquals(CROUCH, seq.sample(0));
        assertEquals(CROUCH, seq.sample(100 * MS - 1));
        assertEquals(NONE, seq.sample(100 * MS));
        assertEquals(NONE, seq.sample(600 * MS - 1));
        assertEquals(JUMP, seq.sample(600 * MS));
    }
    @Test void longestDirectionHoldAndPauseIncludeTheirUpperBounds() {
        var seq = new AntiAfkSequence(new Scripted(99, 3, 200, 1500, 25));
        assertEquals(RIGHT, seq.sample(0));
        assertEquals(RIGHT, seq.sample(300 * MS - 1));
        assertEquals(NONE, seq.sample(300 * MS));
        assertEquals(NONE, seq.sample(2300 * MS - 1));
        assertEquals(JUMP, seq.sample(2300 * MS));
    }
    @Test void jumpIsOneTickAndDoesNotHoldSpaceThroughThePause() {
        var seq = new AntiAfkSequence(new Scripted(25, 0, 0, 0));
        assertEquals(JUMP, seq.sample(0));
        assertEquals(JUMP, seq.sample(50 * MS - 1));
        assertEquals(NONE, seq.sample(50 * MS));
        assertEquals(NONE, seq.sample(550 * MS - 1));
        assertEquals(CROUCH, seq.sample(550 * MS));
    }
    @Test void stopOrMenuPauseDropsPendingActionsAndRestartsFresh() {
        var seq = new AntiAfkSequence(new Scripted(0, 200, 50, 2, 0));
        assertEquals(CROUCH, seq.sample(0));
        seq.reset();
        assertEquals(BACKWARD, seq.sample(10 * MS));
    }
    @Test void delayedReleaseStillWaitsBeforeStartingAnotherAction() {
        var seq = new AntiAfkSequence(new Scripted(0, 0, 0, 25));
        assertEquals(CROUCH, seq.sample(0));
        assertEquals(NONE, seq.sample(5000 * MS));
        assertEquals(NONE, seq.sample(5500 * MS - 1));
        assertEquals(JUMP, seq.sample(5500 * MS));
    }
    @Test void activeAntiAfkIsSessionOnlyWhileOtherOptionsArePreserved() {
        var original = new QualityConfig(); original.antiAfk = true; original.simpleDebug = false;
        var copy = original.copy(); assertTrue(copy.antiAfk);
        var gson = new com.google.gson.Gson(); var json = gson.toJson(copy);
        assertFalse(com.google.gson.JsonParser.parseString(json).getAsJsonObject().has("antiAfk"));
        var reloaded = gson.fromJson(json, QualityConfig.class);
        assertFalse(reloaded.antiAfk); assertFalse(reloaded.simpleDebug);
        assertEquals(original.settings(), reloaded.settings());
    }

    @Test void optionalActionsAreRemovedWithoutReducingTheRemainingChances() {
        for (boolean jumpEnabled : new boolean[]{false, true}) for (boolean crouchEnabled : new boolean[]{false, true}) {
            int crouch = 0, jump = 0, move = 0;
            for (int ticket = 0; ticket < 100; ticket++) {
                var seq = new AntiAfkSequence(new Scripted(ticket, 0, 0));
                var action = seq.sample(0, jumpEnabled, crouchEnabled);
                if (action == CROUCH) crouch++; else if (action == JUMP) jump++; else { assertEquals(FORWARD, action); move++; }
            }
            assertEquals(crouchEnabled ? 25 : 0, crouch);
            assertEquals(jumpEnabled ? 25 : 0, jump);
            assertEquals(100 - crouch - jump, move);
        }
    }
    @Test void jumpAndCrouchPreferencesPersistWithoutPersistingRunningState() {
        var config = new QualityConfig(); config.antiAfk = true; config.antiAfkJump = false; config.antiAfkCrouch = false;
        var gson = new com.google.gson.Gson();
        var loaded = gson.fromJson(gson.toJson(config.copy()), QualityConfig.class);
        assertFalse(loaded.antiAfkJump); assertFalse(loaded.antiAfkCrouch); assertFalse(loaded.antiAfk);
        var legacy = gson.fromJson("{}", QualityConfig.class);
        assertTrue(legacy.antiAfkJump); assertTrue(legacy.antiAfkCrouch);
    }
}
