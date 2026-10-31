package dev.piggy.n7q4m2.client;

import dev.piggy.n7q4m2.config.ServerPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientConfigTest {
    @Test void reusesUnchangedSettingsWithoutPersistingTheCache() {
        var config = new ClientConfig(); var first = config.movement();
        assertSame(first, config.movement()); assertSame(first, config.movement(new ServerPolicy()));
        config.airAcceleration = 5.5; var changed = config.movement();
        assertNotSame(first, changed); assertEquals(5.5, changed.airAcceleration());
        config.bhopEnabled = true; assertTrue(config.movement().enabled());
        assertFalse(new com.google.gson.Gson().toJson(config).contains("cachedMovement"));
        var policy = new ServerPolicy(); policy.maxAirAcceleration = 3;
        assertEquals(3, config.movement(policy).airAcceleration());
        policy.enabled = false; assertFalse(config.movement(policy).enabled());
    }
    @Test void savesValidPreferencesAboveDefaultServerBounds() {
        var c = new ClientConfig(); c.airAcceleration = 30; c.maxSpeedBps = 120;
        c.sanitize();
        assertEquals(30, c.airAcceleration); assertEquals(120, c.maxSpeedBps);
        var policy = new ServerPolicy();
        assertEquals(20, policy.constrain(c.movement()).airAcceleration());
        assertEquals(80, policy.constrain(c.movement()).maxSpeedBps());
        policy.maxAirAcceleration = 40; policy.maxSpeedCapBps = 200; policy.sanitize();
        assertEquals(c.movement(), policy.constrain(c.movement()));
    }
    @Test void clampsUnsupportedValuesAndPreservesToggle() {
        var c = new ClientConfig(); c.airAcceleration = Double.NaN; c.maxSpeedBps = 999;
        c.hudX = Double.POSITIVE_INFINITY; c.bhopEnabled = false; c.sanitize();
        assertEquals(8, c.airAcceleration); assertEquals(200, c.maxSpeedBps);
        assertEquals(.5, c.hudX); assertFalse(c.bhopEnabled);
    }

    @Test void newAndLegacyConfigsStartWithBhopDisabled() {
        assertFalse(new ClientConfig().movement().enabled());
        assertFalse(dev.piggy.n7q4m2.config.MovementSettings.defaults().enabled());
        var legacy = new com.google.gson.Gson().fromJson("{\"enabled\":true,\"airAcceleration\":5.5}", ClientConfig.class);
        assertFalse(legacy.bhopEnabled);
        assertEquals(5.5, legacy.airAcceleration);
    }

    @Test void explicitMenuPreferenceSurvivesSaveReloadAndCopy() {
        var gson = new com.google.gson.Gson();
        for (boolean value : new boolean[]{true, false}) {
            var chosen = new ClientConfig(); chosen.bhopEnabled = value;
            var reloaded = gson.fromJson(gson.toJson(chosen.copy()), ClientConfig.class);
            reloaded.sanitize();
            assertEquals(value, reloaded.bhopEnabled);
            assertEquals(value, new ServerPolicy().constrain(reloaded.movement()).enabled());
        }
    }

    @Test void serverCannotEnableAnOffPreferenceAndCanForbidAnOnPreference() {
        var policy = new ServerPolicy(); var config = new ClientConfig();
        assertFalse(policy.constrain(config.movement()).enabled());
        config.bhopEnabled = true;
        assertTrue(policy.constrain(config.movement()).enabled());
        policy.enabled = false;
        assertFalse(policy.constrain(config.movement()).enabled());
        assertTrue(config.bhopEnabled);
    }

    @Test void clientModeUsesSavedPreferenceAndFullLocalRangesWithoutServerPolicy() {
        var config = new ClientConfig();
        assertFalse(config.movement(null).enabled());
        config.bhopEnabled = true; config.airAcceleration = 30; config.maxSpeedBps = 120;
        config.sanitize();
        assertEquals(config.movement(), config.movement(null));
        assertTrue(config.movement(null).enabled());
        assertEquals(30, config.movement(null).airAcceleration());
        assertEquals(120, config.movement(null).maxSpeedBps());
    }

    @Test void serverPolicyOverridesClientModeOnlyWhilePresent() {
        var config = new ClientConfig(); config.bhopEnabled = true; config.maxSpeedBps = 120;
        var policy = new ServerPolicy();
        assertEquals(80, config.movement(policy).maxSpeedBps());
        policy.enabled = false;
        assertFalse(config.movement(policy).enabled());
        assertTrue(config.bhopEnabled);
        assertTrue(config.movement(null).enabled());
        assertEquals(120, config.movement(null).maxSpeedBps());
    }

    @Test void freeCameraSpeedCopiesPersistsAndRejectsNonFiniteValues() {
        var c = new ClientConfig(); c.freeCameraSpeedBps = 25.5;
        var gson = new com.google.gson.Gson();
        var loaded = gson.fromJson(gson.toJson(c.copy()), ClientConfig.class);
        assertEquals(25.5, loaded.freeCameraSpeedBps);
        loaded.freeCameraSpeedBps = Double.NaN; loaded.sanitize(); assertEquals(12, loaded.freeCameraSpeedBps);
        loaded.freeCameraSpeedBps = 999; loaded.sanitize(); assertEquals(100, loaded.freeCameraSpeedBps);
    }
}
