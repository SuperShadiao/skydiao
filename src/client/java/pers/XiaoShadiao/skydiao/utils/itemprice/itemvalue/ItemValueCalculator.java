package pers.XiaoShadiao.skydiao.utils.itemprice.itemvalue;

import net.minecraft.nbt.CompoundTag;
import pers.XiaoShadiao.skydiao.utils.itemprice.AttributeShardRegistry;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * 自写的物品价值计算器，替代 net.azureaaron:networth-calculator。
 *
 * <p>计算口径对齐 Skyblocker 的 EstimatedItemValueTooltip / networth-calculator 1.0.5：
 * 基础价取 Bazaar 买价 → 3 天均价 → Lowest BIN，然后逐项加上附魔、星星、宝石、重铸等附加值。</p>
 *
 * <p>与库的差异（有意为之）：
 * <ul>
 *     <li>属性碎片改用当前 Bazaar 的 {@code SHARD_*} 产品定价，不再用已废弃的 {@code ATTRIBUTE_SHARD-*}；</li>
 *     <li>Kuudra 装备的进阶关系从官方 items 数据里现算，不再维护静态表；</li>
 *     <li>God Roll、新年蛋糕袋暂不计算（现价源没有对应数据）。</li>
 * </ul>
 */
public final class ItemValueCalculator {

    private static final String ENCHANTED_BOOK = "ENCHANTED_BOOK";
    private static final String ATTRIBUTE_SHARD = "ATTRIBUTE_SHARD";

    private static final List<String> PET_RARITIES =
            List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
    private static final Map<String, Integer> PET_RARITY_OFFSETS = Map.of(
            "COMMON", 0, "UNCOMMON", 6, "RARE", 11, "EPIC", 16, "LEGENDARY", 20,
            "MYTHIC", 20, "SPECIAL", 20, "VERY_SPECIAL", 20);
    private static final Set<String> BLOCKED_CANDY_REDUCTION_PETS =
            Set.of("ENDER_DRAGON", "GOLDEN_DRAGON", "SCATHA");
    /** 上限 200 级的宠物 */
    private static final Set<String> MAX_LEVEL_200_PETS =
            Set.of("GOLDEN_DRAGON", "JADE_DRAGON", "ROSE_DRAGON");

    /** 每级所需经验，按稀有度偏移量切分，和 PetConstants 一致。 */
    private static final int[] PET_LEVEL_XP = {
            100, 110, 120, 130, 145, 160, 175, 190, 210, 230, 250, 275, 300, 330, 360, 400, 440, 490, 540, 600,
            660, 730, 800, 880, 960, 1050, 1150, 1260, 1380, 1510, 1650, 1800, 1960, 2130, 2310, 2500, 2700, 2920,
            3160, 3420, 3700, 4000, 4350, 4750, 5200, 5700, 6300, 7000, 7800, 8700, 9700, 10800, 12000, 13300,
            14700, 16200, 17800, 19500, 21300, 23200, 25200, 27400, 29800, 32400, 35200, 38200, 41400, 44800,
            48400, 52200, 56200, 60400, 64800, 69400, 74200, 79200, 84700, 90700, 97200, 104200, 111700, 119700,
            128200, 137200, 146700, 156700, 167700, 179700, 192700, 206700, 221700, 237700, 254700, 272700,
            291700, 311700, 333700, 357700, 383700, 411700, 441700, 476700, 516700, 561700, 611700, 666700,
            726700, 791700, 861700, 936700, 1016700, 1101700, 1191700, 1286700, 1386700, 1496700, 1616700,
            1746700, 1886700, 0, 5555, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700,
            1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700,
            1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700,
            1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700,
            1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700,
            1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700,
            1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700,
            1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700,
            1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700, 1886700,
            1886700, 1886700
    };

    private ItemValueCalculator() {
    }

