package pers.XiaoShadiao.skydiao.utils.itemprice.itemvalue;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 物品价值计算用到的静态表，数值与 networth-calculator 1.0.5 的
 * {@code ModifierValues} / {@code ItemConstants} 保持一致。
 *
 * <p>权重是"附加物品在成交价里的折价系数"：例如汉堡书只按 60% 计价。</p>
 */
public final class ItemValueConstants {

    private static final double DEFAULT_WEIGHT = 1.0;

    private static final Map<String, Double> WEIGHTS = Map.ofEntries(
            Map.entry("enrichment", 0.5),
            Map.entry("farming4Dummies", 0.5),
            Map.entry("powerScroll", 0.5),
            Map.entry("woodSingularity", 0.5),
            Map.entry("artOfWar", 0.6),
            Map.entry("fumingPotatoBook", 0.6),
            Map.entry("gemstoneSlots", 0.6),
            Map.entry("runes", 0.6),
            Map.entry("skins", 0.67),
            Map.entry("transmissionTuner", 0.7),
            Map.entry("pocketSackInASack", 0.7),
            Map.entry("essence", 0.75),
            Map.entry("goldenBounty", 0.75),
            Map.entry("silex", 0.75),
            Map.entry("artOfPeace", 0.8),
            Map.entry("divanPowderCoating", 0.8),
            Map.entry("jalapenoBook", 0.8),
            Map.entry("manaDisintegrator", 0.8),
            Map.entry("recombobulator", 0.8),
            Map.entry("thunderInABottle", 0.8),
            Map.entry("enchantments", 0.85),
            Map.entry("shensAuctionPrice", 0.85),
            Map.entry("dye", 0.9),
            Map.entry("gemstoneChambers", 0.9),
            Map.entry("petCandy", 0.65),
            Map.entry("soulboundPetSkins", 0.8)
    );

    /** 单种附魔的权重覆盖，未列出的用 {@code enchantments} 的默认权重。 */
    private static final Map<String, Double> ENCHANTMENT_WEIGHTS = Map.of(
            "COUNTER_STRIKE", 0.2,
            "BIG_BRAIN", 0.35,
            "ULTIMATE_INFERNO", 0.35,
            "OVERLOAD", 0.35,
            "ULTIMATE_SOUL_EATER", 0.35,
            "ULTIMATE_FATAL_TEMPO", 0.65
    );

    /** 某些物品天生无法附上这些附魔，即使 NBT 里有也不计价。 */
    private static final Map<String, Set<String>> BLOCKED_ENCHANTMENTS = Map.of(
            "BONE_BOOMERANG", Set.of("OVERLOAD", "POWER", "ULTIMATE_SOUL_EATER"),
            "DEATH_BOW", Set.of("OVERLOAD", "POWER", "ULTIMATE_SOUL_EATER"),
            "GARDENING_AXE", Set.of("REPLENISH"),
            "GARDENING_HOE", Set.of("REPLENISH"),
            "ADVANCED_GARDENING_AXE", Set.of("REPLENISH"),
            "ADVANCED_GARDENING_HOE", Set.of("REPLENISH")
    );

    /** 该等级视为免费附魔，不参与计价。 */
    private static final Map<String, Integer> IGNORED_ENCHANTMENTS = Map.of("SCAVENGER", 5);

    /** 等级可无限叠加的附魔，按 1 级书的价格计。 */
    private static final Set<String> STACKING_ENCHANTMENTS =
            Set.of("EXPERTISE", "COMPACT", "CULTIVATING", "CHAMPION", "HECATOMB", "TOXOPHILITE");

    private static final Set<String> IGNORE_SILEX = Set.of("PROMISING_SPADE", "STONK_PICKAXE");

    /** 部分装备的属性价格应参考更便宜的底板物品。 */
    private static final Map<String, String> ATTRIBUTE_BASE_COSTS = Map.of(
            "GLOWSTONE_GAUNTLET", "GLOWSTONE_GAUNTLET",
            "VANQUISHED_GLOWSTONE_GAUNTLET", "GLOWSTONE_GAUNTLET",
            "BLAZE_BELT", "BLAZE_BELT",
            "VANQUISHED_BLAZE_BELT", "BLAZE_BELT",
            "MAGMA_NECKLACE", "MAGMA_NECKLACE",
            "VANQUISHED_MAGMA_NECKLACE", "MAGMA_NECKLACE",
            "MAGMA_ROD", "MAGMA_ROD",
            "INFERNO_ROD", "MAGMA_ROD",
            "HELLFIRE_ROD", "MAGMA_ROD"
    );

