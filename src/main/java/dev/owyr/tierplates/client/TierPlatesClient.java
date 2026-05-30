package dev.owyr.tierplates.client;

import dev.owyr.tierplates.client.config.ConfigManager;
import dev.owyr.tierplates.client.config.TierPlatesConfig;
import dev.owyr.tierplates.client.screen.TierPlatesConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class TierPlatesClient implements ClientModInitializer {
    private static TierPlatesConfig config;

    @Override
    public void onInitializeClient() {
        config = ConfigManager.load();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("tiersbyowyr7").executes(context -> {
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> client.setScreen(new TierPlatesConfigScreen(client.currentScreen)));
                return 1;
            }));
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
