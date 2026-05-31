package dev.owyr.tierplates.client.config;

public class TierPlatesConfig {
    public boolean enabled = true;
    public Side mctiersSide = Side.LEFT;
    public Side pvptiersSide = Side.RIGHT;
    public DisplayMode displayMode = DisplayMode.SWORD;
    public boolean showIcons = true;
    public IconSize iconSize = IconSize.MEDIUM;
    public boolean showNametags = true;
    public boolean showTab = true;
    public boolean showChat = true;
    public boolean forceNameplates = false;
    public NameplateMode nameplateMode = NameplateMode.OWN_F5;
    public boolean showNameInNametag = true;
    public boolean drawBackground = true;
    public boolean useDemoData = false;
    public boolean previewOwnPlayer = false;
    public String previewPlayerName = "ItzRealMe";
    public TierColors tierColors = new TierColors();

    public enum Side {
        LEFT,
        RIGHT,
        OFF;

        public Side next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public enum IconSize {
        SMALL("gamemodes_small"),
        MEDIUM("gamemodes"),
        LARGE("gamemodes_large");

        public final String fontId;

        IconSize(String fontId) {
            this.fontId = fontId;
        }

        public IconSize next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public enum NameplateMode {
        VANILLA_ONLY,
        OWN_F5,
        FORCE_ALL;

        public NameplateMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public enum DisplayMode {
        SWORD,
        AXE,
        CRYSTAL,
        POT,
        UHC,
        SMP,
        NETH_POT,
        MACE,
        BEST;

        public DisplayMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public static class TierColors {
        public int ht1 = 0xE1BB4A;
        public int lt1 = 0xD0B45E;
        public int ht2 = 0xC7D3E6;
        public int lt2 = 0xA1A7B1;
        public int ht3 = 0xECA461;
        public int lt3 = 0xBC7F48;
        public int ht4 = 0x7F7598;
        public int lt4 = 0x635C78;
        public int ht5 = 0x8D83A6;
        public int lt5 = 0x635C78;

        public int get(String tier) {
            return switch (tier.replace("R", "")) {
                case "HT1" -> ht1;
                case "LT1" -> lt1;
                case "HT2" -> ht2;
                case "LT2" -> lt2;
                case "HT3" -> ht3;
                case "LT3" -> lt3;
                case "HT4" -> ht4;
                case "LT4" -> lt4;
                case "HT5" -> ht5;
                case "LT5" -> lt5;
                default -> 0xFFFFFF;
            };
        }

        public void set(String tier, int color) {
            switch (tier.replace("R", "")) {
                case "HT1" -> ht1 = color;
                case "LT1" -> lt1 = color;
                case "HT2" -> ht2 = color;
                case "LT2" -> lt2 = color;
                case "HT3" -> ht3 = color;
                case "LT3" -> lt3 = color;
                case "HT4" -> ht4 = color;
                case "LT4" -> lt4 = color;
                case "HT5" -> ht5 = color;
                case "LT5" -> lt5 = color;
                default -> {
                }
            }
        }
    }
}
