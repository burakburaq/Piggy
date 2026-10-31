package dev.piggy.n7q4m2.quality.metrics;

public record FoodPreview(int hunger, float saturation) {
    public record Slice(int left, int right) {}
    public static int filledPixels(float value, int cell) {
        return Math.round(9 * Math.clamp((value - cell * 2) / 2f, 0, 1));
    }
    /** A food cell fills from its right edge; health cells use the opposite direction. */
    public static Slice rightSlice(float before, float after, int cell) {
        int current = filledPixels(before, cell), next = filledPixels(after, cell);
        return new Slice(9 - next, 9 - current);
    }
    /** Vanilla regeneration at rest, including the strict exhaustion > 4 boundary. */
    public float recovery(float exhaustion, float missingHealth, boolean naturalRegeneration, boolean peaceful) {
        if (!naturalRegeneration || !Float.isFinite(missingHealth) || missingHealth <= 0 || hunger < 18) return 0;
        int level = hunger, timer = 0;
        float sat = saturation, spent = Math.max(0, Math.min(40, exhaustion)), healed = 0;
        for (int tick = 0; tick < 10000 && healed < missingHealth && level >= 18; tick++) {
            if (spent > 4) {
                spent -= 4;
                if (sat > 0) sat = Math.max(0, sat - 1);
                else if (!peaceful) level--;
            }
            if (level < 18) break;
            boolean fast = level >= 20 && sat > 0;
            if (++timer >= (fast ? 10 : 80)) {
                float gain = fast ? Math.min(sat, 6) : 6;
                healed += gain / 6;
                spent = Math.min(40, spent + gain);
                timer = 0;
            }
        }
        return Math.min(missingHealth, healed);
    }
    public static FoodPreview afterEating(int hunger, float saturation, int nutrition, float foodSaturation) {
        int filledHunger = Math.max(0, Math.min(20, hunger + nutrition));
        return new FoodPreview(filledHunger, Math.max(0, Math.min(filledHunger, saturation + foodSaturation)));
    }
}