    /** 只有这些符文有 Bazaar 报价，其余符文不参与计价。 */
    private static final Set<String> VALID_RUNES = Set.of(
            "MUSIC_RUNE_1", "MUSIC_RUNE_2", "MUSIC_RUNE_3", "MEOW_MUSIC_RUNE_3",
            "ENCHANT_RUNE_1", "ENCHANT_RUNE_2", "ENCHANT_RUNE_3",
            "GRAND_SEARING_RUNE_3", "SPELLBOUND_RUNE_3", "GRAND_FREEZING_RUNE_3",
            "PRIMAL_FEAR_RUNE_3", "GOLDEN_CARPET_RUNE_3", "ICE_SKATES_RUNE_3",
            "BARK_TUNES_RUNE_3", "SMITTEN_RUNE_3", "RAINY_DAY_RUNE_3"
    );

    /** 重铸名 -> 重铸石 id。 */
    private static final Map<String, String> REFORGES = Map.ofEntries(
            Map.entry("stiff", "HARDENED_WOOD"),
            Map.entry("salty", "SALT_CUBE"),
            Map.entry("aote_stone", "AOTE_STONE"),
            Map.entry("blazing", "BLAZEN_SPHERE"),
            Map.entry("waxed", "BLAZE_WAX"),
            Map.entry("rooted", "BURROWING_SPORES"),
            Map.entry("candied", "CANDY_CORN"),
            Map.entry("perfect", "DIAMOND_ATOM"),
            Map.entry("fleet", "DIAMONITE"),
            Map.entry("fabled", "DRAGON_CLAW"),
            Map.entry("spiked", "DRAGON_SCALE"),
            Map.entry("royal", "DWARVEN_TREASURE"),
            Map.entry("hyper", "ENDSTONE_GEODE"),
            Map.entry("coldfusion", "ENTROPY_SUPPRESSOR"),
            Map.entry("blooming", "FLOWERING_BOUQUET"),
            Map.entry("fanged", "FULL_JAW_FANGED_KIT"),
            Map.entry("jaded", "JADERALD"),
            Map.entry("jerry", "JERRY_STONE"),
            Map.entry("magnetic", "LAPIS_CRYSTAL"),
            Map.entry("earthy", "LARGE_WALNUT"),
            Map.entry("fortified", "METEOR_SHARD"),
            Map.entry("gilded", "MIDAS_JEWEL"),
            Map.entry("cubic", "MOLTEN_CUBE"),
            Map.entry("necrotic", "NECROMANCER_BROOCH"),
            Map.entry("fruitful", "ONYX"),
            Map.entry("precise", "OPTICAL_LENS"),
            Map.entry("mossy", "OVERGROWN_GRASS"),
            Map.entry("pitchin", "PITCHIN_KOI"),
            Map.entry("undead", "PREMIUM_FLESH"),
            Map.entry("blood_soaked", "PRESUMED_GALLON_OF_RED_PAINT"),
            Map.entry("mithraic", "PURE_MITHRIL"),
            Map.entry("reinforced", "RARE_DIAMOND"),
            Map.entry("ridiculous", "RED_NOSE"),
            Map.entry("loving", "RED_SCARF"),
            Map.entry("auspicious", "ROCK_GEMSTONE"),
            Map.entry("treacherous", "RUSTY_ANCHOR"),
            Map.entry("headstrong", "SALMON_OPAL"),
            Map.entry("strengthened", "SEARING_STONE"),
            Map.entry("glistening", "SHINY_PRISM"),
            Map.entry("bustling", "SKYMART_BROCHURE"),
            Map.entry("spiritual", "SPIRIT_DECOY"),
            Map.entry("suspicious", "SUSPICIOUS_VIAL"),
            Map.entry("snowy", "TERRY_SNOWGLOBE"),
            Map.entry("dimensional", "TITANIUM_TESSERACT"),
            Map.entry("ambered", "AMBER_MATERIAL"),
            Map.entry("beady", "BEADY_EYES"),
            Map.entry("blessed", "BLESSED_FRUIT"),
            Map.entry("bulky", "BULKY_STONE"),
            Map.entry("buzzing", "CLIPPED_WINGS"),
            Map.entry("submerged", "DEEP_SEA_ORB"),
            Map.entry("renowned", "DRAGON_HORN"),
            Map.entry("festive", "FROZEN_BAUBLE"),
            Map.entry("giant", "GIANT_TOOTH"),
            Map.entry("lustrous", "GLEAMING_CRYSTAL"),
            Map.entry("bountiful", "GOLDEN_BALL"),
            Map.entry("chomp", "KUUDRA_MANDIBLE"),
            Map.entry("lucky", "LUCKY_DICE"),
            Map.entry("stellar", "PETRIFIED_STARFALL"),
            Map.entry("scraped", "POCKET_ICEBERG"),
            Map.entry("ancient", "PRECURSOR_GEAR"),
            Map.entry("refined", "REFINED_AMBER"),
            Map.entry("empowered", "SADAN_BROOCH"),
            Map.entry("withered", "WITHER_BLOOD"),
            Map.entry("glacial", "FRIGID_HUSK"),
            Map.entry("heated", "HOT_STUFF"),
            Map.entry("dirty", "DIRT_BOTTLE"),
            Map.entry("moil", "MOIL_LOG"),
            Map.entry("toil", "TOIL_LOG"),
            Map.entry("greater_spook", "BOO_STONE")
    );

