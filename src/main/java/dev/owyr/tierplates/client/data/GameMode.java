package dev.owyr.tierplates.client.data;

public enum GameMode {
    SWORD("sword", "\uF005"),
    AXE("axe", "\uF006"),
    CRYSTAL("crystal", "\uF000"),
    POT("pot", "\uF002"),
    UHC("uhc", "\uF001"),
    SMP("smp", "\uF004"),
    NETH_POT("neth_pot", "\uF003"),
    MACE("mace", "\uF007");

    public final String apiKey;
    public final String icon;

    GameMode(String apiKey, String icon) {
        this.apiKey = apiKey;
        this.icon = icon;
    }
}
