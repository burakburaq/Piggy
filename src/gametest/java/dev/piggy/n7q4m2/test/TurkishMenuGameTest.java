package dev.piggy.n7q4m2.test;

import dev.piggy.n7q4m2.client.quality.QualityConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Only loads Turkish and checks the menu affected by the encoding regression. */
public final class TurkishMenuGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(960, 720);
        AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
        context.runOnClient(c -> {
            c.options.guiScale().set(3);
            c.resizeGui();
            c.getLanguageManager().setSelected("tr_tr");
            c.options.languageCode = "tr_tr";
            reload.set(c.reloadResourcePacks());
        });
        context.waitFor(c -> reload.get().isDone() && c.gui.overlay() == null, 400);
        if (reload.get().isCompletedExceptionally()) throw new AssertionError("Language reload failed");
        context.setScreen(() -> new QualityConfigScreen(null, QualityConfigScreen.Section.INTERACTIVE));
        context.runOnClient(c -> {
            String[][] labels = {
                {"placement_assist", "Köprü yerleştirme yardımı"},
                {"paired_doors", "Çift kapı"},
                {"bucket_cycle", "Otomatik kova seçimi"}
            };
            for (var entry : labels) {
                String key = "piggy.quality." + entry[0];
                if (!Component.translatable(key).getString().equals(entry[1]))
                    throw new AssertionError("Incorrect Turkish translation: " + key);
                var button = c.gui.screen().children().stream().filter(Button.class::isInstance)
                        .map(Button.class::cast).filter(b -> b.getMessage().getString().startsWith(entry[1] + ": "))
                        .findFirst().orElseThrow();
                if (c.font.width(button.getMessage()) > button.getWidth() - 8)
                    throw new AssertionError("Turkish label exceeds its button: " + key);
            }
        });
        context.waitTicks(2);
        context.takeScreenshot("turkish-interactive-fixed");
        context.setScreen(() -> null);
        org.slf4j.LoggerFactory.getLogger("Piggy").info("TURKISH_MENU_PASS");
    }
}
