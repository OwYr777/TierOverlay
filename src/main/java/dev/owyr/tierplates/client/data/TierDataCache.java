package dev.owyr.tierplates.client.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class TierDataCache {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final Map<UUID, CompletableFuture<PlayerTierProfile>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Optional<UUID>>> NAME_CACHE = new ConcurrentHashMap<>();
    private static CompletableFuture<String> mctiersTopName;

    private TierDataCache() {
    }

    public static void clear() {
        CACHE.clear();
        NAME_CACHE.clear();
        mctiersTopName = null;
    }

    public static Optional<PlayerTierProfile> get(UUID uuid, String name, boolean demoData) {
        if (demoData) {
            return Optional.of(demoProfile(name));
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

        CompletableFuture<Optional<UUID>> uuidFuture = uuidFuture(name);
        if (!uuidFuture.isDone() || uuidFuture.isCompletedExceptionally()) {
            return Optional.empty();
        }
        return uuidFuture.getNow(Optional.empty()).flatMap(uuid -> get(uuid, name, false));
    }

    public static Optional<PlayerTierProfile> getBestEffort(UUID uuid, String name, boolean demoData) {
        Optional<PlayerTierProfile> byUuid = get(uuid, name, demoData);
        if (byUuid.map(PlayerTierProfile::hasAnyData).orElse(false)) {
            return byUuid;
        }

        Optional<PlayerTierProfile> byName = getByName(name, demoData);
        if (byName.map(PlayerTierProfile::hasAnyData).orElse(false)) {
            return byName;
        }
        return byUuid.isPresent() ? byUuid : byName;
    }

    public static Optional<UUID> uuidForName(String name) {
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
                }).orElse("ItzRealMe"));
    }

    private static CompletableFuture<Optional<UUID>> uuidFuture(String name) {
        String cacheKey = name.toLowerCase(Locale.ROOT);
        return NAME_CACHE.computeIfAbsent(cacheKey, ignored -> resolveUuid(name));
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

        return CompletableFuture.allOf(mctiers, pvptiers, subtiers).handle((ignored, throwable) -> profile);
    }

    private static CompletableFuture<Optional<JsonObject>> fetchJson(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .header("Accept", "application/json")
                .header("User-Agent", "TierPlates/0.1")
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
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }

        JsonObject object = element.getAsJsonObject();
        if (!object.has("tier") || !object.has("pos")) {
            return Optional.empty();
        }

        String tier = object.get("tier").getAsString();
        String pos = object.get("pos").getAsString();
        String retired = object.has("retired") && !object.get("retired").isJsonNull() ? object.get("retired").getAsString() : "false";
        String prefix = "0".equals(pos) ? "HT" : "LT";
        String displayed = ("true".equalsIgnoreCase(retired) ? "R" : "") + prefix + tier;
        return Optional.of(new TierEntry(displayed.toUpperCase(Locale.ROOT), mode));
    }

    private static String[] keysFor(TierSource source, GameMode mode) {
        if (source == TierSource.MCTIERS && mode == GameMode.NETH_POT) {
            return new String[]{"neth_op", mode.apiKey};
        }
        if (source == TierSource.MCTIERS && mode == GameMode.CRYSTAL) {
            return new String[]{"vanilla", mode.apiKey};
        }
        return new String[]{mode.apiKey};
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

            PlayerTierProfile subtierProfile = new PlayerTierProfile();
            parseTierSource(Optional.of(object), subtierProfile, TierSource.MCTIERS);
            subtierProfile.best(TierSource.MCTIERS).ifPresent(entry -> profile.subtier(entry.tier()));
        });
    }

    private static PlayerTierProfile demoProfile(String name) {
        PlayerTierProfile profile = new PlayerTierProfile();
        int shift = Math.abs(name.hashCode()) % 4;
        profile.put(TierSource.MCTIERS, GameMode.SWORD, new TierEntry(shift % 2 == 0 ? "LT4" : "LT3", GameMode.SWORD));
        profile.put(TierSource.MCTIERS, GameMode.AXE, new TierEntry("HT4", GameMode.AXE));
        profile.put(TierSource.PVPTIERS, GameMode.SWORD, new TierEntry(shift % 3 == 0 ? "HT4" : "LT3", GameMode.SWORD));
        profile.put(TierSource.PVPTIERS, GameMode.CRYSTAL, new TierEntry("HT3", GameMode.CRYSTAL));
        if (shift != 0) {
            profile.subtier("S");
        }
        return profile;
    }
}
