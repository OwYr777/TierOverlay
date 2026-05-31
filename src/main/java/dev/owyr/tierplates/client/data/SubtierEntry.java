package dev.owyr.tierplates.client.data;

public record SubtierEntry(String tier, SubtierMode mode) {
    public int score() {
        return switch (tier.replace("R", "")) {
            case "HT1" -> 60;
            case "LT1" -> 44;
            case "HT2" -> 28;
            case "LT2" -> 16;
            case "HT3" -> 10;
            case "LT3" -> 6;
            case "HT4" -> 4;
            case "LT4" -> 3;
            case "HT5" -> 2;
            case "LT5" -> 1;
            default -> 0;
        };
    }
}
