package dev.piggy.n7q4m2.test;

import com.mojang.authlib.GameProfile;
import dev.piggy.n7q4m2.client.quality.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.UUID;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.util.concurrent.atomic.AtomicReference;

/** Only the new player visibility setting and its rendering path. */
public final class SeeThroughGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static int changedPixels(Path before, Path after) {
        return changedPixels(before, after, 280, 360);
    }
    private static int changedPixels(Path before, Path after, int left, int right) {
        try {
            var a = ImageIO.read(before.toFile()); var b = ImageIO.read(after.toFile()); int changed = 0;
            for (int y = 220; y < 340; y++) for (int x = left; x < right; x++) {
                int av = a.getRGB(x, y), bv = b.getRGB(x, y);
                int difference = Math.abs((av >> 16 & 255) - (bv >> 16 & 255))
                        + Math.abs((av >> 8 & 255) - (bv >> 8 & 255)) + Math.abs((av & 255) - (bv & 255));
                if (difference > 55) changed++;
            }
            return changed;
        } catch (java.io.IOException e) { throw new AssertionError("Could not compare rendered pixels", e); }
    }
    private static void toggle(Minecraft client) {
        String prefix = Component.translatable("piggy.quality.see_through_players").getString() + ": ";
        client.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow().onPress(null);
    }
    @Override public void runTest(ClientGameTestContext context) {
        var original = QualityClient.config.copy();
        var friend = new AtomicReference<RemotePlayer>();
        var second = new AtomicReference<RemotePlayer>();
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
            world.getServer().runCommand("fill -4 -60 3 4 -56 3 stone");
            context.waitFor(c -> c.player != null && c.gui.screen() == null, 200);
            context.waitTicks(25);
            context.getInput().resizeWindow(640, 480);
            context.runOnClient(c -> {
                c.options.guiScale().set(2); c.resizeGui();
                check(!new QualityConfig().seeThroughPlayers, "Feature must default to off");
                var config = QualityClient.config.copy(); config.seeThroughPlayers = false; QualityClient.save(config);
                var remote = new RemotePlayer(c.level, new GameProfile(UUID.fromString("11223344-5566-7788-9900-112233445566"), "Friend"));
                remote.setId(100000001); remote.setPos(.5, -60, 6.5); remote.setYRot(180); remote.yBodyRot = 180;
                remote.setNoGravity(true); remote.setOldPosAndRot();
                remote.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                remote.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                c.level.addEntity(remote); friend.set(remote);
                var other = new RemotePlayer(c.level, new GameProfile(UUID.fromString("11223344-5566-7788-9900-112233445567"), "SecondFriend"));
                other.setId(100000002); other.setPos(-1.5, -60, 6.5); other.setYRot(180); other.yBodyRot = 180;
                other.setNoGravity(true); other.setOldPosAndRot();
                other.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                c.level.addEntity(other); second.set(other);
            });
            context.waitTicks(20); Path off = context.takeScreenshot("see-through-off");
            context.setScreen(() -> new QualityConfigScreen(null, QualityConfigScreen.Section.INTERFACE));
            context.runOnClient(c -> {
                var widgets = c.gui.screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
                for (int i = 0; i < widgets.size(); i++) {
                    var a = widgets.get(i);
                    check(a.getRight() <= c.gui.screen().width && a.getBottom() <= c.gui.screen().height, "Settings outside screen");
                    for (int j = i + 1; j < widgets.size(); j++) {
                        var b = widgets.get(j);
                        check(!(a.getX() < b.getRight() && a.getRight() > b.getX() && a.getY() < b.getBottom() && a.getBottom() > b.getY()), "Settings overlap");
                    }
                }
                toggle(c);
            });
            context.waitTicks(2); context.takeScreenshot("see-through-menu-320");
            context.clickScreenButton("piggy.save");
            context.waitTicks(10);
            context.runOnClient(c -> {
                check(QualityClient.config.seeThroughPlayers, "Menu did not save enabled state");
                check(c.level.getEntity(friend.get().getId()) == friend.get(), "Remote player unloaded");
                var extracted = c.getEntityRenderDispatcher().extractEntity(friend.get(), 1);
                check(((SeeThroughState)extracted).piggy$seeThrough(), "Extraction lost enabled state");
                check(((SeeThroughState)c.getEntityRenderDispatcher().extractEntity(second.get(), 1)).piggy$seeThrough(), "Second player was not included in the shared pass");
                check(dev.piggy.n7q4m2.config.ConfigFiles.load("piggy-quality.json", QualityConfig.class, new QualityConfig()).seeThroughPlayers, "Setting did not persist");
            });
            context.waitTicks(10); Path armored = context.takeScreenshot("see-through-on-armored");
            check(changedPixels(off, armored) > 800, "Player and armor were not drawn through the wall");
            check(changedPixels(off, armored, 390, 480) > 500, "Second player was not drawn in the shared pass");
            context.runOnClient(c -> {
                check(QualityClient.config.copy().seeThroughPlayers, "Config copy lost the setting");
                check(!SeeThroughPlayers.includes(c.player), "Local player was included");
                friend.get().setInvisible(true);
                check(!SeeThroughPlayers.includes(friend.get()), "Invisibility was overridden");
                friend.get().setInvisible(false);
                friend.get().setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                friend.get().setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            });
            context.waitTicks(5); Path skin = context.takeScreenshot("see-through-on-skin");
            check(changedPixels(off, skin) > 800, "Player skin was not drawn through the wall");
            context.setScreen(() -> new QualityConfigScreen(null, QualityConfigScreen.Section.INTERFACE));
            context.runOnClient(SeeThroughGameTest::toggle); context.clickScreenButton("piggy.save");
            context.waitTicks(10); Path offAgain = context.takeScreenshot("see-through-off-again");
            check(changedPixels(off, offAgain) < 80, "Turning off did not restore the occluding wall");
            context.runOnClient(c -> {
                check(!SeeThroughPlayers.includes(friend.get()), "Turning off did not disable rendering");
                check(!((SeeThroughState)c.getEntityRenderDispatcher().extractEntity(friend.get(), 1)).piggy$seeThrough(), "Extraction retained an old enabled state");
                org.slf4j.LoggerFactory.getLogger("Piggy").info("SEE_THROUGH_PASS");
            });
        } finally {
            context.runOnClient(c -> QualityClient.save(original));
        }
    }
}
