package pers.XiaoShadiao.skydiao.compat.rei;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * NEU mob JSON 里的 {@code entity} 别名到原版 EntityType 的映射。
 *
 * <p>NEU 的别名并不总是注册名（例如 Eisengolem、Dragon、Pigman），
 * 因此先查显式表，再按“去掉下划线后比较”兜底。
 */
final class MobEntityTypes {

    private static final Map<String, EntityType<?>> ALIASES = buildAliases();

    private MobEntityTypes() {
    }

    static EntityType<?> byEntityTypeId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
        } catch (Exception ignored) {
            return null;
        }
    }

    static EntityType<?> byAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            return null;
        }
        EntityType<?> direct = ALIASES.get(alias);
        if (direct != null) {
            return direct;
        }

        String normalized = normalize(alias);
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (key != null && normalize(key.getPath()).equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static Map<String, EntityType<?>> buildAliases() {
        Map<String, EntityType<?>> map = new HashMap<>();

        map.put("Armadillo", EntityTypes.ARMADILLO);
        map.put("ArmorStand", EntityTypes.ARMOR_STAND);
        map.put("Axolotl", EntityTypes.AXOLOTL);
        map.put("Bat", EntityTypes.BAT);
        map.put("Bee", EntityTypes.BEE);
        map.put("Blaze", EntityTypes.BLAZE);
        map.put("Bogged", EntityTypes.BOGGED);
        map.put("Breeze", EntityTypes.BREEZE);
        map.put("CaveSpider", EntityTypes.CAVE_SPIDER);
        map.put("Chicken", EntityTypes.CHICKEN);
        map.put("Cod", EntityTypes.COD);
        map.put("Cow", EntityTypes.COW);
        map.put("Creaking", EntityTypes.CREAKING);
        map.put("Creeper", EntityTypes.CREEPER);
        map.put("Dolphin", EntityTypes.DOLPHIN);
        map.put("Donkey", EntityTypes.DONKEY);
        map.put("Dragon", EntityTypes.ENDER_DRAGON);
        map.put("Drowned", EntityTypes.DROWNED);
        map.put("Eisengolem", EntityTypes.IRON_GOLEM);
        map.put("ElderGuardian", EntityTypes.ELDER_GUARDIAN);
        map.put("Enderman", EntityTypes.ENDERMAN);
        map.put("Endermite", EntityTypes.ENDERMITE);
        map.put("Evoker", EntityTypes.EVOKER);
        map.put("Fox", EntityTypes.FOX);
        map.put("Frog", EntityTypes.FROG);
        map.put("Ghast", EntityTypes.GHAST);
        map.put("Giant", EntityTypes.GIANT);
        map.put("GlowSquid", EntityTypes.GLOW_SQUID);
        map.put("Goat", EntityTypes.GOAT);
        map.put("Guardian", EntityTypes.GUARDIAN);
        map.put("HappyGhast", EntityTypes.HAPPY_GHAST);
        map.put("Horse", EntityTypes.HORSE);
        map.put("Husk", EntityTypes.HUSK);
        map.put("Illusioner", EntityTypes.ILLUSIONER);
        map.put("Llama", EntityTypes.LLAMA);
        map.put("LLama", EntityTypes.LLAMA);
        map.put("MagmaCube", EntityTypes.MAGMA_CUBE);
        map.put("Mooshroom", EntityTypes.MOOSHROOM);
        map.put("Mule", EntityTypes.MULE);
        map.put("Ocelot", EntityTypes.OCELOT);
        map.put("Panda", EntityTypes.PANDA);
        map.put("Parched", EntityTypes.PARCHED);
        map.put("Phantom", EntityTypes.PHANTOM);
        map.put("Pig", EntityTypes.PIG);
        map.put("Piglin", EntityTypes.PIGLIN);
        map.put("PiglinBrute", EntityTypes.PIGLIN_BRUTE);
        map.put("Pigman", EntityTypes.ZOMBIFIED_PIGLIN);
        map.put("Pillager", EntityTypes.PILLAGER);
        map.put("Player", EntityTypes.PLAYER);
        map.put("PolarBear", EntityTypes.POLAR_BEAR);
        map.put("Pufferfish", EntityTypes.PUFFERFISH);
        map.put("Rabbit", EntityTypes.RABBIT);
        map.put("Salmon", EntityTypes.SALMON);
        map.put("Salmom", EntityTypes.SALMON);
        map.put("Sheep", EntityTypes.SHEEP);
        map.put("Shulker", EntityTypes.SHULKER);
        map.put("Silverfish", EntityTypes.SILVERFISH);
        map.put("Skeleton", EntityTypes.SKELETON);
        map.put("Slime", EntityTypes.SLIME);
        map.put("Sniffer", EntityTypes.SNIFFER);
        map.put("Snowman", EntityTypes.SNOW_GOLEM);
        map.put("Spider", EntityTypes.SPIDER);
        map.put("Squid", EntityTypes.SQUID);
        map.put("Stray", EntityTypes.STRAY);
        map.put("Strider", EntityTypes.STRIDER);
        map.put("Tadpole", EntityTypes.TADPOLE);
        map.put("TropicalFish", EntityTypes.TROPICAL_FISH);
        map.put("Turtle", EntityTypes.TURTLE);
        map.put("Vex", EntityTypes.VEX);
        map.put("Villager", EntityTypes.VILLAGER);
        map.put("Vindicator", EntityTypes.VINDICATOR);
        map.put("Warden", EntityTypes.WARDEN);
        map.put("Witch", EntityTypes.WITCH);
        map.put("Wither", EntityTypes.WITHER);
        map.put("WitherSkeleton", EntityTypes.WITHER_SKELETON);
        map.put("Wolf", EntityTypes.WOLF);
        map.put("Zoglin", EntityTypes.ZOGLIN);
        map.put("Zombie", EntityTypes.ZOMBIE);
        map.put("ZombieVillager", EntityTypes.ZOMBIE_VILLAGER);
        map.put("ZombieNautilus", EntityTypes.ZOMBIE_NAUTILUS);

        return Map.copyOf(map);
    }
}
