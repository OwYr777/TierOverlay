package dev.owyr.tierplates.client.render;

import dev.owyr.tierplates.client.TierPlatesClient;
import dev.owyr.tierplates.client.config.TierPlatesConfig;
import dev.owyr.tierplates.client.data.GameMode;
import dev.owyr.tierplates.client.data.PlayerTierProfile;
import dev.owyr.tierplates.client.data.TierDataCache;
import dev.owyr.tierplates.client.data.TierEntry;
import dev.owyr.tierplates.client.data.TierSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TierTextFormatter {
    private TierTextFormatter() {
    }

    public static Text decorateFlatName(UUID uuid, String fallbackName, Text vanillaName) {
        TierPlatesConfig config = TierPlatesClient.config();
        if (!config.showTab) {
            return vanillaName;
        }
        return decorateInlineName(uuid, fallbackName, vanillaName);
    }

    private static Text decorateInlineName(UUID uuid, String fallbackName, Text vanillaName) {
        TierPlatesConfig config = TierPlatesClient.config();
        if (!config.enabled || uuid == null) {
            return vanillaName;
        }

        Optional<PlayerTierProfile> optionalProfile = TierDataCache.getBestEffort(uuid, fallbackName, false);
        if (optionalProfile.isEmpty()) {
            return vanillaName;
        }

        PlayerTierProfile profile = optionalProfile.get();
        Optional<TierEntry> left = entryFor(profile, sourceFor(config, TierPlatesConfig.Side.LEFT), config.displayMode);
        Optional<TierEntry> right = entryFor(profile, sourceFor(config, TierPlatesConfig.Side.RIGHT), config.displayMode);
        if (left.isEmpty() && right.isEmpty()) {
            return vanillaName;
        }

        MutableText text = Text.empty();
        left.ifPresent(entry -> text.append(badge(entry, config.showIcons)).append(" "));
        text.append(Text.literal("| ").setStyle(Style.EMPTY.withColor(0xDADADA)));
        text.append(vanillaName.copy());
        text.append(Text.literal(" |").setStyle(Style.EMPTY.withColor(0xDADADA)));
        right.ifPresent(entry -> text.append(" ").append(badge(entry, config.showIcons)));
        return text;
    }

    public static Text decorateChatMessage(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        TierPlatesConfig config = TierPlatesClient.config();
        if (!config.enabled || !config.showChat || client.getNetworkHandler() == null) {
            return message;
        }

        String plain = message.getString();
        List<Replacement> replacements = new ArrayList<>();
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().name();
            if (plain.startsWith("<" + name + ">")) {
                Text decoratedName = decorateInlineName(entry.getProfile().id(), name, Text.literal(name));
                return Text.literal("<").append(decoratedName).append(Text.literal(">" + plain.substring(name.length() + 2)));
            }
            if (plain.startsWith(name + ":")) {
                Text decoratedName = decorateInlineName(entry.getProfile().id(), name, Text.literal(name));
                return decoratedName.copy().append(Text.literal(plain.substring(name.length())));
            }
            Text decoratedName = decorateInlineName(entry.getProfile().id(), name, Text.literal(name));
            if (!decoratedName.getString().equals(name)) {
                collectNameReplacements(plain, name, decoratedName, replacements);
            }
        }

        if (replacements.isEmpty()) {
            return message;
        }

        replacements.sort(Comparator.comparingInt(Replacement::start).thenComparing((left, right) -> Integer.compare(right.end(), left.end())));
        MutableText decorated = Text.empty();
        int cursor = 0;
        for (Replacement replacement : replacements) {
            if (replacement.start() < cursor) {
                continue;
            }
            if (replacement.start() > cursor) {
                decorated.append(Text.literal(plain.substring(cursor, replacement.start())));
            }
            decorated.append(replacement.text());
            cursor = replacement.end();
        }
        if (cursor < plain.length()) {
            decorated.append(Text.literal(plain.substring(cursor)));
        }
        return decorated;
    }

    private static void collectNameReplacements(String plain, String name, Text decoratedName, List<Replacement> replacements) {
        int index = plain.indexOf(name);
        while (index >= 0) {
            int end = index + name.length();
            if (isNameBoundary(plain, index - 1) && isNameBoundary(plain, end)) {
                replacements.add(new Replacement(index, end, decoratedName));
            }
            index = plain.indexOf(name, end);
        }
    }

    private static boolean isNameBoundary(String plain, int index) {
        if (index < 0 || index >= plain.length()) {
            return true;
        }
        char c = plain.charAt(index);
        return !Character.isLetterOrDigit(c) && c != '_';
    }

    public static Optional<TierSource> sourceFor(TierPlatesConfig config, TierPlatesConfig.Side side) {
        if (config.mctiersSide == side) {
            return Optional.of(TierSource.MCTIERS);
        }
        if (config.pvptiersSide == side) {
            return Optional.of(TierSource.PVPTIERS);
        }
        return Optional.empty();
    }

    public static Optional<TierEntry> entryFor(PlayerTierProfile profile, Optional<TierSource> source, TierPlatesConfig.DisplayMode mode) {
        if (source.isEmpty()) {
            return Optional.empty();
        }
        if (mode == TierPlatesConfig.DisplayMode.BEST) {
            return profile.best(source.get());
        }
        return profile.get(source.get(), GameMode.valueOf(mode.name()));
    }

    public static MutableText badge(TierEntry entry, boolean showIcon) {
        MutableText text = Text.literal(entry.tier() + " ").setStyle(Style.EMPTY.withColor(tierColor(entry.tier())));
        if (showIcon) {
            text.append(Text.literal(entry.mode().icon).setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(iconFont(TierPlatesClient.config()))).withColor(0xFFFFFF)));
        } else {
            text.append(Text.literal(entry.mode().apiKey));
        }
        return text;
    }

    public static Identifier iconFont(TierPlatesConfig config) {
        return Identifier.of("tierplates", config.iconSize.fontId);
    }

    public static int tierColor(String tier) {
        TierPlatesConfig.TierColors colors = TierPlatesClient.config().tierColors;
        return colors == null ? 0xFFFFFF : colors.get(tier);
    }

    private record Replacement(int start, int end, Text text) {
    }
}
