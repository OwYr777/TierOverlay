package dev.owyr.tierplates.client;

import dev.owyr.tierplates.client.config.ConfigManager;
import dev.owyr.tierplates.client.config.TierPlatesConfig;
import dev.owyr.tierplates.client.screen.TierPlatesConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class TierPlatesClient implements ClientModInitializer {
    private static TierPlatesConfig config;
    private static KeyBinding openSettingsKey;

    @Override
    public void onInitializeClient() {
        config = ConfigManager.load();
        registerKeybind();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("tiersbyowyr7").executes(context -> {
                openSettings();
                return 1;
            }));
        });
    }

    public static void openSettings() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.setScreen(new TierPlatesConfigScreen(client.currentScreen)));
    }

    private static void registerKeybind() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("tierplates", "tierplates"));
        openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tierplates.open_settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettingsKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new TierPlatesConfigScreen(null));
                }
            }
        });
    }

    public static TierPlatesConfig config() {
        if (config == null) {
            config = new TierPlatesConfig();
        }
        return config;
    }

    public static void saveConfig() {
        ConfigManager.save(config());
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("TierPlates config saved"), true);
        }
    }

    public static void resetConfig() {
        config = new TierPlatesConfig();
        ConfigManager.save(config);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("TierPlates config reset"), true);
        }
    }
}