    /**
     * @param rawId      物品内部 id，例如 {@code HYPERION}、{@code ENCHANTED_BOOK}
     * @param apiId      价格 API 使用的 id，例如 {@code ENCHANTMENT_SHARPNESS_7}、{@code SHARD_FLAMING_SPIDER}
     * @param count      物品数量，基础价会乘以数量
     * @param customData 物品的 {@code custom_data}
     * @param items      官方 items 数据
     * @param parentOf   进阶关系：子物品 id -> 父物品 id
     * @param prices     价格查询函数，查不到返回 0
     * @return 估算总价值，无法计算返回 0
     */
    public static double calculate(String rawId, String apiId, int count, CompoundTag customData,
                                   Map<String, SkyblockItemInfo> items, Map<String, String> parentOf,
                                   ToDoubleFunction<String> prices) {
        if (rawId == null || rawId.isEmpty() || count <= 0) return 0;

        ItemValueMeta meta = ItemValueMeta.parse(customData);

        if ("PET".equals(rawId)) {
            return calculatePet(meta.pet, prices);
        }

        SkyblockItemInfo info = items.get(rawId);

        double price = price(prices, apiId) * count;

        // 没有市场价但记录过 Shen's Auction 成交价时按 85% 估
        if (price <= 0 && meta.shenPrice > 0) {
            price = meta.shenPrice * ItemValueConstants.weight("shensAuctionPrice");
        }

        // Pickonimbus 按剩余耐久折算
        if ("PICKONIMBUS".equals(rawId) && meta.pickonimbusDurability >= 0) {
            double reduction = meta.pickonimbusDurability / (double) ItemValueConstants.pickonimbusDurability();
            price += price * (reduction - 1);
        }

        // Kuudra 装备通常灵魂绑定、没有市场价，按进阶链的升级成本累加
        if (price <= 0 && parentOf.containsKey(rawId)) {
            price += prestigeValue(rawId, items, parentOf, prices);
        }

        // Shen's Auction 实付价
        if (meta.shenPrice > 0 && meta.auction >= 0 && meta.bid >= 0) {
            double scaled = meta.shenPrice * ItemValueConstants.weight("shensAuctionPrice");
            if (scaled > price) price = scaled;
        }

        // Midas 系列按最高成交价
        if ("MIDAS_STAFF".equals(rawId) || "MIDAS_SWORD".equals(rawId)) {
            price = midasValue(rawId, meta, price, prices);
        }

        if (ENCHANTED_BOOK.equals(rawId)) {
            price = enchantedBookValue(meta, price, prices);
        } else {
            price += enchantmentValue(rawId, meta, prices);
        }

        price += attributeValue(rawId, meta, prices);
        price += gearUpgradeValue(rawId, meta, prices);
        price += miscModifierValue(rawId, meta, prices);
        price += cosmeticValue(rawId, meta, prices);
        price += recombobulatorValue(meta, prices);
        price += gemstoneValue(rawId, meta, info, prices);
        price += reforgeValue(meta, info, prices);
        price += starValue(meta, info, prices);
        price += drillValue(meta, prices);

        return Double.isFinite(price) && price > 0 ? price : 0;
    }

    // ------------------------------------------------------------------
    // 各类附加值
    // ------------------------------------------------------------------

    private static double enchantedBookValue(ItemValueMeta meta, double fallback, ToDoubleFunction<String> prices) {
        if (meta.enchantments.isEmpty()) return fallback;

        if (meta.enchantments.size() == 1) {
            Map.Entry<String, Integer> entry = meta.enchantments.entrySet().iterator().next();
            return price(prices, "ENCHANTMENT_" + entry.getKey().toUpperCase(Locale.ENGLISH) + "_" + entry.getValue());
        }

        double total = 0;
        for (Map.Entry<String, Integer> entry : meta.enchantments.entrySet()) {
            String id = "ENCHANTMENT_" + entry.getKey().toUpperCase(Locale.ENGLISH) + "_" + entry.getValue();
            total += price(prices, id) * ItemValueConstants.weight("enchantments");
        }
        return total;
    }

