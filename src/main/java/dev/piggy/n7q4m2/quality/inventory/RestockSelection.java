package dev.piggy.n7q4m2.quality.inventory;

import java.util.Comparator;
import java.util.List;

/** Selection policy kept separate from Minecraft's mutable inventory. */
public final class RestockSelection {
    private RestockSelection() {}

    public record Candidate(int slot, boolean sameMaterial, int materialStrength, int durability) {}

    public static int choose(List<Candidate> candidates) {
        return candidates.stream().min(Comparator
                .comparingInt((Candidate c) -> c.sameMaterial() ? 0 : 1)
                .thenComparing(Comparator.comparingInt(Candidate::materialStrength).reversed())
                .thenComparingInt(c -> c.slot() >= 9 ? 0 : 1)
                .thenComparing(Comparator.comparingInt(Candidate::durability).reversed())
                .thenComparingInt(Candidate::slot)).map(Candidate::slot).orElse(-1);
    }

    public static int materialStrength(String material) {
        return switch (material) {
            case "netherite" -> 7;
            case "diamond" -> 6;
            case "iron" -> 5;
            case "copper" -> 4;
            case "stone" -> 3;
            case "golden" -> 2;
            case "wooden" -> 1;
            default -> 0;
        };
    }
}
