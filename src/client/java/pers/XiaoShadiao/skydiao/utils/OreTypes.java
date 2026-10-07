package pers.XiaoShadiao.skydiao.utils;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public enum OreTypes {

    // ======================= 矿石 (Ores) =======================
    COAL_ORE("Coal Ore", 30, 1800, Blocks.COAL_ORE),
    IRON_ORE("Iron Ore", 30, 1800, Blocks.IRON_ORE),
    GOLD_ORE("Gold Ore", 30, 1800, Blocks.GOLD_ORE),
    LAPIS_LAZULI_ORE("Lapis Lazuli Ore", 30, 1800, Blocks.LAPIS_ORE),
    REDSTONE_ORE("Redstone Ore", 30, 1800, Blocks.REDSTONE_ORE),
    EMERALD_ORE("Emerald Ore", 30, 1800, Blocks.EMERALD_ORE),
    DIAMOND_ORE("Diamond Ore", 30, 1800, Blocks.DIAMOND_ORE),
    NETHER_QUARTZ_ORE("Nether Quartz Ore", 30, 1800, Blocks.NETHER_QUARTZ_ORE),

    SULPHUR_ORE("Sulphur Ore", 500, 30000, Blocks.SPONGE),

    PURE_COAL("Pure Coal", 600, 36000, Blocks.COAL_BLOCK),
    PURE_IRON("Pure Iron", 600, 36000, Blocks.IRON_BLOCK),
    PURE_GOLD("Pure Gold", 600, 36000, Blocks.GOLD_BLOCK),
    PURE_LAPIS_LAZULI("Pure Lapis Lazuli", 600, 36000, Blocks.LAPIS_BLOCK),
    PURE_REDSTONE("Pure Redstone", 600, 36000, Blocks.REDSTONE_BLOCK),
    PURE_EMERALD("Pure Emerald", 600, 36000, Blocks.EMERALD_BLOCK),
    PURE_DIAMOND("Pure Diamond", 600, 36000, Blocks.DIAMOND_BLOCK),
    PURE_NETHER_QUARTZ("Pure Nether Quartz", 600, 36000, Blocks.QUARTZ_BLOCK),

    // ==================== Mithril / Titanium ====================
    MITHRIL_CYAN_TERRACOTTA("Mithril (Cyan Terracotta)", 500, 30001, Blocks.CYAN_TERRACOTTA),
    MITHRIL_GRAY_WOOL("Mithril (Gray Wool)", 500, 30001, Blocks.GRAY_WOOL),
    MITHRIL_PRISMARINE_1("Mithril (Prismarine 1)", 800, 48001, Blocks.PRISMARINE_BRICKS),
    MITHRIL_PRISMARINE_2("Mithril (Prismarine 2)", 800, 48001, Blocks.PRISMARINE),
    MITHRIL_PRISMARINE_3("Mithril (Prismarine 3)", 800, 48001, Blocks.DARK_PRISMARINE),
    MITHRIL_LIGHT_BLUE_WOOL("Mithril (Light Blue Wool)", 1500, 90001, Blocks.LIGHT_BLUE_WOOL),

    TITANIUM_POLISHED_DIORITE("Titanium (Polished Diorite)", 2000, 120001, Blocks.POLISHED_DIORITE),

    // ========================== Umber ==========================
    UMBER_SMOOTH_RED_SANDSTONE("Umber (Smooth Red Sandstone)", 5600, 336001, Blocks.SMOOTH_RED_SANDSTONE),
    UMBER_TERRACOTTA("Umber (Terracotta)", 5600, 336001, Blocks.TERRACOTTA),
    UMBER_BROWN_TERRACOTTA("Umber (Brown Terracotta)", 5600, 336001, Blocks.BROWN_TERRACOTTA),

    // ========================= Tungsten =========================
    TUNGSTEN_CLAY("Tungsten (Clay)", 5600, 336001, Blocks.CLAY),
    TUNGSTEN_COBBLESTONE("Tungsten (Cobblestone)", 5600, 336001, Blocks.INFESTED_COBBLESTONE),
    TUNGSTEN_COBBLESTONE_SLAB("Tungsten (Cobblestone SLAB)", 5600, 336001, Blocks.COBBLESTONE_SLAB),

    // ========================= Glacite =========================
    GLACITE_PACKED_ICE("Glacite (Packed Ice)", 6000, 360001, Blocks.PACKED_ICE),

    // ==================== 宝石 (Gemstones) ====================
    RUBY("Ruby", 2300, 138001, Blocks.RED_STAINED_GLASS),
    RUBY_PANE("Ruby", 2300, 138001, Blocks.RED_STAINED_GLASS_PANE),
    AMBER("Amber", 3000, 180001, Blocks.ORANGE_STAINED_GLASS),
    AMBER_PANE("Amber", 3000, 180001, Blocks.ORANGE_STAINED_GLASS_PANE),
    AMETHYST("Amethyst", 3000, 180001, Blocks.PURPLE_STAINED_GLASS),
    AMETHYST_PANE("Amethyst", 3000, 180001, Blocks.PURPLE_STAINED_GLASS_PANE),
    JADE("Jade", 3000, 180001, Blocks.LIME_STAINED_GLASS),
    JADE_PANE("Jade", 3000, 180001, Blocks.LIME_STAINED_GLASS_PANE),
    OPAL("Opal", 3000, 180001, Blocks.WHITE_STAINED_GLASS),
    OPAL_PANE("Opal", 3000, 180001, Blocks.WHITE_STAINED_GLASS_PANE),
    SAPPHIRE("Sapphire", 3000, 180001, Blocks.LIGHT_BLUE_STAINED_GLASS),
    SAPPHIRE_PANE("Sapphire", 3000, 180001, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE),
    TOPAZ("Topaz", 3800, 228001, Blocks.YELLOW_STAINED_GLASS),
    TOPAZ_PANE("Topaz", 3800, 228001, Blocks.YELLOW_STAINED_GLASS_PANE),
    JASPER("Jasper", 4800, 288001, Blocks.PINK_STAINED_GLASS),
    JASPER_PANE("Jasper", 4800, 288001, Blocks.PINK_STAINED_GLASS_PANE),
    AQUAMARINE("Aquamarine", 5200, 312001, Blocks.BLUE_STAINED_GLASS),
    AQUAMARINE_PANE("Aquamarine", 5200, 312001, Blocks.BLUE_STAINED_GLASS_PANE),
    CITRINE("Citrine", 5200, 312001, Blocks.BROWN_STAINED_GLASS),
    CITRINE_PANE("Citrine", 5200, 312001, Blocks.BROWN_STAINED_GLASS_PANE),
    ONYX("Onyx", 5200, 312001, Blocks.BLACK_STAINED_GLASS),
    ONYX_PANE("Onyx", 5200, 312001, Blocks.BLACK_STAINED_GLASS_PANE),
    PERIDOT("Peridot", 5200, 312001, Blocks.GREEN_STAINED_GLASS),
    PERIDOT_PANE("Peridot", 5200, 312001, Blocks.GREEN_STAINED_GLASS_PANE);

    private final String name;
    private final double blockStrength;         // 破坏强度
    private final double minMiningSpeed;        // 瞬挖所需最低挖掘速度
    private final Block block;

    OreTypes(String name, double blockStrength, double minMiningSpeed, Block block) {
        this.block = block;
        this.name = name;
        this.blockStrength = blockStrength;
        this.minMiningSpeed = minMiningSpeed;
    }

    public String getName() {
        return name;
    }

    public double getBlockStrength() {
        return blockStrength;
    }

    public double getMinMiningSpeed() {
        return minMiningSpeed;
    }

    public static OreTypes byBlock(Block block) {
        for (OreTypes ore : values()) {
            if (ore.block == block) {
                return ore;
            }
        }
        return null;
    }

    public int getTotalBreakTick(double miningSpeed) {
        return (int) (blockStrength * 30 / miningSpeed);
    }
}