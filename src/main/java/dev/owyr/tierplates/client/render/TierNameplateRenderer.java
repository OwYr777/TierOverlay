package dev.owyr.tierplates.client.render;

import dev.owyr.tierplates.client.TierPlatesClient;
import dev.owyr.tierplates.client.config.TierPlatesConfig;
import dev.owyr.tierplates.client.data.PlayerTierProfile;
import dev.owyr.tierplates.client.data.TierEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.WeakHashMap;

public final class TierNameplateRenderer {
    private static final int WHITE = 0xFFFFFF;
    private static final int PIPE = 0xDADADA;
    private static final long RECENT_NAMEPLATE_MS = 900L;
    private static final Map<PlayerEntityRenderState, Optional<Text>> UPPER_LINES = new WeakHashMap<>();
    private static final Map<UUID, Long> RECENT_NAMEPLATES = new ConcurrentHashMap<>();

    private TierNameplateRenderer() {
    }

    public static void decorateState(PlayerEntity player, PlayerEntityRenderState state) {
        TierPlatesConfig config = TierPlatesClient.config();
        if (!config.enabled || !config.showNametags) {
            forget(player, state);
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        boolean ownPlayer = client.player != null && player.getUuid().equals(client.player.getUuid());
        if (state.displayName == null && !shouldForceMissingNameplate(config, ownPlayer)) {
            forget(player, state);
            return;
        }
        if (state.squaredDistanceToCamera > 4096.0D) {
            forget(player, state);
            return;
        }
        if (client.player != null && player.isInvisibleTo(client.player)) {
            forget(player, state);
            return;
        }

        boolean hasNativeNameplate = state.displayName != null;
        Text vanillaName = hasNativeNameplate ? state.displayName : player.getDisplayName();
        Optional<DecoratedName> optionalDecoratedName = decoratedName(player, vanillaName, hasNativeNameplate);
        if (optionalDecoratedName.isEmpty()) {
            forget(player, state);
            return;
        }

        if (state.nameLabelPos == null) {
            state.nameLabelPos = player.getAttachments()
                    .getPointNullable(EntityAttachmentType.NAME_TAG, 0, player.getLerpedYaw(client.getRenderTickCounter().getTickProgress(false)));
        }

        DecoratedName decoratedName = optionalDecoratedName.get();
        state.displayName = decoratedName.lower();
        state.playerName = decoratedName.upper().orElse(null);
        synchronized (UPPER_LINES) {
            UPPER_LINES.put(state, decoratedName.upper());
        }
        RECENT_NAMEPLATES.put(player.getUuid(), System.currentTimeMillis());
    }

    public static void enforceBeforeRender(PlayerEntityRenderState state) {
        synchronized (UPPER_LINES) {
            if (UPPER_LINES.containsKey(state)) {
                state.playerName = UPPER_LINES.get(state).orElse(null);
            }
        }
    }

    public static boolean hasRecentNameplate(PlayerEntity player) {
        Long lastRender = RECENT_NAMEPLATES.get(player.getUuid());
        if (lastRender == null) {
            return false;
        }

        boolean recent = System.currentTimeMillis() - lastRender <= RECENT_NAMEPLATE_MS;
        if (!recent) {
            RECENT_NAMEPLATES.remove(player.getUuid(), lastRender);
        }
        return recent;
    }

    private static boolean shouldForceMissingNameplate(TierPlatesConfig config, boolean ownPlayer) {
        TierPlatesConfig.NameplateMode mode = config.nameplateMode == null
                ? (config.forceNameplates ? TierPlatesConfig.NameplateMode.FORCE_ALL : TierPlatesConfig.NameplateMode.OWN_F5)
                : config.nameplateMode;
        return switch (mode) {
            case VANILLA_ONLY -> false;
            case OWN_F5 -> ownPlayer;
            case FORCE_ALL -> true;
        };
    }

    private static Optional<DecoratedName> decoratedName(PlayerEntity player, Text vanillaName, boolean hasNativeNameplate) {
        TierPlatesConfig config = TierPlatesClient.config();
        MinecraftClient client = MinecraftClient.getInstance();
        if (!config.enabled || client.player == null) {
            return Optional.empty();
        }

        Optional<PlayerTierProfile> optionalProfile = TierTextFormatter.profileFor(player.getUuid(), player.getNameForScoreboard(), vanillaName);
        if (optionalProfile.isEmpty()) {
            return Optional.empty();
        }

        PlayerTierProfile profile = optionalProfile.get();
        Optional<TierEntry> left = TierTextFormatter.entryFor(profile, TierTextFormatter.sourceFor(config, TierPlatesConfig.Side.LEFT), config.displayMode);
        Optional<TierEntry> right = TierTextFormatter.entryFor(profile, TierTextFormatter.sourceFor(config, TierPlatesConfig.Side.RIGHT), config.displayMode);
        Optional<String> subtier = profile.subtier();

        if (left.isEmpty() && right.isEmpty() && subtier.isEmpty()) {
            return Optional.empty();
        }

        boolean drawName = config.showNameInNametag;
        Text lower = left.isPresent() || right.isPresent()
                ? nameLine(vanillaName, left, right, config.showIcons, drawName)
                : vanillaName;
        Optional<Text> upper = subtier.map(value -> subtierLine(vanillaName, value));
        return Optional.of(new DecoratedName(upper, lower));
    }

    private static void forget(PlayerEntity player, PlayerEntityRenderState state) {
        synchronized (UPPER_LINES) {
            UPPER_LINES.remove(state);
        }
        RECENT_NAMEPLATES.remove(player.getUuid());
    }

    private static MutableText badge(TierEntry entry, boolean showIcon) {
        return TierTextFormatter.badge(entry, showIcon);
    }

    private static Text nameLine(Text name, Optional<TierEntry> left, Optional<TierEntry> right, boolean showIcon, boolean showName) {
        if (!showName) {
            return compactSideBadges(left, right, showIcon);
        }

        MutableText line = Text.empty();
        left.ifPresent(entry -> line.append(badge(entry, showIcon)).append("  "));
        line.append(Text.literal("| ").setStyle(Style.EMPTY.withColor(PIPE)));
        line.append(name.copy().setStyle(Style.EMPTY.withColor(WHITE)));
        line.append(Text.literal(" |").setStyle(Style.EMPTY.withColor(PIPE)));
        right.ifPresent(entry -> line.append("  ").append(badge(entry, showIcon)));
        return line;
    }

    private static Text compactSideBadges(Optional<TierEntry> left, Optional<TierEntry> right, boolean showIcon) {
        MutableText line = Text.empty();
        if (left.isPresent() && right.isPresent()) {
            return line.append(badge(left.get(), showIcon)).append("      ").append(badge(right.get(), showIcon));
        }
        if (left.isPresent()) {
            return line.append(badge(left.get(), showIcon)).append("      ");
        }
        if (right.isPresent()) {
            return line.append("      ").append(badge(right.get(), showIcon));
        }
        return line;
    }

    private static Text subtierLine(Text name, String subtier) {
        MinecraftClient client = MinecraftClient.getInstance();
        int nameWidth = client.textRenderer == null ? name.getString().length() * 6 : client.textRenderer.getWidth(name);
        int subtierWidth = client.textRenderer == null ? subtier.length() * 6 : client.textRenderer.getWidth(subtier);
        int paddingWidth = Math.max(8, (nameWidth - subtierWidth) / 2 + 5);
        int padding = Math.max(1, paddingWidth / 4);
        String spaces = " ".repeat(padding);
        return Text.literal("|").setStyle(Style.EMPTY.withColor(PIPE))
                .append(Text.literal(spaces))
                .append(Text.literal(subtier).setStyle(Style.EMPTY.withColor(0xFFE08A)))
                .append(Text.literal(spaces))
                .append(Text.literal("|").setStyle(Style.EMPTY.withColor(PIPE)));
    }

    private record DecoratedName(Optional<Text> upper, Text lower) {
    }
}