    private static double enchantmentValue(String itemId, ItemValueMeta meta, ToDoubleFunction<String> prices) {
        double total = 0;

        for (Map.Entry<String, Integer> entry : meta.enchantments.entrySet()) {
            String enchantment = entry.getKey().toUpperCase(Locale.ENGLISH);
            int level = entry.getValue();

            if (ItemValueConstants.isBlockedEnchantment(itemId, enchantment)) continue;
            if (ItemValueConstants.isIgnoredEnchantment(enchantment, level)) continue;
            if (ItemValueConstants.isStackingEnchantment(enchantment)) level = 1;

            if ("EFFICIENCY".equals(enchantment) && level > 5 && !ItemValueConstants.ignoreSilex(itemId)) {
                total += price(prices, "SIL_EX") * (level - 5) * ItemValueConstants.weight("silex");
            }

            if ("SCAVENGER".equals(enchantment) && level == 6) {
                total += price(prices, "GOLDEN_BOUNTY") * ItemValueConstants.weight("goldenBounty");
            }

            double enchantmentPrice;
            if (enchantment.startsWith("ULTIMATE")) {
                // 高层终极附魔单本可能比"多本 1 级"还贵，取两者较高值
                int levelOneBooks = (int) Math.pow(2, level - 1);
                double direct = price(prices, "ENCHANTMENT_" + enchantment + "_" + level);
                double fromLevelOne = price(prices, "ENCHANTMENT_" + enchantment + "_1") * levelOneBooks;
                enchantmentPrice = Math.max(direct, fromLevelOne);
            } else {
                enchantmentPrice = price(prices, "ENCHANTMENT_" + enchantment + "_" + level);
            }

            total += enchantmentPrice * ItemValueConstants.enchantmentWeight(enchantment);
        }

        return total;
    }

    private static double attributeValue(String itemId, ItemValueMeta meta, ToDoubleFunction<String> prices) {
        double total = 0;

        for (Map.Entry<String, Integer> entry : meta.attributes.entrySet()) {
            String attribute = entry.getKey().toUpperCase(Locale.ENGLISH);
            int tier = entry.getValue();
            if (tier <= 1) continue;

            int shardsNeeded = (int) (Math.pow(2, tier - 1) - 1);
            double shardPrice = 0;

            // 当前版本的属性碎片就是 Bazaar 的 SHARD_* 产品
            String bazaarId = AttributeShardRegistry.getBazaarName(attribute);
            if (bazaarId != null) {
                shardPrice = price(prices, bazaarId);
            }

            // 部分装备的属性应从更便宜的底板装备折算
            String baseCostId = ItemValueConstants.attributeBaseCostId(itemId);
            if (baseCostId != null) {
                double baseCost = price(prices, baseCostId);
                if (baseCost > 0 && (shardPrice <= 0 || baseCost < shardPrice)) shardPrice = baseCost;
            }

            total += shardPrice * shardsNeeded * ItemValueConstants.weight("attributes");
        }

        return total;
    }

    private static double gearUpgradeValue(String itemId, ItemValueMeta meta, ToDoubleFunction<String> prices) {
        double total = 0;

        if (meta.hotPotatoes > 0) {
            if (meta.hotPotatoes > 10) {
                int fuming = meta.hotPotatoes - 10;
                total += price(prices, "FUMING_POTATO_BOOK") * fuming * ItemValueConstants.weight("fumingPotatoBook");
            }
            total += price(prices, "HOT_POTATO_BOOK") * Math.min(meta.hotPotatoes, 10)
                    * ItemValueConstants.weight("hotPotatoBook");
        }

        total += extra(prices, "WOOD_SINGULARITY", meta.woodSingularities, "woodSingularity");
        total += extra(prices, "THE_ART_OF_WAR", meta.artOfWars, "artOfWar");
        total += extra(prices, "THE_ART_OF_PEACE", meta.artOfPeaces, "artOfPeace");

        if (meta.powerScroll != null) {
            total += price(prices, meta.powerScroll) * ItemValueConstants.weight("powerScroll");
        }

        for (String scroll : meta.abilityScrolls) {
            total += price(prices, scroll) * ItemValueConstants.weight("witherScroll");
        }

        return total;
    }

    private static double miscModifierValue(String itemId, ItemValueMeta meta, ToDoubleFunction<String> prices) {
        double total = 0;

        total += extra(prices, "POCKET_SACK_IN_A_SACK", meta.pocketSackInASacks, "pocketSackInASack");
        total += extra(prices, "JALAPENO_BOOK", meta.jalapenoBooks, "jalapenoBook");
        total += extra(prices, "MANA_DISINTEGRATOR", meta.manaDisintegrators, "manaDisintegrator");
        total += extra(prices, "TRANSMISSION_TUNER", meta.transmissionTuners, "transmissionTuner");
        total += extra(prices, "FARMING_FOR_DUMMIES", meta.farming4Dummies, "farming4Dummies");

        if (meta.ethermerge > 0) {
            total += price(prices, "ETHERWARP_CONDUIT") * ItemValueConstants.weight("etherwarp");
        }

        if (meta.thunderCharges > 0 && "PULSE_RING".equals(itemId)) {
            int bottles = Math.floorDiv(meta.thunderCharges, 50_000);
            total += price(prices, "THUNDER_IN_A_BOTTLE") * bottles * ItemValueConstants.weight("thunderInABottle");
        }

        if (meta.enrichment != null) {
            // 现在 AH 上的词缀物品统一是 ACCESSORY_ENRICHMENT，旧的 TALISMAN_ENRICHMENT_* 已无挂单
            total += price(prices, "ACCESSORY_ENRICHMENT") * ItemValueConstants.weight("enrichment");
        }

        return total;
    }

