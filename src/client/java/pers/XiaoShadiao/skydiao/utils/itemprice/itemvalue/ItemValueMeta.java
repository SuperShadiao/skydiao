package pers.XiaoShadiao.skydiao.utils.itemprice.itemvalue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 从物品 {@code custom_data} 里解析出的、参与价值计算的 NBT 字段。
 *
 * <p>字段名与 Hypixel 的 NBT 保持一致，解析方式对齐 Skyblocker 使用的
 * networth-calculator，因此数值口径可以和它对照。</p>
 *
 * <p>约定：表示"数量"的 int 字段用 {@code -1} 表示 NBT 里不存在，
 * 字符串字段用 {@code null} 表示不存在。</p>
 */
public final class ItemValueMeta {

    public final Map<String, Integer> enchantments;
    public final int rarityUpgrades;
    public final String reforge;
    public final int upgradeLevel;
    /** 怪物掉落物品的 tier，用于排除 recombobulator 计费 */
    public final int itemTier;
    public final int dungeonItemLevel;
    public final List<String> abilityScrolls;

    public final int hotPotatoes;
    public final int artOfWars;
    public final int woodSingularities;
    public final int artOfPeaces;
    public final String powerScroll;

    /** 已付费解锁的宝石槽，例如 {@code COMBAT_0} */
    public final List<String> unlockedGemstoneSlots;
    /** 槽位键 -> 品质，例如 {@code COMBAT_0 -> FLAWLESS} */
    public final Map<String, String> gemstones;
    /** 槽位键 -> 实际宝石类型，来自 {@code <槽位>_gem} */
    public final Map<String, String> gemstoneTypes;

    public final String drillFuelTank;
    public final String drillEngine;
    public final String drillUpgradeModule;
    public final int polarvoids;
    public final int pickonimbusDurability;
    public final int divanPowderCoating;

    public final Map<String, Integer> attributes;

    /** Shen's Auction 成交价 */
    public final int shenPrice;
    public final int winningBid;
    public final int bid;
    public final int auction;

    public final int pocketSackInASacks;
    public final int jalapenoBooks;
    public final int manaDisintegrators;
    public final int transmissionTuners;
    public final int ethermerge;
    public final int farming4Dummies;

    public final String enrichment;
    public final int thunderCharges;

    public final Map<String, Integer> runes;
    public final String skin;
    public final String dye;
    public final boolean shiny;

    public final PetInfo pet;

    public final int newYearCakeYear;
    public final String partyHatColor;
    public final String partyHatEmoji;

    public record PetInfo(String type, double xp, String tier, int candies, String heldItem, String skin) {
        public static final PetInfo EMPTY = new PetInfo("", 0, "", 0, null, null);

        public boolean isEmpty() {
            return type.isEmpty();
        }
    }

