package dev.owyr.tierplates.client.data;

import java.util.Arrays;
import java.util.Optional;

public enum SubtierMode {
    OVERALL("overall", "\uF100"),
    BED("bed", "\uF101"),
    BOW("bow", "\uF102"),
    CREEPER("creeper", "\uF103"),
    DEBUFF("debuff", "\uF104"),
    DIA_CRYSTAL("dia_crystal", "\uF105"),
    DIA_SMP("dia_smp", "\uF106"),
    ELYTRA("elytra", "\uF107"),
    MACE("mace", "\uF108"),
    MANHUNT("manhunt", "\uF109"),
    MINECART("minecart", "\uF10A"),
    OG_VANILLA("og_vanilla", "\uF10B"),
    SPEED("speed", "\uF10C"),
    TRIDENT("trident", "\uF10D");

    public final String apiKey;
    public final String icon;

    SubtierMode(String apiKey, String icon) {
        this.apiKey = apiKey;
        this.icon = icon;
    }

    public static Optional<SubtierMode> fromKey(String key) {
        return Arrays.stream(values())
                .filter(mode -> mode.apiKey.equalsIgnoreCase(key))
                .findFirst();
    }
}
