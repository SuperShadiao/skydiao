package pers.XiaoShadiao.skydiao.utils.itemprice.itemvalue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hypixel 官方 {@code /v2/resources/skyblock/items} 里计算物品价值需要的字段。
 *
 * <p>只保留四个字段：{@code category}、{@code upgrade_costs}（星星/精华升级）、
 * {@code gemstone_slots}（宝石槽）和 {@code prestige}（Kuudra 装备进阶）。
 * 其余字段（材质、属性、博物馆数据）计算价值用不到。</p>
 */
public record SkyblockItemInfo(
        String id,
        String category,
        List<List<GearUpgrade>> upgradeCosts,
        List<GemstoneSlot> gemstoneSlots,
        Prestige prestige
) {

    /** 单次升级消耗：{@code item_id} 和 {@code essence_type} 二选一。 */
    public record GearUpgrade(String itemId, String essenceType, int amount) {
    }

    public record GemstoneSlot(String type, List<GemstoneSlotCost> costs) {
    }

    public record GemstoneSlotCost(String type, String itemId, int amount, long coins) {
    }

    public record Prestige(String itemId, List<GearUpgrade> costs) {
    }

    /**
     * 解析官方 items 数组，同时生成 {@code 子物品 -> 父物品} 的进阶关系表。
     *
     * <p>官方数据里 {@code CRIMSON_HELMET.prestige.item_id = HOT_CRIMSON_HELMET}，
     * 因此 {@code parentOf[HOT_CRIMSON_HELMET] = CRIMSON_HELMET}。这样就不必像
     * Skyblocker 那样维护一份上百行的静态进阶表。</p>
     */
    public static Map<String, SkyblockItemInfo> parseAll(JsonArray items, Map<String, String> parentOf) {
        Map<String, SkyblockItemInfo> parsed = new HashMap<>(items.size());

        for (JsonElement element : items) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();

            String id = stringOrNull(object, "id");
            if (id == null) continue;

            List<List<GearUpgrade>> upgradeCosts = parseUpgradeCosts(object.getAsJsonArray("upgrade_costs"));
            List<GemstoneSlot> gemstoneSlots = parseGemstoneSlots(object.getAsJsonArray("gemstone_slots"));
            Prestige prestige = parsePrestige(object.getAsJsonObject("prestige"));

            parsed.put(id, new SkyblockItemInfo(id, stringOrNull(object, "category"),
                    upgradeCosts, gemstoneSlots, prestige));

            if (prestige != null) {
                parentOf.put(prestige.itemId(), id);
            }
        }

        return Collections.unmodifiableMap(parsed);
    }

    private static List<List<GearUpgrade>> parseUpgradeCosts(JsonArray array) {
        if (array == null || array.isEmpty()) return null;

        List<List<GearUpgrade>> parsed = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element.isJsonArray()) {
                parsed.add(parseGearUpgrades(element.getAsJsonArray()));
            } else if (element.isJsonObject()) {
                parsed.add(List.of(parseGearUpgrade(element.getAsJsonObject())));
            }
        }
        return parsed.isEmpty() ? null : parsed;
    }

    private static List<GearUpgrade> parseGearUpgrades(JsonArray array) {
        List<GearUpgrade> parsed = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                parsed.add(parseGearUpgrade(element.getAsJsonObject()));
            }
        }
        return parsed;
    }

    private static GearUpgrade parseGearUpgrade(JsonObject object) {
        return new GearUpgrade(
                stringOrNull(object, "item_id"),
                stringOrNull(object, "essence_type"),
                intOr(object, "amount", 0)
        );
    }

    private static List<GemstoneSlot> parseGemstoneSlots(JsonArray array) {
        if (array == null || array.isEmpty()) return null;

        List<GemstoneSlot> parsed = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();

            String type = stringOrNull(object, "slot_type");
            if (type == null) continue;

            JsonArray costsArray = object.getAsJsonArray("costs");
            List<GemstoneSlotCost> costs = null;
            if (costsArray != null && !costsArray.isEmpty()) {
                costs = new ArrayList<>(costsArray.size());
                for (JsonElement costElement : costsArray) {
                    if (!costElement.isJsonObject()) continue;
                    JsonObject cost = costElement.getAsJsonObject();
                    costs.add(new GemstoneSlotCost(
                            stringOrNull(cost, "type"),
                            stringOrNull(cost, "item_id"),
                            intOr(cost, "amount", 0),
                            intOr(cost, "coins", 0)
                    ));
                }
            }

            parsed.add(new GemstoneSlot(type, costs));
        }
        return parsed.isEmpty() ? null : parsed;
    }

    private static Prestige parsePrestige(JsonObject object) {
        if (object == null) return null;

        String itemId = stringOrNull(object, "item_id");
        if (itemId == null) return null;

        JsonArray costsArray = object.getAsJsonArray("costs");
        List<GearUpgrade> costs = costsArray == null ? List.of() : parseGearUpgrades(costsArray);
        return new Prestige(itemId, costs);
    }

    private static String stringOrNull(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static int intOr(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsInt();
    }
}