    private static double cosmeticValue(String itemId, ItemValueMeta meta, ToDoubleFunction<String> prices) {
        double total = 0;

        if (!meta.runes.isEmpty() && !itemId.contains("RUNE")) {
            Map.Entry<String, Integer> rune = meta.runes.entrySet().iterator().next();
            String runeItemId = rune.getKey().toUpperCase(Locale.ENGLISH) + "_RUNE_" + rune.getValue();
            if (ItemValueConstants.isValidRune(runeItemId)) {
                total += price(prices, runeItemId) * ItemValueConstants.weight("runes");
            }
        }

        if (meta.dye != null) {
            total += price(prices, meta.dye) * ItemValueConstants.weight("dye");
        }

        // 物品皮肤按物品自身 id 查价（PET_SKIN_* / 具体皮肤 id）
        if (meta.skin != null) {
            total += price(prices, meta.skin) * ItemValueConstants.weight("skins");
        }

        return total;
    }

    private static double recombobulatorValue(ItemValueMeta meta, ToDoubleFunction<String> prices) {
        // 怪物掉落物（item_tier）不算 recomb
        if (meta.rarityUpgrades <= 0 || meta.itemTier >= 0) return 0;
        return price(prices, "RECOMBOBULATOR_3000") * meta.rarityUpgrades
                * ItemValueConstants.weight("recombobulator");
    }

    private static double gemstoneValue(String rawId, ItemValueMeta meta, SkyblockItemInfo info,
                                        ToDoubleFunction<String> prices) {
        if (info == null || info.gemstoneSlots() == null) return 0;

        double total = 0;

        // 已镶嵌的宝石
        for (Map.Entry<String, String> entry : meta.gemstones.entrySet()) {
            String key = entry.getKey();
            String quality = entry.getValue();
            String type = meta.gemstoneTypes.get(key);
            if (type == null) {
                type = key.replaceAll("_\\d+$", "");
            }
            if (ItemValueConstants.isGemstoneGroupSlot(type.toUpperCase(Locale.ENGLISH))) continue;

            total += price(prices, (quality + "_" + type + "_GEM").toUpperCase(Locale.ENGLISH));
        }

        // Divan 装备额外解锁的宝石槽按镶孔成本计价
        if (ItemValueConstants.isDivanGear(rawId)) {
            for (String unlocked : meta.unlockedGemstoneSlots) {
                String unlockedUpper = unlocked.toUpperCase(Locale.ENGLISH);
                for (SkyblockItemInfo.GemstoneSlot slot : info.gemstoneSlots()) {
                    if (slot.costs() == null || !unlockedUpper.startsWith(slot.type())) continue;

                    double cost = 0;
                    for (SkyblockItemInfo.GemstoneSlotCost slotCost : slot.costs()) {
                        cost += "COINS".equals(slotCost.type())
                                ? slotCost.coins()
                                : price(prices, slotCost.itemId()) * slotCost.amount();
                    }
                    total += cost * ItemValueConstants.weight("gemstoneChambers");
                    break;
                }
            }
        }

        return total;
    }

    private static double reforgeValue(ItemValueMeta meta, SkyblockItemInfo info, ToDoubleFunction<String> prices) {
        if (meta.reforge == null) return 0;
        // 饰品重铸不再计入价值
        if (info != null && "ACCESSORY".equals(info.category())) return 0;

        String stone = ItemValueConstants.reforgeItemId(meta.reforge);
        if (stone == null) return 0;
        return price(prices, stone) * ItemValueConstants.weight("reforge");
    }

