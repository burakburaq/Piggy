package dev.piggy.n7q4m2.client.quality.movement;

import dev.piggy.n7q4m2.client.quality.QualityClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.player.Input;
import java.util.concurrent.ThreadLocalRandom;
import static dev.piggy.n7q4m2.client.quality.movement.AntiAfkSequence.Action;

/** Client input only: never changes real key-binding state or presses operating-system keys. */
public final class AntiAfk {
    private static final AntiAfkSequence SEQUENCE = new AntiAfkSequence(ThreadLocalRandom.current());
    private AntiAfk() {}
    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop());
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (!QualityClient.config.antiAfk) SEQUENCE.reset();
            else if (client.player == null || client.level == null || !client.player.isAlive() || FreeCamera.active()) stop();
            else if (client.gui.screen() != null || client.isPaused()) SEQUENCE.reset();
        });
    }
    public static void stop() {
        QualityClient.config.antiAfk = false;
        SEQUENCE.reset();
    }
    public static void keyPressed(KeyEvent event) {
        if (!QualityClient.config.antiAfk) return;
        Options options = Minecraft.getInstance().options;
        if (options.keyUp.matches(event) || options.keyLeft.matches(event)
                || options.keyDown.matches(event) || options.keyRight.matches(event)
                || options.keyShift.matches(event) || options.keyJump.matches(event)) stop();
    }
    public static Input apply(Input physical) {
        if (!QualityClient.config.antiAfk) { SEQUENCE.reset(); return physical; }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !client.player.isAlive()) {
            stop(); return physical;
        }
        // Read freshly sampled real input before adding automation, so our own movement never cancels itself.
        if (physical.forward() || physical.backward() || physical.left() || physical.right()
                || physical.shift() || physical.jump()) {
            stop(); return physical;
        }
        if (client.gui.screen() != null || client.isPaused()) { SEQUENCE.reset(); return physical; }
        Action action = SEQUENCE.sample(System.nanoTime(), QualityClient.config.antiAfkJump, QualityClient.config.antiAfkCrouch);
        if (action == Action.NONE) return physical;
        return new Input(action == Action.FORWARD, action == Action.BACKWARD,
                action == Action.LEFT, action == Action.RIGHT, action == Action.JUMP,
                action == Action.CROUCH, physical.sprint());
    }
}
