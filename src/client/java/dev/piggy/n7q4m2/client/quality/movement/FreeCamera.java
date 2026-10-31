package dev.piggy.n7q4m2.client.quality.movement;

import com.mojang.blaze3d.platform.InputConstants;
import dev.piggy.n7q4m2.client.PiggyClient;
import dev.piggy.n7q4m2.movement.HopAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/** An unregistered client camera entity. Its position is never used as the player's network position. */
public final class FreeCamera {
    private static CameraEntity camera;
    private static LocalPlayer owner;
    private static CameraType previousView;
    private FreeCamera() {}
    public static boolean active() { return camera != null; }
    public static void init() {
        var key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.piggy.free_camera", InputConstants.Type.KEYBOARD,
                InputConstants.KEY_F4, PiggyClient.KEY_CATEGORY));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop());
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                if (client.gui.screen() == null && !InputConstants.isKeyDown(InputConstants.KEY_F3)) setEnabled(!active());
            }
            if (!active()) return;
            if (client.player != owner || client.level != camera.level() || !owner.isAlive() || client.getCameraEntity() != camera) {
                stop(); return;
            }
            camera.setOldPosAndRot();
        });
    }
    public static void setEnabled(boolean enabled) {
        if (!enabled) { stop(); return; }
        if (active()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !client.player.isAlive()) return;
        AntiAfk.stop();
        owner = client.player;
        ((HopAccess)owner).piggy$state().reset();
        owner.setDeltaMovement(0, owner.getDeltaMovement().y, 0);
        camera = new CameraEntity(client);
        camera.setPos(owner.getEyePosition());
        camera.setYRot(owner.getYRot()); camera.setXRot(owner.getXRot()); camera.setOldPosAndRot();
        previousView = client.options.getCameraType();
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.setCameraEntity(camera);
    }
    public static void stop() {
        if (!active()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.getCameraEntity() == camera) client.setCameraEntity(client.player);
        if (previousView != null) client.options.setCameraType(previousView);
        camera = null; owner = null; previousView = null;
    }
    public static void turn(LocalPlayer player, double x, double y) {
        if (active() && player == owner) camera.turn(x, y);
        else player.turn(x, y);
    }
    public static void move(Input input) {
        Minecraft client = Minecraft.getInstance();
        if (!active() || client.gui.screen() != null || client.isPaused()) return;
        double forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
        double side = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
        double vertical = (input.jump() ? 1 : 0) - (input.shift() ? 1 : 0);
        double yaw = Math.toRadians(camera.getYRot());
        Vec3 direction = camera.getViewVector(1).scale(forward)
                .add(Math.cos(yaw) * side, vertical, Math.sin(yaw) * side);
        if (direction.lengthSqr() > 1) direction = direction.normalize();
        double speed = PiggyClient.config.freeCameraSpeedBps / 20 * (input.sprint() ? 3 : 1);
        camera.setPos(camera.position().add(direction.scale(speed)));
    }
    private static final class CameraEntity extends net.minecraft.world.entity.Marker {
        CameraEntity(Minecraft client) { super(EntityTypes.MARKER, client.level); noPhysics = true; setNoGravity(true); }
        @Override public boolean isSpectator() { return true; }
    }
}
