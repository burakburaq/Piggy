package dev.piggy.n7q4m2.quality.metrics;

/** Readable biome-themed colors, including every vanilla biome in 26.3. */
public final class WorldColors {
    private WorldColors() {}

    public static int dimension(String namespace, String path) {
        if (!namespace.equals("minecraft")) return 0xFFFFFF;
        return switch (path) {
            case "overworld" -> 0x9BEDA6;
            case "the_nether" -> 0xFF9292;
            case "the_end" -> 0xD7A5FF;
            default -> 0xFFFFFF;
        };
    }

    public static int biome(String namespace, String path) {
        if (!namespace.equals("minecraft")) return 0xFFFFFF;
        return switch (path) {
            case "plains" -> 0xA8DC75;
            case "sunflower_plains" -> 0xF4DF70;
            case "snowy_plains", "snowy_beach" -> 0xDEF5FF;
            case "ice_spikes", "frozen_peaks", "frozen_river" -> 0xA3E6FF;
            case "desert", "beach" -> 0xF2D58C;
            case "swamp" -> 0xA6BA77;
            case "mangrove_swamp" -> 0xC5A177;
            case "forest" -> 0x80C878;
            case "flower_forest" -> 0xEDA8DA;
            case "birch_forest", "old_growth_birch_forest" -> 0xC4DB9A;
            case "dappled_forest" -> 0xD5BA82;
            case "dark_forest" -> 0x7DAA86;
            case "pale_garden" -> 0xC8CDCF;
            case "old_growth_pine_taiga", "old_growth_spruce_taiga", "taiga" -> 0x83B8A3;
            case "snowy_taiga", "grove" -> 0xB3DBD8;
            case "savanna", "savanna_plateau", "windswept_savanna" -> 0xD4CA79;
            case "windswept_hills", "windswept_gravelly_hills", "stony_peaks", "stony_shore" -> 0xB6BBC8;
            case "windswept_forest" -> 0x98B69A;
            case "jungle", "sparse_jungle" -> 0x73CF78;
            case "bamboo_jungle" -> 0xB2DB70;
            case "badlands", "eroded_badlands", "wooded_badlands" -> 0xEAA078;
            case "meadow" -> 0xBAE59A;
            case "cherry_grove" -> 0xFFB6D1;
            case "snowy_slopes", "jagged_peaks" -> 0xC6DEEF;
            case "river", "ocean" -> 0x83B9F5;
            case "warm_ocean" -> 0x6DE1D5;
            case "lukewarm_ocean", "deep_lukewarm_ocean" -> 0x7ED4DF;
            case "deep_ocean" -> 0x8A9EE5;
            case "cold_ocean", "deep_cold_ocean" -> 0x91B9DD;
            case "frozen_ocean", "deep_frozen_ocean" -> 0xB2E2F4;
            case "mushroom_fields" -> 0xDA9BCB;
            case "dripstone_caves" -> 0xCBAF94;
            case "lush_caves" -> 0xACE080;
            case "deep_dark" -> 0x72B4BF;
            case "sulfur_caves" -> 0xE9DD78;
            case "nether_wastes" -> 0xF18F84;
            case "warped_forest" -> 0x6DD8CC;
            case "crimson_forest" -> 0xEE7F9B;
            case "soul_sand_valley" -> 0x9DD5E2;
            case "basalt_deltas" -> 0xB7ADBF;
            case "the_end", "end_highlands" -> 0xDAC09C;
            case "end_midlands" -> 0xC6ACDF;
            case "small_end_islands" -> 0xD5B9ED;
            case "end_barrens" -> 0xB8A8D3;
            case "the_void" -> 0xA9A1BD;
            default -> 0xFFFFFF;
        };
    }
}
