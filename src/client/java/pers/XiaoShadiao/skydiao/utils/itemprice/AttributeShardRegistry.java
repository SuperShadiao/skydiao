package pers.XiaoShadiao.skydiao.utils.itemprice;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 属性碎片（ATTRIBUTE_SHARD）到 Bazaar 产品 id 的映射。
 *
 * <p>数据来自 NEU 的 {@code constants/attribute_shards.json}，精简后打包在
 * {@code /assets/skydiao/attribute_shards.json}，包含两部分：</p>
 *
 * <ul>
 *   <li>{@code attributes}：物品 NBT {@code custom_data.attributes} 的键 -> Bazaar 产品 id，
 *       例如 {@code arachno -> SHARD_FLAMING_SPIDER}；</li>
 *   <li>{@code display_names}：碎片显示名（小写）-> Bazaar 产品 id，
 *       例如 {@code bogged -> SHARD_SEA_ARCHER}、{@code zealot bruiser -> SHARD_BRUISER}。
 *       用于 Hunting Box 这类只有显示名、没有 NBT id 的菜单物品。</li>
 * </ul>
 *
 * <p>等级（arachno 后面的 int）不参与 Bazaar 键，Bazaar 只按基础碎片 id 定价。</p>
 */
public final class AttributeShardRegistry {

    private static final Logger LOGGER = LogManager.getLogger("XSD AttributeShard");
    private static final String RESOURCE = "/assets/skydiao/attribute_shards.json";

    private static volatile Map<String, String> attributeToBazaar;
    private static volatile Map<String, String> displayNameToBazaar;

    private AttributeShardRegistry() {
    }

    /**
     * @param attributeKey custom_data.attributes 里的键，例如 {@code arachno}
     * @return Bazaar 产品 id，例如 {@code SHARD_FLAMING_SPIDER}；无映射时返回 null
     */
    public static String getBazaarName(String attributeKey) {
        if (attributeKey == null || attributeKey.isEmpty()) return null;
        ensureLoaded();
        return attributeToBazaar.get(attributeKey.toLowerCase(Locale.ENGLISH));
    }

    /**
     * @param displayName 碎片显示名，例如 {@code Bogged}、{@code Zealot Bruiser}
     * @return Bazaar 产品 id，例如 {@code SHARD_SEA_ARCHER}；无映射时返回 null
     */
    public static String getBazaarNameByDisplayName(String displayName) {
        if (displayName == null || displayName.isEmpty()) return null;
        ensureLoaded();
        return displayNameToBazaar.get(displayName.toLowerCase(Locale.ENGLISH));
    }

    private static void ensureLoaded() {
        if (attributeToBazaar != null && displayNameToBazaar != null) return;

        synchronized (AttributeShardRegistry.class) {
            if (attributeToBazaar == null || displayNameToBazaar == null) {
                load();
            }
        }
    }

    private static void load() {
        Map<String, String> attributes = Map.of();
        Map<String, String> displayNames = Map.of();

        try (InputStream stream = AttributeShardRegistry.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                LOGGER.warn("未找到属性碎片映射资源: {}", RESOURCE);
            } else {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                attributes = readMap(root.getAsJsonObject("attributes"));
                displayNames = readMap(root.getAsJsonObject("display_names"));
                LOGGER.info("属性碎片映射已加载: {} 个属性键, {} 个显示名", attributes.size(), displayNames.size());
            }
        } catch (Exception e) {
            LOGGER.warn("加载属性碎片映射失败", e);
        }

        attributeToBazaar = attributes;
        displayNameToBazaar = displayNames;
    }

    private static Map<String, String> readMap(JsonObject object) {
        if (object == null) return Map.of();

        Map<String, String> parsed = new HashMap<>(object.size());
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && !value.isJsonNull()) {
                parsed.put(entry.getKey().toLowerCase(Locale.ENGLISH), value.getAsString());
            }
        }
        return Map.copyOf(parsed);
    }
}
