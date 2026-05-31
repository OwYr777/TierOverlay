package dev.owyr.tierplates.client.render;

import dev.owyr.tierplates.client.TierPlatesClient;
import dev.owyr.tierplates.client.config.TierPlatesConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;

public final class DuplicateServerTagSuppressor {
    private static final double MAX_HORIZONTAL_DISTANCE_SQUARED = 1.55D;
    private static final double MIN_VERTICAL_OFFSET = 1.15D;
    private static final double MAX_VERTICAL_OFFSET = 3.45D;

    private DuplicateServerTagSuppressor() {
    }

    public static boolean shouldHide(double x, double y, double z, Text label) {
        TierPlatesConfig config = TierPlatesClient.config();
        if (!config.enabled || !config.showNametags || !config.hideDuplicateServerTags || label == null) {
            return false;
        }

        String plainLabel = label.getString();
        if (looksLikeHealthIndicator(plainLabel)) {
            return false;
        }
        if (!looksLikeNameTagLine(plainLabel)) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            return false;
        }

        for (PlayerEntity player : world.getPlayers()) {
            if (!isRelevantPlayer(client, player)) {
                continue;
            }
            double dy = y - player.getY();
            if (dy < MIN_VERTICAL_OFFSET || dy > MAX_VERTICAL_OFFSET) {
                continue;
            }
            double dx = x - player.getX();
            double dz = z - player.getZ();
            if (dx * dx + dz * dz > MAX_HORIZONTAL_DISTANCE_SQUARED) {
                continue;
            }
            if (TierNameplateRenderer.hasRecentNameplate(player)
                    && (matchesPlayerLine(plainLabel, player) || looksLikeStackedNameTagLine(plainLabel))) {
                return true;
            }
        }

        return false;
    }

    private static boolean isRelevantPlayer(MinecraftClient client, PlayerEntity player) {
        return player != null
                && player.isAlive()
                && !player.isInvisibleTo(client.player);
    }

    private static boolean matchesPlayerLine(String label, PlayerEntity player) {
        String normalized = normalize(label);
        return containsName(normalized, player.getNameForScoreboard())
                || containsName(normalized, player.getName().getString())
                || containsName(normalized, player.getDisplayName().getString());
    }

    private static boolean containsName(String normalizedLabel, String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String normalizedName = normalize(name);
        return !normalizedName.isBlank() && normalizedLabel.contains(normalizedName);
    }

    private static boolean looksLikeNameTagLine(String label) {
        if (label == null || label.isBlank()) {
            return false;
        }

        String trimmed = label.trim();
        if (trimmed.length() > 48) {
            return false;
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        return !lower.contains("http://")
                && !lower.contains("https://")
                && !lower.contains("click")
                && !lower.contains("join");
    }

    private static boolean looksLikeStackedNameTagLine(String label) {
        String trimmed = label.trim();
        return trimmed.length() <= 32
                && !trimmed.contains(":")
                && !trimmed.contains("/")
                && !trimmed.contains("\\");
    }

    private static boolean looksLikeHealthIndicator(String label) {
        String trimmed = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isBlank()) {
            return false;
        }

        return trimmed.contains("❤")
                || trimmed.contains("♥")
                || trimmed.contains("hp")
                || trimmed.contains("health")
                || trimmed.contains("leben")
                || trimmed.contains("life")
                || trimmed.matches("[0-9]{1,3}(\\.[0-9])?")
                || trimmed.matches("[0-9]{1,3}(\\.[0-9])?\\s*/\\s*[0-9]{1,3}(\\.[0-9])?")
                || trimmed.matches("[0-9]{1,3}(\\.[0-9])?\\s*(❤|♥|hp)");
    }

    private static String normalize(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }
}