    private ItemValueMeta(CompoundTag tag) {
        Dynamic<Tag> dynamic = new Dynamic<>(NbtOps.INSTANCE, tag);

        this.enchantments = readIntMap(tag, "enchantments");
        this.rarityUpgrades = tag.getIntOr("rarity_upgrades", 0);
        this.reforge = emptyToNull(tag.getStringOr("modifier", ""));
        this.upgradeLevel = tag.getIntOr("upgrade_level", 0);
        this.itemTier = tag.contains("item_tier") ? tag.getIntOr("item_tier", 0) : -1;
        this.dungeonItemLevel = tag.contains("dungeon_item_level") ? tag.getIntOr("dungeon_item_level", 0) : -1;
        this.abilityScrolls = readStringList(dynamic, "ability_scroll");

        this.hotPotatoes = tag.contains("hot_potato_count") ? tag.getIntOr("hot_potato_count", 0) : -1;
        this.artOfWars = tag.contains("art_of_war_count") ? tag.getIntOr("art_of_war_count", 0) : -1;
        this.woodSingularities = tag.contains("wood_singularity_count") ? tag.getIntOr("wood_singularity_count", 0) : -1;
        this.artOfPeaces = tag.contains("artOfPeaceApplied") ? tag.getIntOr("artOfPeaceApplied", 0) : -1;
        this.powerScroll = emptyToNull(tag.getStringOr("power_ability_scroll", ""));

        CompoundTag gems = tag.getCompoundOrEmpty("gems");
        this.unlockedGemstoneSlots = readStringList(new Dynamic<>(NbtOps.INSTANCE, gems), "unlocked_slots");
        Map<String, String> gemstoneQualities = new HashMap<>();
        Map<String, String> gemstoneTypeHints = new HashMap<>();
        for (String key : gems.keySet()) {
            if ("unlocked_slots".equals(key)) continue;

            if (key.endsWith("_gem")) {
                String type = gems.getStringOr(key, "");
                if (!type.isEmpty()) gemstoneTypeHints.put(key.substring(0, key.length() - "_gem".length()), type);
                continue;
            }

            CompoundTag value = gems.getCompoundOrEmpty(key);
            String quality = value.keySet().isEmpty() ? gems.getStringOr(key, "") : value.getStringOr("quality", "");
            if (!quality.isEmpty()) gemstoneQualities.put(key, quality);
        }
        this.gemstones = gemstoneQualities;
        this.gemstoneTypes = gemstoneTypeHints;

        this.drillFuelTank = emptyToNull(tag.getStringOr("drill_part_fuel_tank", ""));
        this.drillEngine = emptyToNull(tag.getStringOr("drill_part_engine", ""));
        this.drillUpgradeModule = emptyToNull(tag.getStringOr("drill_part_upgrade_module", ""));
        this.polarvoids = tag.getIntOr("polarvoid", 0);
        this.pickonimbusDurability = tag.contains("pickonimbus_durability") ? tag.getIntOr("pickonimbus_durability", 0) : -1;
        this.divanPowderCoating = tag.contains("divan_powder_coating") ? tag.getIntOr("divan_powder_coating", 0) : -1;

        this.attributes = readIntMap(tag, "attributes");

        this.shenPrice = tag.contains("price") ? tag.getIntOr("price", 0) : -1;
        this.winningBid = tag.contains("winning_bid") ? tag.getIntOr("winning_bid", 0) : -1;
        this.bid = tag.contains("bid") ? tag.getIntOr("bid", 0) : -1;
        this.auction = tag.contains("auction") ? tag.getIntOr("auction", 0) : -1;

        this.pocketSackInASacks = tag.contains("sack_pss") ? tag.getIntOr("sack_pss", 0) : -1;
        this.jalapenoBooks = tag.contains("jalapeno_count") ? tag.getIntOr("jalapeno_count", 0) : -1;
        this.manaDisintegrators = tag.contains("mana_disintegrator_count") ? tag.getIntOr("mana_disintegrator_count", 0) : -1;
        this.transmissionTuners = tag.contains("tuned_transmission") ? tag.getIntOr("tuned_transmission", 0) : -1;
        this.ethermerge = tag.contains("ethermerge") ? tag.getIntOr("ethermerge", 0) : -1;
        this.farming4Dummies = tag.contains("farming_for_dummies_count") ? tag.getIntOr("farming_for_dummies_count", 0) : -1;

        this.enrichment = emptyToNull(tag.getStringOr("talisman_enrichment", ""));
        this.thunderCharges = tag.contains("thunder_charge") ? tag.getIntOr("thunder_charge", 0) : -1;

        this.runes = readIntMap(tag, "runes");
        this.skin = emptyToNull(tag.getStringOr("skin", ""));
        this.dye = emptyToNull(tag.getStringOr("dye_item", ""));
        this.shiny = tag.getBooleanOr("is_shiny", false);

        this.pet = readPetInfo(tag.getStringOr("petInfo", ""));

        this.newYearCakeYear = tag.contains("new_years_cake") ? tag.getIntOr("new_years_cake", 0) : -1;
        this.partyHatColor = emptyToNull(tag.getStringOr("party_hat_color", ""));
        this.partyHatEmoji = emptyToNull(tag.getStringOr("party_hat_emoji", ""));
    }

    public static ItemValueMeta parse(CompoundTag tag) {
        return new ItemValueMeta(tag == null ? new CompoundTag() : tag);
    }

    private static Map<String, Integer> readIntMap(CompoundTag tag, String key) {
        CompoundTag compound = tag.getCompoundOrEmpty(key);
        if (compound.keySet().isEmpty()) return Map.of();

        Map<String, Integer> parsed = new HashMap<>(compound.keySet().size());
        for (String entryKey : compound.keySet()) {
            parsed.put(entryKey.toLowerCase(Locale.ENGLISH), compound.getIntOr(entryKey, 0));
        }
        return parsed;
    }

    private static List<String> readStringList(Dynamic<Tag> dynamic, String key) {
        List<String> parsed = new ArrayList<>();
        for (Dynamic<Tag> element : dynamic.get(key).asStream().toList()) {
            String value = element.asString("");
            if (!value.isEmpty()) parsed.add(value);
        }
        return List.copyOf(parsed);
    }

    private static PetInfo readPetInfo(String json) {
        if (json.isEmpty()) return PetInfo.EMPTY;

        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            return new PetInfo(
                    stringOr(object, "type"),
                    object.has("exp") ? object.get("exp").getAsDouble() : 0,
                    stringOr(object, "tier"),
                    object.has("candyUsed") ? object.get("candyUsed").getAsInt() : 0,
                    emptyToNull(stringOr(object, "heldItem")),
                    emptyToNull(stringOr(object, "skin"))
            );
        } catch (Exception e) {
            return PetInfo.EMPTY;
        }
    }

    private static String stringOr(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
