package pers.XiaoShadiao.skydiao.utils;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.BooleanSupplier;

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
    MITHRIL_CYAN_TERRACOTTA("Mithril (Cyan Terracotta)", 500, 30001, Blocks.DYED_TERRACOTTA.cyan()),
    MITHRIL_GRAY_WOOL("Mithril (Gray Wool)", 500, 30001, Blocks.WOOL.gray()),
    MITHRIL_PRISMARINE_1("Mithril (Prismarine 1)", 800, 48001, Blocks.PRISMARINE_BRICKS),
    MITHRIL_PRISMARINE_2("Mithril (Prismarine 2)", 800, 48001, Blocks.PRISMARINE),
    MITHRIL_PRISMARINE_3("Mithril (Prismarine 3)", 800, 48001, Blocks.DARK_PRISMARINE),
    MITHRIL_LIGHT_BLUE_WOOL("Mithril (Light Blue Wool)", 1500, 90001, Blocks.WOOL.lightBlue()),

    TITANIUM_POLISHED_DIORITE("Titanium (Polished Diorite)", 2000, 120001, Blocks.POLISHED_DIORITE),

    // ========================== Umber ==========================
    UMBER_SMOOTH_RED_SANDSTONE("Umber (Smooth Red Sandstone)", 5600, 336001, Blocks.SMOOTH_RED_SANDSTONE),
    UMBER_TERRACOTTA("Umber (Terracotta)", 5600, 336001, Blocks.TERRACOTTA),
    UMBER_BROWN_TERRACOTTA("Umber (Brown Terracotta)", 5600, 336001, Blocks.DYED_TERRACOTTA.brown()),

    // ========================= Tungsten =========================
    TUNGSTEN_CLAY("Tungsten (Clay)", 5600, 336001, Blocks.CLAY),
    TUNGSTEN_COBBLESTONE("Tungsten (Cobblestone)", 5600, 336001, Blocks.INFESTED_COBBLESTONE),
    TUNGSTEN_COBBLESTONE_SLAB("Tungsten (Cobblestone SLAB)", 5600, 336001, Blocks.COBBLESTONE_SLAB),

    TUNGSTEN_COBBLESTONE2("Tungsten (Cobblestone2)", 5600, 336001, Blocks.COBBLESTONE, () -> "mineshaft".equals(StatusManager.get().getMode())),
    TUNGSTEN_COBBLESTONE_STAIRS("Tungsten (Cobblestone Stairs)", 5600, 336001, Blocks.COBBLESTONE_STAIRS, () -> "mineshaft".equals(StatusManager.get().getMode())),

    // ========================= Glacite =========================
    GLACITE_PACKED_ICE("Glacite (Packed Ice)", 6000, 360001, Blocks.PACKED_ICE),

    // ==================== 宝石 (Gemstones) ====================
    RUBY("Ruby", 2300, 138001, Blocks.STAINED_GLASS.red()),
    RUBY_PANE("Ruby", 2300, 138001, Blocks.STAINED_GLASS_PANE.red()),
    AMBER("Amber", 3000, 180001, Blocks.STAINED_GLASS.orange()),
    AMBER_PANE("Amber", 3000, 180001, Blocks.STAINED_GLASS_PANE.orange()),
    AMETHYST("Amethyst", 3000, 180001, Blocks.STAINED_GLASS.purple()),
    AMETHYST_PANE("Amethyst", 3000, 180001, Blocks.STAINED_GLASS_PANE.purple()),
    JADE("Jade", 3000, 180001, Blocks.STAINED_GLASS.lime()),
    JADE_PANE("Jade", 3000, 180001, Blocks.STAINED_GLASS_PANE.lime()),
    OPAL("Opal", 3000, 180001, Blocks.STAINED_GLASS.white()),
    OPAL_PANE("Opal", 3000, 180001, Blocks.STAINED_GLASS_PANE.white()),
    SAPPHIRE("Sapphire", 3000, 180001, Blocks.STAINED_GLASS.lightBlue()),
    SAPPHIRE_PANE("Sapphire", 3000, 180001, Blocks.STAINED_GLASS_PANE.lightBlue()),
    TOPAZ("Topaz", 3800, 228001, Blocks.STAINED_GLASS.yellow()),
    TOPAZ_PANE("Topaz", 3800, 228001, Blocks.STAINED_GLASS_PANE.yellow()),
    JASPER("Jasper", 4800, 288001, Blocks.STAINED_GLASS.magenta()),
    JASPER_PANE("Jasper", 4800, 288001, Blocks.STAINED_GLASS_PANE.magenta()),
    AQUAMARINE("Aquamarine", 5200, 312001, Blocks.STAINED_GLASS.blue()),
    AQUAMARINE_PANE("Aquamarine", 5200, 312001, Blocks.STAINED_GLASS_PANE.blue()),
    CITRINE("Citrine", 5200, 312001, Blocks.STAINED_GLASS.brown()),
    CITRINE_PANE("Citrine", 5200, 312001, Blocks.STAINED_GLASS_PANE.brown()),
    ONYX("Onyx", 5200, 312001, Blocks.STAINED_GLASS.black()),
    ONYX_PANE("Onyx", 5200, 312001, Blocks.STAINED_GLASS_PANE.black()),
    PERIDOT("Peridot", 5200, 312001, Blocks.STAINED_GLASS.green()),
    PERIDOT_PANE("Peridot", 5200, 312001, Blocks.STAINED_GLASS_PANE.green()),
    ;

    private final String name;
    private final double blockStrength;         // 破坏强度
    private final double minMiningSpeed;        // 瞬挖所需最低挖掘速度
    private final Block block;
    private final BooleanSupplier tester;

    OreTypes(String name, double blockStrength, double minMiningSpeed, Block block) {
        this(name, blockStrength, minMiningSpeed, block, () -> true);
    }

    OreTypes(String name, double blockStrength, double minMiningSpeed, Block block, BooleanSupplier tester) {
        this.block = block;
        this.name = name;
        this.blockStrength = blockStrength;
        this.minMiningSpeed = minMiningSpeed;
        this.tester = tester;
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
            if (ore.block == block && ore.tester.getAsBoolean()) {
                return ore;
            }
        }
        return null;
    }

    public int getTotalBreakTick(double miningSpeed) {
        return (int) (blockStrength * 30 / miningSpeed);
    }
}