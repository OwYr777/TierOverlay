package dev.owyr.tierplates.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("tierplates.json");

    private ConfigManager() {
    }

    public static TierPlatesConfig load() {
        if (!Files.exists(PATH)) {
            TierPlatesConfig config = new TierPlatesConfig();
            save(config);
            return config;
        }

        try {
            String json = Files.readString(PATH);
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            TierPlatesConfig config = GSON.fromJson(object, TierPlatesConfig.class);
            if (config == null) {
                return new TierPlatesConfig();
            }
            fillDefaults(config, object);
            return config;
        } catch (Exception ignored) {
            return new TierPlatesConfig();
        }
    }

    public static void save(TierPlatesConfig config) {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static void fillDefaults(TierPlatesConfig config, JsonObject object) {
        if (config.mctiersSide == null) {
            config.mctiersSide = TierPlatesConfig.Side.LEFT;
        }
        if (config.pvptiersSide == null) {
            config.pvptiersSide = TierPlatesConfig.Side.RIGHT;
        }
        if (config.displayMode == null) {
            config.displayMode = TierPlatesConfig.DisplayMode.SWORD;
        }
        if (config.iconSize == null) {
            config.iconSize = TierPlatesConfig.IconSize.MEDIUM;
        }
        if (config.nameplateMode == null) {
            config.nameplateMode = config.forceNameplates ? TierPlatesConfig.NameplateMode.FORCE_ALL : TierPlatesConfig.NameplateMode.OWN_F5;
        }
        if (config.tierColors == null) {
            config.tierColors = new TierPlatesConfig.TierColors();
        }
        if (!object.has("showNametags")) {
            config.showNametags = true;
        }
        if (!object.has("showTab")) {
            config.showTab = true;
        }
        if (!object.has("showChat")) {
            config.showChat = true;
        }
        if (!object.has("forceNameplates")) {
            config.forceNameplates = false;
        }
        if (!object.has("showNameInNametag")) {
            config.showNameInNametag = true;
        }
        if (config.previewPlayerName == null || config.previewPlayerName.isBlank()
                || config.previewPlayerName.equalsIgnoreCase("ItzRealMe")) {
            config.previewPlayerName = "Swight";
        }
    }
}
