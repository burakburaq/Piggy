package dev.piggy.n7q4m2.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.piggy.n7q4m2.Piggy;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigFiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private ConfigFiles() {}

    public static <T> T load(String name, Class<T> type, T defaults) {
        Path path = path(name);
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                T result = GSON.fromJson(reader, type);
                if (result != null) return result;
            } catch (IOException | RuntimeException e) {
                Piggy.LOGGER.warn("Cannot read {}; using defaults", path, e);
            }
        } else save(name, defaults);
        return defaults;
    }

    public static void save(String name, Object config) {
        try {
            Files.createDirectories(path(name).getParent());
            Files.writeString(path(name), GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) { Piggy.LOGGER.error("Cannot save {}", name, e); }
    }

    private static Path path(String name) { return FabricLoader.getInstance().getConfigDir().resolve(name); }
}
