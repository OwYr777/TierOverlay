package dev.owyr.tierplates.client.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class TierDataCache {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final Map<UUID, CompletableFuture<PlayerTierProfile>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Optional<UUID>>> NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<PlayerTierProfile>> NAME_PROFILE_CACHE = new ConcurrentHashMap<>();
    private static CompletableFuture<String> mctiersTopName;

    private TierDataCache() {
    }

    public static void clear() {
        CACHE.clear();
        NAME_CACHE.clear();
        NAME_PROFILE_CACHE.clear();
        mctiersTopName = null;
    }

    public static Optional<PlayerTierProfile> get(UUID uuid, String name, boolean demoData) {
        if (demoData) {
            return Optional.of(demoProfile(name));
        }
        if (uuid == null) {
            return Optional.empty();
        }

        CompletableFuture<PlayerTierProfile> future = CACHE.computeIfAbsent(uuid, ignored -> fetch(uuid));
        if (!future.isDone() || future.isCompletedExceptionally()) {
            return Optional.empty();
        }
        return Optional.ofNullable(future.getNow(null));
    }

    public static Optional<PlayerTierProfile> getByName(String name, boolean demoData) {
        if (demoData) {
            return Optional.of(demoProfile(name));
        }
        if (!isMinecraftName(name)) {
            return Optional.empty();
        }

        String cacheKey = name.toLowerCase(Locale.ROOT);
        CompletableFuture<PlayerTierProfile> profileFuture = NAME_PROFILE_CACHE.computeIfAbsent(cacheKey, ignored -> fetchByName(name));
        if (!profileFuture.isDone() || profileFuture.isCompletedExceptionally()) {
            return Optional.empty();
        }
        return Optional.ofNullable(profileFuture.getNow(null));
    }

    public static Optional<PlayerTierProfile> getBestEffort(UUID uuid, String name, boolean demoData) {
        Optional<PlayerTierProfile> byName = getByName(name, demoData);
        if (byName.map(PlayerTierProfile::hasAnyData).orElse(false)) {
            uuidAlias(uuid, byName.get());
            return byName;
        }

        Optional<PlayerTierProfile> byUuid = get(uuid, name, demoData);
        if (byUuid.map(PlayerTierProfile::hasAnyData).orElse(false)) {
            return byUuid;
        }
        if (isMinecraftName(name) && isNameLookupPending(name)) {
            return Optional.empty();
        }
        return byUuid.isPresent() ? byUuid : byName;
    }

    public static Optional<UUID> uuidForName(String name) {
        if (!isMinecraftName(name)) {
            return Optional.empty();
        }
        CompletableFuture<Optional<UUID>> uuidFuture = uuidFuture(name);
        if (!uuidFuture.isDone() || uuidFuture.isCompletedExceptionally()) {
            return Optional.empty();
        }
        return uuidFuture.getNow(Optional.empty());
    }

    public static Optional<String> mctiersTopName() {
        CompletableFuture<String> future = mctiersTopName;
        if (future == null) {
            future = fetchMctiersTopName();
            mctiersTopName = future;
        }
        if (!future.isDone() || future.isCompletedExceptionally()) {
            return Optional.empty();
        }
        return Optional.ofNullable(future.getNow(null)).filter(value -> !value.isBlank());
    }

    public static String mctiersStatus(UUID uuid, boolean demoData) {
        return sourceStatus(uuid, demoData, TierSource.MCTIERS);
    }

    public static String pvptiersStatus(UUID uuid, boolean demoData) {
        return sourceStatus(uuid, demoData, TierSource.PVPTIERS);
    }

    public static String subtiersStatus(UUID uuid, boolean demoData) {
        if (demoData) {
            return "demo";
        }
        CompletableFuture<PlayerTierProfile> future = CACHE.get(uuid);
        if (future == null || !future.isDone()) {
            return "loading";
        }
        if (future.isCompletedExceptionally()) {
            return "error";
        }
        PlayerTierProfile profile = future.getNow(null);
        return profile != null && profile.subtier().isPresent() ? "loaded" : "none";
    }

    private static String sourceStatus(UUID uuid, boolean demoData, TierSource source) {
        if (demoData) {
            return "demo";
        }
        CompletableFuture<PlayerTierProfile> future = CACHE.get(uuid);
        if (future == null || !future.isDone()) {
            return "loading";
        }
        if (future.isCompletedExceptionally()) {
            return "error";
        }
        PlayerTierProfile profile = future.getNow(null);
        return profile != null && profile.hasEntries(source) ? "loaded" : "none";
    }

    private static CompletableFuture<Optional<UUID>> resolveUuid(String name) {
        return fetchJson("https://api.mojang.com/users/profiles/minecraft/" + name)
                .thenApply(json -> json.flatMap(object -> {
                    JsonElement id = object.get("id");
                    if (id == null || !id.isJsonPrimitive()) {
                        return Optional.empty();
                    }
                    return uuidFromCompact(id.getAsString());
                }));
    }

    public static boolean isMinecraftName(String name) {
        return name != null && USERNAME_PATTERN.matcher(name).matches();
    }

    private static CompletableFuture<String> fetchMctiersTopName() {
        return fetchJson("https://mctiers.com/api/tier/overall?count=1")
                .thenApply(json -> json.flatMap(object -> {
                    JsonElement rankings = object.get("rankings");
                    JsonElement players = object.get("players");
                    if (rankings == null || !rankings.isJsonArray() || rankings.getAsJsonArray().isEmpty()
                            || players == null || !players.isJsonObject()) {
                        return Optional.<String>empty();
                    }

                    String uuid = rankings.getAsJsonArray().get(0).getAsString();
                    JsonElement player = players.getAsJsonObject().get(uuid);
                    if (player == null || !player.isJsonObject()) {
                        return Optional.<String>empty();
                    }
                    JsonElement name = player.getAsJsonObject().get("name");
                    if (name == null || !name.isJsonPrimitive()) {
                        return Optional.<String>empty();
                    }
                    return Optional.of(name.getAsString());
                }).orElse("Swight"));
    }

    private static CompletableFuture<Optional<UUID>> uuidFuture(String name) {
        String cacheKey = name.toLowerCase(Locale.ROOT);
        return NAME_CACHE.computeIfAbsent(cacheKey, ignored -> resolveUuid(name));
    }

    private static boolean isNameLookupPending(String name) {
        CompletableFuture<PlayerTierProfile> future = NAME_PROFILE_CACHE.get(name.toLowerCase(Locale.ROOT));
        return future != null && !future.isDone();
    }

    private static void uuidAlias(UUID uuid, PlayerTierProfile profile) {
        if (uuid != null && profile.hasAnyData()) {
            CACHE.put(uuid, CompletableFuture.completedFuture(profile));
        }
    }

    private static CompletableFuture<PlayerTierProfile> fetchByName(String name) {
        return uuidFuture(name).thenCompose(uuid -> uuid
                .map(value -> fetch(value).thenApply(profile -> {
                    if (profile.hasAnyData()) {
                        CACHE.put(value, CompletableFuture.completedFuture(profile));
                        requestTabRefresh();
                    }
                    return profile;
                }))
                .orElseGet(() -> CompletableFuture.completedFuture(new PlayerTierProfile())));
    }

    private static Optional<UUID> uuidFromCompact(String compactUuid) {
        if (compactUuid == null || compactUuid.length() != 32) {
            return Optional.empty();
        }

        String dashed = compactUuid.substring(0, 8) + "-"
                + compactUuid.substring(8, 12) + "-"
                + compactUuid.substring(12, 16) + "-"
                + compactUuid.substring(16, 20) + "-"
                + compactUuid.substring(20);
        try {
            return Optional.of(UUID.fromString(dashed));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static CompletableFuture<PlayerTierProfile> fetch(UUID uuid) {
        String compactUuid = uuid.toString().replace("-", "");
        PlayerTierProfile profile = new PlayerTierProfile();

        CompletableFuture<Void> mctiers = fetchJson("https://mctiers.com/api/v2/profile/" + compactUuid)
                .thenAccept(json -> parseTierSource(json, profile, TierSource.MCTIERS));
        CompletableFuture<Void> pvptiers = fetchJson("https://pvptiers.com/api/profile/" + compactUuid)
                .thenAccept(json -> parseTierSource(json, profile, TierSource.PVPTIERS));
        CompletableFuture<Void> subtiers = fetchJson("https://subtiers.net/api/profile/" + compactUuid)
                .thenAccept(json -> parseSubtier(json, profile));

        return CompletableFuture.allOf(mctiers, pvptiers, subtiers).handle((ignored, throwable) -> {
            if (profile.hasAnyData()) {
                requestTabRefresh();
            }
            return profile;
        });
    }

    private static void requestTabRefresh() {
        try {
            Class<?> renderer = Class.forName("tab.bettertab.tabList.TabRenderer", false, TierDataCache.class.getClassLoader());
            Field field = renderer.getDeclaredField("immediatelyUpdate");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Throwable ignored) {
            // BetterTab is optional; vanilla tab rendering updates every frame anyway.
        }
    }

    private static CompletableFuture<Optional<JsonObject>> fetchJson(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .header("Accept", "application/json")
                .header("User-Agent", "TierOverlay/1.0.0")
                .GET()
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                        return Optional.<JsonObject>empty();
                    }
                    JsonElement element = JsonParser.parseString(response.body());
                    if (!element.isJsonObject()) {
                        return Optional.<JsonObject>empty();
                    }
                    return Optional.of(element.getAsJsonObject());
                })
                .exceptionally(ignored -> Optional.empty());
    }

    private static void parseTierSource(Optional<JsonObject> json, PlayerTierProfile profile, TierSource source) {
        json.map(object -> object.get("rankings"))
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .ifPresent(rankings -> {
                    for (GameMode mode : GameMode.values()) {
                        parseEntry(rankings, source, mode).ifPresent(entry -> profile.put(source, mode, entry));
                    }
                });
    }

    private static Optional<TierEntry> parseEntry(JsonObject rankings, TierSource source, GameMode mode) {
        JsonElement element = null;
        for (String key : keysFor(source, mode)) {
            element = rankings.get(key);
            if (element != null) {
                break;
            }
        }
        if (element == null) {
            return Optional.empty();
        }
        return parseTierElement(element, mode);
    }

    private static Optional<TierEntry> parseTierElement(JsonElement element, GameMode mode) {
        if (element.isJsonPrimitive()) {
            return tierLiteral(element.getAsString(), mode);
        }
        if (!element.isJsonObject()) {
            return Optional.empty();
        }

        JsonObject object = element.getAsJsonObject();
        if (!object.has("tier")) {
            return Optional.empty();
        }

        String tier = object.get("tier").getAsString();
        Optional<TierEntry> literal = tierLiteral(tier, mode);
        if (literal.isPresent()) {
            return literal;
        }
        if (!object.has("pos")) {
            return Optional.empty();
        }

        String pos = object.get("pos").getAsString();
        String retired = object.has("retired") && !object.get("retired").isJsonNull() ? object.get("retired").getAsString() : "false";
        String prefix = highTierPosition(pos) ? "HT" : "LT";
        String displayed = ("true".equalsIgnoreCase(retired) ? "R" : "") + prefix + tier;
        return Optional.of(new TierEntry(displayed.toUpperCase(Locale.ROOT), mode));
    }

    private static String[] keysFor(TierSource source, GameMode mode) {
        if (source == TierSource.MCTIERS && mode == GameMode.NETH_POT) {
            return new String[]{"nethop", "neth_op", mode.apiKey};
        }
        if (source == TierSource.MCTIERS && mode == GameMode.CRYSTAL) {
            return new String[]{"vanilla", mode.apiKey};
        }
        return new String[]{mode.apiKey};
    }

    private static Optional<TierEntry> tierLiteral(String tier, GameMode mode) {
        if (tier == null) {
            return Optional.empty();
        }
        String normalized = tier.toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");
        if (normalized.matches("R?(HT|LT)[1-5]")) {
            return Optional.of(new TierEntry(normalized, mode));
        }
        return Optional.empty();
    }

    private static boolean highTierPosition(String pos) {
        String normalized = pos == null ? "" : pos.trim().toUpperCase(Locale.ROOT);
        return "0".equals(normalized) || "HT".equals(normalized) || "HIGH".equals(normalized);
    }

    private static void parseSubtier(Optional<JsonObject> json, PlayerTierProfile profile) {
        json.ifPresent(object -> {
            for (String key : new String[]{"subtier", "rank", "tier"}) {
                JsonElement element = object.get(key);
                if (element != null && !element.isJsonNull() && element.isJsonPrimitive()) {
                    profile.subtier(element.getAsString().toUpperCase(Locale.ROOT));
                    return;
                }
            }

            JsonElement rankings = object.get("rankings");
            if (rankings != null && rankings.isJsonObject() && !rankings.getAsJsonObject().isEmpty()) {
                bestSubtierRanking(rankings.getAsJsonObject()).ifPresent(entry -> profile.subtier(entry.tier()));
                return;
            }

            PlayerTierProfile subtierProfile = new PlayerTierProfile();
            parseTierSource(Optional.of(object), subtierProfile, TierSource.MCTIERS);
            subtierProfile.best(TierSource.MCTIERS).ifPresent(entry -> profile.subtier(entry.tier()));
        });
    }

    private static Optional<TierEntry> bestSubtierRanking(JsonObject rankings) {
        return rankings.entrySet().stream()
                .map(entry -> parseTierElement(entry.getValue(), GameMode.SWORD))
                .flatMap(Optional::stream)
                .max((left, right) -> Integer.compare(left.score(), right.score()));
    }

    private static PlayerTierProfile demoProfile(String name) {
        PlayerTierProfile profile = new PlayerTierProfile();
        int shift = Math.abs(name.hashCode()) % 4;
        profile.put(TierSource.MCTIERS, GameMode.SWORD, new TierEntry(shift % 2 == 0 ? "LT4" : "LT3", GameMode.SWORD));
        profile.put(TierSource.MCTIERS, GameMode.AXE, new TierEntry("HT4", GameMode.AXE));
        profile.put(TierSource.PVPTIERS, GameMode.SWORD, new TierEntry(shift % 3 == 0 ? "HT4" : "LT3", GameMode.SWORD));
        profile.put(TierSource.PVPTIERS, GameMode.CRYSTAL, new TierEntry("HT3", GameMode.CRYSTAL));
        if (shift != 0) {
            profile.subtier("LT3");
        }
        return profile;
    }
}
