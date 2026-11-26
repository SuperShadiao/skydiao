package pers.XiaoShadiao.skydiao.utils.neu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

/**
 * 把游戏内物品的 custom_data 映射为 NEU internalname。
 */
public final class NeuIds {

    private static final String[] PET_RARITIES = {
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL"
    };

    private NeuIds() {
    }

    public static String of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CompoundTag customData = stack.getComponents()
                .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag();
        String id = customData.getStringOr("id", "");
        if (id.isEmpty()) {
            return "";
        }

        return switch (id) {
            case "ENCHANTED_BOOK" -> {
                CompoundTag enchantments = customData.getCompoundOrEmpty("enchantments");
                String enchantment = enchantments.keySet().stream().findFirst().orElse("");
                if (enchantment.isEmpty()) {
                    yield id;
                }
                yield enchantment.toUpperCase(Locale.ENGLISH) + ";" + enchantments.getIntOr(enchantment, 0);
            }
            case "PET" -> {
                String petInfoJson = customData.getStringOr("petInfo", "");
                if (petInfoJson.isEmpty()) {
                    yield id;
                }
                try {
                    JsonObject petInfo = JsonParser.parseString(petInfoJson).getAsJsonObject();
                    String type = petInfo.get("type").getAsString().toUpperCase(Locale.ENGLISH);
                    String tier = petInfo.get("tier").getAsString().toUpperCase(Locale.ENGLISH);
                    yield type + ";" + rarityIndex(tier);
                } catch (Exception ignored) {
                    yield id;
                }
            }
            case "POTION" -> {
                String potion = customData.getStringOr("potion", "");
                int level = customData.getIntOr("potion_level", 0);
                if (potion.isEmpty() || level <= 0) {
                    yield id;
                }
                yield "POTION_" + potion.toUpperCase(Locale.ENGLISH) + ";" + level;
            }
            case "RUNE", "UNIQUE_RUNE" -> {
                CompoundTag runes = customData.getCompoundOrEmpty("runes");
                String rune = runes.keySet().stream().findFirst().orElse("");
                if (rune.isEmpty()) {
                    yield id;
                }
                yield rune.toUpperCase(Locale.ENGLISH) + "_RUNE;" + runes.getIntOr(rune, 0);
            }
            case "ATTRIBUTE_SHARD" -> {
                CompoundTag attributes = customData.getCompoundOrEmpty("attributes");
                String attribute = attributes.keySet().stream().findFirst().orElse("");
                if (attribute.isEmpty()) {
                    yield id;
                }
                String internalName = NeuItemRepository.INSTANCE.shardInternalName(attribute);
                yield internalName != null ? internalName : id;
            }
            default -> id.replace(":", "-");
        };
    }

    private static int rarityIndex(String tier) {
        for (int i = 0; i < PET_RARITIES.length; i++) {
            if (PET_RARITIES[i].equals(tier)) {
                return i;
            }
        }
        return 0;
    }
}
