package pers.XiaoShadiao.skydiao.utils.itemprice;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.XiaoShadiao.skydiao.utils.FetchFailureNotifier;
import pers.XiaoShadiao.skydiao.utils.itemprice.itemvalue.ItemValueCalculator;
import pers.XiaoShadiao.skydiao.utils.itemprice.itemvalue.SkyblockItemInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Est. Item Value（物品估值），行为对齐 Skyblocker 的 EstimatedItemValueTooltip。
 *
 * <p>物品基础数据来自 Hypixel 官方 {@code /v2/resources/skyblock/items}，
 * 价格来源复用 {@link PriceDataManager}（Bazaar 优先，其次 3 天均价 / Lowest BIN），
 * 升级项由自写的 {@link ItemValueCalculator} 计算，不再依赖 networth-calculator。</p>
 *
 * <p>物品数据在后台线程拉取，tooltip 渲染时只读内存快照。</p>
 */
public final class NetworthDataManager {

    private static final Logger LOGGER = LogManager.getLogger("XSD ItemValue");

    private static final String ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";
    private static final long REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L;
    private static final long RETRY_INTERVAL_MS = 5 * 60 * 1000L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static volatile Map<String, SkyblockItemInfo> itemData = Map.of();
    /** 子物品 id -> 父物品 id，用于 Kuudra 装备的进阶链估值 */
    private static volatile Map<String, String> itemParents = Map.of();
    private static ScheduledExecutorService executor;

    private NetworthDataManager() {
    }

    public static synchronized void init() {
        if (executor != null) return;

        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "skydiao-item-value-data");
            thread.setDaemon(true);
            return thread;
        });

        scheduleNext(0L);
    }

    public static boolean isReady() {
        return !itemData.isEmpty();
    }

    /**
     * 计算单件物品（含附魔、升级等）的估值。
     *
     * @param stack  物品，必须带 Hypixel 的 custom_data
     * @param rawId  物品内部 id（例如 {@code HYPERION}、{@code ATTRIBUTE_SHARD}）
     * @param apiId  价格 API 使用的 id（例如 {@code ENCHANTMENT_SHARPNESS_7}、{@code SHARD_FLAMING_SPIDER}）
     * @param count  数量，返回值是总价
     * @return 总估值；数据未就绪或计算失败返回 0
     */
    public static double getEstimatedValue(ItemStack stack, String rawId, String apiId, int count) {
        if (rawId == null || rawId.isEmpty() || itemData.isEmpty()) return 0;

        try {
            CompoundTag customData = stack.getComponents()
                    .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                    .copyTag();

            return ItemValueCalculator.calculate(
                    rawId, apiId, Math.max(count, 1), customData,
                    itemData, itemParents, NetworthDataManager::getPrice);
        } catch (Exception e) {
            // 和 Skyblocker 一样：算不出来就不显示，不要影响 tooltip
            return 0;
        }
    }

    /**
     * 和 Skyblocker 的 NetworthDataSuppliers#getPrice 一致：
     * Bazaar 买价优先，其次 3 天均价，最后 Lowest BIN。
     */
    private static double getPrice(String skyblockId) {
        Double price = PriceDataManager.getPrice(skyblockId, true, true);
        return price == null ? 0 : price;
    }

    private static void scheduleNext(long delayMs) {
        executor.schedule(() -> {
            boolean success = refresh();
            scheduleNext(success ? REFRESH_INTERVAL_MS : RETRY_INTERVAL_MS);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private static boolean refresh() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ITEMS_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "XiaoShadiao-Skydiao")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                LOGGER.warn("物品数据请求失败: HTTP {}", response.statusCode());
                FetchFailureNotifier.recordFailure(ITEMS_URL);
                return false;
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray items = root.getAsJsonArray("items");
            if (items == null) {
                LOGGER.warn("物品数据响应缺少 items 字段");
                FetchFailureNotifier.recordFailure(ITEMS_URL);
                return false;
            }

            Map<String, String> parents = new HashMap<>();
            Map<String, SkyblockItemInfo> parsed = SkyblockItemInfo.parseAll(items, parents);

            boolean firstLoad = itemData.isEmpty();
            itemData = parsed;
            itemParents = Map.copyOf(parents);
            FetchFailureNotifier.recordSuccess(ITEMS_URL);

            if (firstLoad) {
                LOGGER.info("物品基础数据已加载: {} 个物品, {} 条进阶关系", parsed.size(), parents.size());
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("物品数据拉取失败", e);
            FetchFailureNotifier.recordFailure(ITEMS_URL);
            return false;
        }
    }
}
