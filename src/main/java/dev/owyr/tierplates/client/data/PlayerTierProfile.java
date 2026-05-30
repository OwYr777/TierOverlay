package dev.owyr.tierplates.client.data;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class PlayerTierProfile {
    private final Map<TierSource, Map<GameMode, TierEntry>> entries = new EnumMap<>(TierSource.class);
    private String subtier;

    public void put(TierSource source, GameMode mode, TierEntry entry) {
        entries.computeIfAbsent(source, ignored -> new EnumMap<>(GameMode.class)).put(mode, entry);
    }

    public Optional<TierEntry> get(TierSource source, GameMode mode) {
        return Optional.ofNullable(entries.get(source)).map(map -> map.get(mode));
    }

    public Optional<TierEntry> best(TierSource source) {
        return Optional.ofNullable(entries.get(source)).stream()
                .flatMap(map -> map.values().stream())
                .max((left, right) -> Integer.compare(left.score(), right.score()));
    }

    public boolean hasEntries(TierSource source) {
        return Optional.ofNullable(entries.get(source)).map(map -> !map.isEmpty()).orElse(false);
    }

    public boolean hasAnyData() {
        return entries.values().stream().anyMatch(map -> !map.isEmpty()) || subtier().isPresent();
    }

    public Optional<String> subtier() {
        return Optional.ofNullable(subtier).filter(value -> !value.isBlank());
    }

    public void subtier(String subtier) {
        this.subtier = subtier;
    }
}