    private static double starValue(ItemValueMeta meta, SkyblockItemInfo info, ToDoubleFunction<String> prices) {
        if (info == null || info.upgradeCosts() == null) return 0;

        List<List<SkyblockItemInfo.GearUpgrade>> costs = info.upgradeCosts();
        int dungeonLevel = Math.max(meta.dungeonItemLevel, 0);
        int upgradeLevel = Math.max(meta.upgradeLevel, 0);
        int level = Math.max(dungeonLevel, upgradeLevel);

        double total = 0;

        // 5 星之后是大师星
        int masterStarsUsed = level - 5;
        if (masterStarsUsed > 0 && costs.size() <= 5) {
            List<String> masterStars = ItemValueConstants.masterStars();
            for (int i = 0; i < Math.min(masterStarsUsed, masterStars.size()); i++) {
                total += price(prices, masterStars.get(i));
            }
        }

        if (level > 0) {
            total += upgradeCostSum(costs.subList(0, Math.min(level + 1, costs.size())), prices);
        }

        return total;
    }

    private static double drillValue(ItemValueMeta meta, ToDoubleFunction<String> prices) {
        double total = 0;

        if (meta.drillFuelTank != null) {
            total += price(prices, meta.drillFuelTank.toUpperCase(Locale.ENGLISH))
                    * ItemValueConstants.weight("drillPart");
        }
        if (meta.drillEngine != null) {
            total += price(prices, meta.drillEngine.toUpperCase(Locale.ENGLISH))
                    * ItemValueConstants.weight("drillPart");
        }
        if (meta.drillUpgradeModule != null) {
            total += price(prices, meta.drillUpgradeModule.toUpperCase(Locale.ENGLISH))
                    * ItemValueConstants.weight("drillPart");
        }
        if (meta.polarvoids > 0) {
            total += price(prices, "POLARVOID_BOOK") * meta.polarvoids
                    * ItemValueConstants.weight("polarvoidBook");
        }
        if (meta.divanPowderCoating > 0) {
            total += price(prices, "DIVAN_POWDER_COATING") * meta.divanPowderCoating
                    * ItemValueConstants.weight("divanPowderCoating");
        }

        return total;
    }

    private static double midasValue(String itemId, ItemValueMeta meta, double current,
                                     ToDoubleFunction<String> prices) {
        if (meta.winningBid < 0) return current;

        int maxBid = "MIDAS_SWORD".equals(itemId) ? 50_000_000 : 100_000_000;
        if (meta.winningBid >= maxBid) {
            String maxItem = "MIDAS_SWORD".equals(itemId) ? "MIDAS_SWORD_50M" : "MIDAS_STAFF_100M";
            return price(prices, maxItem);
        }
        return meta.winningBid * ItemValueConstants.weight("winningBid");
    }

    // ------------------------------------------------------------------
    // 进阶链 / 升级成本
    // ------------------------------------------------------------------

    private static double prestigeValue(String rawId, Map<String, SkyblockItemInfo> items,
                                        Map<String, String> parentOf, ToDoubleFunction<String> prices) {
        double total = 0;
        String current = parentOf.get(rawId);

        while (current != null) {
            SkyblockItemInfo ancestor = items.get(current);
            if (ancestor != null) {
                if (ancestor.upgradeCosts() != null) {
                    total += upgradeCostSum(ancestor.upgradeCosts(), prices);
                }
                if (ancestor.prestige() != null) {
                    total += upgradeCostSum(List.of(ancestor.prestige().costs()), prices);
                }
            }
            current = parentOf.get(current);
        }

        return total;
    }

    private static double upgradeCostSum(List<List<SkyblockItemInfo.GearUpgrade>> costs,
                                         ToDoubleFunction<String> prices) {
        double total = 0;

        for (List<SkyblockItemInfo.GearUpgrade> level : costs) {
            for (SkyblockItemInfo.GearUpgrade upgrade : level) {
                if (upgrade.essenceType() != null) {
                    total += price(prices, "ESSENCE_" + upgrade.essenceType()) * upgrade.amount()
                            * ItemValueConstants.weight("essence");
                } else if (upgrade.itemId() != null) {
                    total += price(prices, upgrade.itemId()) * upgrade.amount();
                }
            }
        }

        return total;
    }

    // ------------------------------------------------------------------
    // 宠物
    // ------------------------------------------------------------------