    /** 这些槽位是"组合槽"，实际宝石类型要从 {@code <槽位>_gem} 读取。 */
    private static final Set<String> GEMSTONE_GROUP_SLOT_TYPES =
            Set.of("COMBAT", "OFFENSIVE", "DEFENSIVE", "MINING", "UNIVERSAL", "CHISEL");

    private static final Set<String> DIVAN_GEAR =
            Set.of("DIVAN_HELMET", "DIVAN_CHESTPLATE", "DIVAN_LEGGINGS", "DIVAN_BOOTS", "DIVAN_PENDANT");

    private static final List<String> MASTER_STARS = List.of(
            "FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR",
            "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR");

    private static final int PICKONIMBUS_DURABILITY = 2000;

    private ItemValueConstants() {
    }

    public static double weight(String key) {
        return WEIGHTS.getOrDefault(key, DEFAULT_WEIGHT);
    }

    public static double enchantmentWeight(String enchantment) {
        return ENCHANTMENT_WEIGHTS.getOrDefault(enchantment, weight("enchantments"));
    }

    public static boolean isBlockedEnchantment(String itemId, String enchantment) {
        Set<String> blocked = BLOCKED_ENCHANTMENTS.get(itemId);
        return blocked != null && blocked.contains(enchantment);
    }

    public static boolean isIgnoredEnchantment(String enchantment, int level) {
        Integer ignoredLevel = IGNORED_ENCHANTMENTS.get(enchantment);
        return ignoredLevel != null && ignoredLevel == level;
    }

    public static boolean isStackingEnchantment(String enchantment) {
        return STACKING_ENCHANTMENTS.contains(enchantment);
    }

    public static boolean ignoreSilex(String itemId) {
        return IGNORE_SILEX.contains(itemId);
    }

    public static String attributeBaseCostId(String itemId) {
        return ATTRIBUTE_BASE_COSTS.get(itemId);
    }

    public static boolean isValidRune(String runeItemId) {
        return VALID_RUNES.contains(runeItemId);
    }

    public static String reforgeItemId(String reforge) {
        return REFORGES.get(reforge);
    }

    public static boolean isGemstoneGroupSlot(String slotType) {
        return GEMSTONE_GROUP_SLOT_TYPES.contains(slotType);
    }

    public static boolean isDivanGear(String itemId) {
        return DIVAN_GEAR.contains(itemId);
    }

    public static List<String> masterStars() {
        return MASTER_STARS;
    }

    public static int pickonimbusDurability() {
        return PICKONIMBUS_DURABILITY;
    }
}
