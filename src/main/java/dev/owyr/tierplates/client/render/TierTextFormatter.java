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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TierTextFormatter {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])[A-Za-z0-9_]{3,16}(?![A-Za-z0-9_])");
    private static final Set<String> NON_PLAYER_TOKENS = Set.of(
            "admin", "builder", "clan", "default", "echt", "friend", "freunde", "global", "guild",
            "level", "member", "mod", "none", "owner", "party", "rank", "rang", "spieler", "staff",
            "stray", "team", "vip"
    );

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
        if (!config.enabled) {
            return vanillaName;
        }

        Optional<PlayerTierProfile> optionalProfile = profileFor(uuid, fallbackName, vanillaName);
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

    private static Optional<PlayerTierProfile> profileFor(UUID uuid, String fallbackName, Text visibleName) {
        Optional<PlayerTierProfile> firstResolved = Optional.empty();
        LinkedHashSet<String> candidates = nameCandidates(fallbackName, visibleName);

        if (uuid != null && isPlayerName(fallbackName)) {
            Optional<PlayerTierProfile> byUuid = TierDataCache.getBestEffort(uuid, fallbackName, false);
            if (byUuid.map(PlayerTierProfile::hasAnyData).orElse(false)) {
                return byUuid;
            }
            firstResolved = byUuid;
        }

        for (String candidate : candidates) {
            Optional<PlayerTierProfile> byName = TierDataCache.getByName(candidate, false);
            if (byName.map(PlayerTierProfile::hasAnyData).orElse(false)) {
                return byName;
            }
            if (firstResolved.isEmpty()) {
                firstResolved = byName;
            }
        }

        return firstResolved;
    }

    private static LinkedHashSet<String> nameCandidates(String fallbackName, Text visibleName) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (isPlayerName(fallbackName)) {
            candidates.add(fallbackName);
        }

        Set<String> knownPlayerNames = knownPlayerNames();
        String plain = visibleName == null ? "" : visibleName.getString();
        Matcher matcher = USERNAME_PATTERN.matcher(plain);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (isPlayerName(candidate) && isKnownPlayerCandidate(candidate, fallbackName, knownPlayerNames)) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    private static boolean isKnownPlayerCandidate(String candidate, String fallbackName, Set<String> knownPlayerNames) {
        if (fallbackName != null && candidate.equalsIgnoreCase(fallbackName)) {
            return true;
        }
        return knownPlayerNames.contains(candidate.toLowerCase(Locale.ROOT));
    }

    private static Set<String> knownPlayerNames() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            return Set.of();
        }

        Set<String> names = new LinkedHashSet<>();
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().name();
            if (isPlayerName(name)) {
                names.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    private static boolean isPlayerName(String value) {
        if (value == null || !USERNAME_PATTERN.matcher(value).matches()) {
            return false;
        }
        return !NON_PLAYER_TOKENS.contains(value.toLowerCase(Locale.ROOT));
    }

    public static Text decorateChatMessage(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        TierPlatesConfig config = TierPlatesClient.config();
        if (!config.enabled || !config.showChat || client.getNetworkHandler() == null) {
            return message;
        }

        String plain = message.getString();
        List<StyledSegment> segments = styledSegments(message);
        List<Replacement> replacements = new ArrayList<>();
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().name();
            if (name == null || name.isBlank()) {
                continue;
            }
            Text decoratedName = decorateInlineName(entry.getProfile().id(), name, Text.literal(name));
            if (!decoratedName.getString().equals(name)) {
                collectNameReplacements(plain, name, entry.getProfile().id(), replacements);
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
                appendStyledRange(decorated, segments, plain, cursor, replacement.start());
            }
            Style nameStyle = styleAt(segments, replacement.start());
            decorated.append(decorateInlineName(replacement.uuid(), replacement.name(), Text.literal(replacement.name()).setStyle(nameStyle)));
            cursor = replacement.end();
        }
        if (cursor < plain.length()) {
            appendStyledRange(decorated, segments, plain, cursor, plain.length());
        }
        return decorated;
    }

    private static void collectNameReplacements(String plain, String name, UUID uuid, List<Replacement> replacements) {
        int index = plain.indexOf(name);
        while (index >= 0) {
            int end = index + name.length();
            if (isNameBoundary(plain, index - 1) && isNameBoundary(plain, end)) {
                replacements.add(new Replacement(index, end, uuid, name));
            }
            index = plain.indexOf(name, end);
        }
    }

    private static List<StyledSegment> styledSegments(Text message) {
        List<StyledSegment> segments = new ArrayList<>();
        int[] cursor = {0};
        message.visit((style, string) -> {
            if (!string.isEmpty()) {
                segments.add(new StyledSegment(cursor[0], cursor[0] + string.length(), string, style));
                cursor[0] += string.length();
            }
            return Optional.empty();
        }, Style.EMPTY);
        return segments;
    }

    private static void appendStyledRange(MutableText target, List<StyledSegment> segments, String plain, int start, int end) {
        if (start >= end) {
            return;
        }
        if (segments.isEmpty()) {
            target.append(Text.literal(plain.substring(start, end)));
            return;
        }
        for (StyledSegment segment : segments) {
            if (segment.end() <= start) {
                continue;
            }
            if (segment.start() >= end) {
                break;
            }
            int localStart = Math.max(start, segment.start()) - segment.start();
            int localEnd = Math.min(end, segment.end()) - segment.start();
            if (localEnd > localStart) {
                target.append(Text.literal(segment.text().substring(localStart, localEnd)).setStyle(segment.style()));
            }
        }
    }

    private static Style styleAt(List<StyledSegment> segments, int position) {
        for (StyledSegment segment : segments) {
            if (position >= segment.start() && position < segment.end()) {
                return segment.style();
            }
        }
        return Style.EMPTY;
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

    private record Replacement(int start, int end, UUID uuid, String name) {
    }

    private record StyledSegment(int start, int end, String text, Style style) {
    }
}