    private static double calculatePet(ItemValueMeta.PetInfo pet, ToDoubleFunction<String> prices) {
        if (pet == null || pet.isEmpty()) return 0;

        String tier = effectivePetTier(pet);
        String type = pet.type();

        double levelOne = price(prices, "LVL_1_" + tier + "_" + type);
        double levelHundred = price(prices, "LVL_100_" + tier + "_" + type);
        double levelTwoHundred = price(prices, "LVL_200_" + tier + "_" + type);

        int[] levelInfo = petLevel(pet, tier);
        int level = levelInfo[0];
        int maxXp = levelInfo[1];

        // 价格源按等级提供 key，采集不到所有等级，所以只在有对应锚点时估值：
        // 1~99 级用 1 级与 100 级插值；100 级以上用 100/200 级价格
        double price;
        if (level < 100) {
            if (levelOne > 0 && levelHundred > 0 && maxXp > 0) {
                price = (levelHundred - levelOne) / maxXp * pet.xp() + levelOne;
            } else if (level <= 1 && levelOne > 0) {
                price = levelOne;
            } else {
                return 0;
            }
        } else if (level < 200) {
            if (levelHundred <= 0) return 0;
            if (levelTwoHundred > 0) {
                int incrementalLevel = level % (int) Math.pow(10, (int) Math.log10(level));
                price = incrementalLevel != 1
                        ? (levelTwoHundred - levelHundred) / 100 * incrementalLevel + levelHundred
                        : levelHundred;
            } else {
                price = levelHundred;
            }
        } else {
            // 200 级宠物优先用 200 级价格，采不到时退回 100 级
            price = levelTwoHundred > 0 ? levelTwoHundred : levelHundred;
            if (price <= 0) return 0;
        }

        if (pet.skin() != null) {
            price += price(prices, "PET_SKIN_" + pet.skin()) * ItemValueConstants.weight("skins");
        }
        if (pet.heldItem() != null) {
            price += price(prices, pet.heldItem());
        }

        int xpFromCandy = pet.candies() * 1_000_000;
        if (pet.candies() > 0 && !BLOCKED_CANDY_REDUCTION_PETS.contains(type)
                && pet.xp() - xpFromCandy < maxXp) {
            double reduced = price * ItemValueConstants.weight("petCandy");
            price = Math.max(reduced, price - (level == 100 ? 5_000_000 : 2_500_000));
        }

        return price;
    }

    private static String effectivePetTier(ItemValueMeta.PetInfo pet) {
        String tier = pet.tier();
        if ("PET_ITEM_TIER_BOOST".equals(pet.heldItem())) {
            int index = PET_RARITIES.indexOf(tier);
            if (index >= 0 && index < PET_RARITIES.size() - 1) {
                tier = PET_RARITIES.get(index + 1);
            }
        }
        return tier;
    }

    /** @return {@code [等级, 满级所需总经验]} */
    private static int[] petLevel(ItemValueMeta.PetInfo pet, String tier) {
        int maxLevel = MAX_LEVEL_200_PETS.contains(pet.type()) ? 200 : 100;
        String offsetTier = "BINGO".equals(pet.type()) ? "COMMON" : tier;
        int offset = PET_RARITY_OFFSETS.getOrDefault(offsetTier, 0);

        int maxXp = 0;
        for (int i = 0; i < maxLevel - 1; i++) {
            maxXp += PET_LEVEL_XP[Math.min(offset + i, PET_LEVEL_XP.length - 1)];
        }

        int level = 1;
        int totalXp = 0;

        for (int i = 0; i < maxLevel - 1; i++) {
            int levelXp = PET_LEVEL_XP[Math.min(offset + i, PET_LEVEL_XP.length - 1)];
            totalXp += levelXp;
            if (totalXp > pet.xp()) break;
            level++;
        }

        return new int[]{Math.min(level, maxLevel), maxXp};
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static double extra(ToDoubleFunction<String> prices, String id, int amount, String weightKey) {
        if (amount <= 0) return 0;
        return price(prices, id) * amount * ItemValueConstants.weight(weightKey);
    }

    private static double price(ToDoubleFunction<String> prices, String id) {
        if (id == null || id.isEmpty()) return 0;
        double value = prices.applyAsDouble(id);
        return Double.isFinite(value) && value > 0 ? value : 0;
    }
}
