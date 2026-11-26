package pers.XiaoShadiao.skydiao.utils.neu;

import net.minecraft.resources.Identifier;

/**
 * 把 NEU 里 1.8 时代的 {@code itemid + damage} 映射到当前版本的现代物品 ID。
 *
 * <p>NEU 仓库仍保留大量 1.8 ID（skull、dye、log、wool 等），直接按 ID 取物品会得到
 * AIR，最终显示为屏障。这里只覆盖客户端实际用到的常见旧 ID。</p>
 */
public final class LegacyItemIds {

    private static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue",
            "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue",
            "brown", "green", "red", "black"
    };

    private static final String[] DYES = {
            "ink_sac", "red_dye", "green_dye", "cocoa_beans",
            "lapis_lazuli", "purple_dye", "cyan_dye", "light_gray_dye",
            "gray_dye", "pink_dye", "lime_dye", "yellow_dye",
            "light_blue_dye", "magenta_dye", "orange_dye", "bone_meal"
    };

    private static final String[] WOODS = {
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak"
    };

    private LegacyItemIds() {
    }

    /**
     * @return 现代物品 ID；不需要映射或未知旧 ID 时返回 null
     */
    public static Identifier map(String itemId, int damage) {
        if (itemId == null || !itemId.startsWith("minecraft:")) {
            return null;
        }
        String path = itemId.substring("minecraft:".length());
        int d = Math.max(0, damage);

        return switch (path) {
            case "skull" -> id("player_head");
            case "banner" -> id(COLORS[color(d)] + "_banner");
            case "bed" -> id("red_bed");
            case "boat" -> id("oak_boat");
            case "brick_block" -> id("bricks");
            case "carpet" -> id(COLORS[color(d)] + "_carpet");
            case "cooked_fish" -> id(d == 1 ? "cooked_salmon" : "cooked_cod");
            case "deadbush" -> id("dead_bush");
            case "double_plant" -> id(switch (d & 7) {
                case 0 -> "sunflower";
                case 1 -> "lilac";
                case 2 -> "tall_grass";
                case 3 -> "large_fern";
                case 4 -> "rose_bush";
                case 5 -> "peony";
                default -> "tall_grass";
            });
            case "dye" -> id(DYES[color(d)]);
            case "fence_gate" -> id("oak_fence_gate");
            case "fence" -> id("oak_fence");
            case "firework_charge" -> id("firework_star");
            case "fireworks" -> id("firework_rocket");
            case "fish" -> id(switch (d & 3) {
                case 0 -> "cod";
                case 1 -> "salmon";
                case 2 -> "tropical_fish";
                default -> "pufferfish";
            });
            case "golden_rail" -> id("powered_rail");
            case "grass" -> id("grass_block");
            case "hardened_clay" -> id("terracotta");
            case "leaves" -> id(WOODS[Math.min(3, d & 3)] + "_leaves");
            case "leaves2" -> id((d & 1) == 0 ? "acacia_leaves" : "dark_oak_leaves");
            case "lit_pumpkin" -> id("jack_o_lantern");
            case "log" -> id(WOODS[Math.min(3, d & 3)] + "_log");
            case "log2" -> id((d & 1) == 0 ? "acacia_log" : "dark_oak_log");
            case "melon_block" -> id("melon");
            case "mob_spawner" -> id("spawner");
            case "monster_egg" -> id(switch (d & 7) {
                case 0 -> "infested_stone";
                case 1 -> "infested_cobblestone";
                case 2 -> "infested_stone_bricks";
                case 3 -> "infested_mossy_stone_bricks";
                case 4 -> "infested_cracked_stone_bricks";
                case 5 -> "infested_chiseled_stone_bricks";
                default -> "infested_stone";
            });
            case "netherbrick" -> id("nether_brick");
            case "noteblock" -> id("note_block");
            case "planks" -> id(WOODS[Math.min(5, d & 7)] + "_planks");
            case "quartz_ore" -> id("nether_quartz_ore");
            case "record_11" -> id("music_disc_11");
            case "record_13" -> id("music_disc_13");
            case "record_blocks" -> id("music_disc_blocks");
            case "record_cat" -> id("music_disc_cat");
            case "record_chirp" -> id("music_disc_chirp");
            case "record_far" -> id("music_disc_far");
            case "record_mall" -> id("music_disc_mall");
            case "record_mellohi" -> id("music_disc_mellohi");
            case "record_stal" -> id("music_disc_stal");
            case "record_strad" -> id("music_disc_strad");
            case "record_wait" -> id("music_disc_wait");
            case "record_ward" -> id("music_disc_ward");
            case "red_flower" -> id(switch (d & 15) {
                case 0 -> "poppy";
                case 1 -> "blue_orchid";
                case 2 -> "allium";
                case 3 -> "azure_bluet";
                case 4 -> "red_tulip";
                case 5 -> "orange_tulip";
                case 6 -> "white_tulip";
                case 7 -> "pink_tulip";
                case 8 -> "oxeye_daisy";
                default -> "poppy";
            });
            case "reeds" -> id("sugar_cane");
            case "sapling" -> id(WOODS[Math.min(5, d & 7)] + "_sapling");
            case "sign" -> id("oak_sign");
            case "slime" -> id("slime_block");
            case "snow_layer" -> id("snow");
            case "spawn_egg" -> id(switch (d) {
                case 66 -> "witch_spawn_egg";
                case 92 -> "cow_spawn_egg";
                case 120 -> "villager_spawn_egg";
                default -> "chicken_spawn_egg";
            });
            case "speckled_melon" -> id("glistering_melon_slice");
            case "stained_glass_pane" -> id(COLORS[color(d)] + "_stained_glass_pane");
            case "stained_glass" -> id(COLORS[color(d)] + "_stained_glass");
            case "stained_hardened_clay" -> id(COLORS[color(d)] + "_terracotta");
            case "stone_slab2" -> id("red_sandstone_slab");
            case "stonebrick" -> id(switch (d & 3) {
                case 0 -> "stone_bricks";
                case 1 -> "mossy_stone_bricks";
                case 2 -> "cracked_stone_bricks";
                default -> "chiseled_stone_bricks";
            });
            case "tallgrass" -> id(switch (d & 3) {
                case 0 -> "dead_bush";
                case 1 -> "short_grass";
                case 2 -> "fern";
                default -> "short_grass";
            });
            case "trapdoor" -> id("oak_trapdoor");
            case "waterlily" -> id("lily_pad");
            case "web" -> id("cobweb");
            case "wooden_button" -> id("oak_button");
            case "wooden_door" -> id("oak_door");
            case "wooden_pressure_plate" -> id("oak_pressure_plate");
            case "wooden_slab" -> id(WOODS[Math.min(5, d & 7)] + "_slab");
            case "wool" -> id(COLORS[color(d)] + "_wool");
            case "yellow_flower" -> id("dandelion");
            default -> null;
        };
    }

    private static int color(int damage) {
        return Math.min(15, Math.max(0, damage & 15));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }
}
