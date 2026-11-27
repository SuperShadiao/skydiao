package pers.XiaoShadiao.skydiao.utils.itemprice;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.XiaoShadiao.skydiao.utils.FetchFailureNotifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 价格数据管理：从 skb.xiaoshadiao.club 拉取 Bazaar / Lowest BIN / 1、3 天均价。
 *
 * <p>数据在后台线程定时刷新，tooltip 渲染时只做内存查表，不要在这里的 getter 里发请求。</p>
 */
public final class PriceDataManager {

    private static final Logger LOGGER = LogManager.getLogger("XSD PriceData");

    private static final String BASE_URL = "https://skb.xiaoshadiao.club";
    private static final String BAZAAR_URL = BASE_URL + "/api/bazaar";
    private static final String LOWEST_BINS_URL = BASE_URL + "/api/auctions/lowestbins";
    private static final String AVERAGE_1D_URL = BASE_URL + "/api/auctions/lowestbins/average?days=1";
    private static final String AVERAGE_3D_URL = BASE_URL + "/api/auctions/lowestbins/average?days=3";

    private static final long BAZAAR_INTERVAL_MS = 60_000L;
    private static final long AUCTION_INTERVAL_MS = 600_000L;
    private static final Duration CONNECT_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    /** endpoint -> ETag */
    private static final Map<String, String> ETAGS = new ConcurrentHashMap<>();

    private static volatile Map<String, BazaarProduct> bazaar = Map.of();
    /** Bazaar 展示名去掉 "Shard " 前缀后的名字（小写） -> 产品 id，用于 Hunting Box 等只有名字的展示物品 */
    private static volatile Map<String, String> shardIdsByDisplayName = Map.of();
    private static volatile long bazaarUpdatedAt;

    private static volatile Map<String, Double> lowestBins = Map.of();
    private static volatile long lowestBinsUpdatedAt;

    private static volatile Map<String, Double> average1Day = Map.of();
    private static volatile Map<String, Double> average3Day = Map.of();
    private static volatile long averageUpdatedAt;

    private static ScheduledExecutorService executor;

    private PriceDataManager() {
    }

    /**
     * Bazaar 产品聚合。价格字段可能为 null（该侧没有挂单），使用前务必判空。
     */
    public record BazaarProduct(
            String name,
            Double bestAsk,
            Double bestBid,
            Double weightedBuyPrice,
            Double weightedSellPrice,
            long buyVolume,
            long sellVolume,
            int buyOrders,
            int sellOrders
    ) {
    }

    public static synchronized void init() {
        if (executor != null) return;

        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "skydiao-price-data");
            thread.setDaemon(true);
            return thread;
        });

        executor.execute(() -> runSafely(PriceDataManager::refreshAll));
        executor.scheduleWithFixedDelay(
                () -> runSafely(PriceDataManager::refreshBazaar),
                BAZAAR_INTERVAL_MS, BAZAAR_INTERVAL_MS, TimeUnit.MILLISECONDS);
        executor.scheduleWithFixedDelay(
                () -> runSafely(PriceDataManager::refreshAuctions),
                AUCTION_INTERVAL_MS, AUCTION_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    // ------------------------------------------------------------------
    // 查询接口
    // ------------------------------------------------------------------

    public static BazaarProduct getBazaarProduct(String apiId) {
        if (apiId == null || apiId.isEmpty()) return null;
        return bazaar.get(apiId);
    }

    public static Double getLowestBin(String apiId) {
        if (apiId == null || apiId.isEmpty()) return null;
        return lowestBins.get(apiId);
    }

    public static Double getAverage1Day(String apiId) {
        if (apiId == null || apiId.isEmpty()) return null;
        return average1Day.get(apiId);
    }

    public static Double getAverage3Day(String apiId) {
        if (apiId == null || apiId.isEmpty()) return null;
        return average3Day.get(apiId);
    }

    /**
     * 用展示名反查 Bazaar 的 shard 产品 id，供 Hunting Box 这类只渲染名字、没有 custom_data 的菜单物品使用。
     *
     * @param displayName 物品显示名，例如 {@code Flaming Spider}（可带 {@code Shard} 后缀）
     * @return 产品 id，例如 {@code SHARD_FLAMING_SPIDER}；没有对应产品时返回 null
     */
    public static String getShardBazaarIdByDisplayName(String displayName) {
        if (displayName == null || displayName.isEmpty()) return null;

        String key = displayName.toLowerCase(Locale.ENGLISH);
        String id = shardIdsByDisplayName.get(key);
        if (id != null) return id;

        if (key.endsWith(" shard")) {
            return shardIdsByDisplayName.get(key.substring(0, key.length() - " shard".length()).trim());
        }
        return null;
    }

    /**
     * 统一取价链，和 Skyblocker 的 ItemUtils#getItemPrice 对齐：
     * Bazaar 优先（默认取 bestBid），然后可选 3 天均价，最后 Lowest BIN。
     *
     * @param useBazaarBuyPrice true 时取 weightedBuyPrice，否则取 bestBid
     * @param useAuctionAverage true 时在回退到 Lowest BIN 前先尝试 3 天均价
     */
    public static Double getPrice(String apiId, boolean useBazaarBuyPrice, boolean useAuctionAverage) {
        if (apiId == null || apiId.isEmpty()) return null;

        BazaarProduct product = bazaar.get(apiId);
        if (product != null) {
            return useBazaarBuyPrice ? product.weightedBuyPrice() : product.bestBid();
        }

        if (useAuctionAverage) {
            Double average = average3Day.get(apiId);
            if (average != null) return average;
        }

        return lowestBins.get(apiId);
    }

    public static boolean isBazaarReady() {
        return !bazaar.isEmpty();
    }

    public static boolean isAuctionDataReady() {
        return !lowestBins.isEmpty();
    }

    public static long getBazaarUpdatedAt() {
        return bazaarUpdatedAt;
    }

    public static long getLowestBinsUpdatedAt() {
        return lowestBinsUpdatedAt;
    }

    public static long getAverageUpdatedAt() {
        return averageUpdatedAt;
    }

    // ------------------------------------------------------------------
    // 刷新
    // ------------------------------------------------------------------

    private static void refreshAll() {
        refreshBazaar();
        refreshAuctions();
    }

    private static void refreshBazaar() {
        JsonObject root = getJson(BAZAAR_URL);
        if (root == null) return;

        JsonObject productsObject = root.getAsJsonObject("products");
        if (productsObject == null) {
            LOGGER.warn("Bazaar 响应缺少 products 字段");
            return;
        }

        Map<String, BazaarProduct> parsed = new HashMap<>();
        Map<String, String> parsedShardNames = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : productsObject.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject object = entry.getValue().getAsJsonObject();
            String productId = entry.getKey();
            String productName = stringOr(object, "name", "");

            parsed.put(productId, new BazaarProduct(
                    productName,
                    doubleOrNull(object, "bestAsk"),
                    doubleOrNull(object, "bestBid"),
                    doubleOrNull(object, "weightedBuyPrice"),
                    doubleOrNull(object, "weightedSellPrice"),
                    longOr(object, "buyVolume", 0L),
                    longOr(object, "sellVolume", 0L),
                    intOr(object, "buyOrders", 0),
                    intOr(object, "sellOrders", 0)
            ));

            // "Shard Flaming Spider" -> "flaming spider" -> SHARD_FLAMING_SPIDER
            if (productId.startsWith("SHARD_") && productName.startsWith("Shard ")) {
                parsedShardNames.put(productName.substring("Shard ".length()).toLowerCase(Locale.ENGLISH), productId);
            }
        }

        boolean firstLoad = bazaar.isEmpty();
        bazaar = Map.copyOf(parsed);
        shardIdsByDisplayName = Map.copyOf(parsedShardNames);
        bazaarUpdatedAt = longOr(root, "updatedAt", System.currentTimeMillis());

        if (firstLoad) {
            LOGGER.info("Bazaar 价格已加载: {} 个物品", parsed.size());
        }
    }

    private static void refreshAuctions() {
        refreshLowestBins();

        Map<String, Double> oneDay = fetchPriceMap(AVERAGE_1D_URL, "items");
        Map<String, Double> threeDay = fetchPriceMap(AVERAGE_3D_URL, "items");

        boolean refreshed = false;
        if (oneDay != null) {
            average1Day = Map.copyOf(oneDay);
            refreshed = true;
        }
        if (threeDay != null) {
            average3Day = Map.copyOf(threeDay);
            refreshed = true;
        }
        if (refreshed) {
            averageUpdatedAt = System.currentTimeMillis();
        }
    }

    private static void refreshLowestBins() {
        JsonObject root = getJson(LOWEST_BINS_URL);
        if (root == null) return;

        Map<String, Double> parsed = parsePriceMap(root, "items");
        if (parsed == null) {
            LOGGER.warn("Lowest BIN 响应缺少 items 字段");
            return;
        }

        boolean firstLoad = lowestBins.isEmpty();
        lowestBins = Map.copyOf(parsed);
        lowestBinsUpdatedAt = longOr(root, "updatedAt", System.currentTimeMillis());

        if (firstLoad) {
            LOGGER.info("Lowest BIN 已加载: {} 个物品", parsed.size());
        }
    }

    private static Map<String, Double> fetchPriceMap(String url, String field) {
        JsonObject root = getJson(url);
        if (root == null) return null;
        return parsePriceMap(root, field);
    }

    // ------------------------------------------------------------------
    // JSON / HTTP
    // ------------------------------------------------------------------

    /**
     * 解析 { "items": { "ID": 123.0 } }，null 或非数字价格会被跳过。
     */
    private static Map<String, Double> parsePriceMap(JsonObject root, String field) {
        JsonElement itemsElement = root.get(field);
        if (itemsElement == null || !itemsElement.isJsonObject()) return null;

        Map<String, Double> parsed = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : itemsElement.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) continue;
            try {
                parsed.put(entry.getKey(), value.getAsDouble());
            } catch (Exception ignored) {
                // 忽略单条脏数据，不影响整体刷新
            }
        }
        return parsed;
    }

    /**
     * 带 ETag 的 GET。返回 null 表示 304、请求失败或数据未就绪，调用方保留旧数据即可。
     */
    private static JsonObject getJson(String url) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("User-Agent", "XiaoShadiao-Skydiao")
                    .GET();

            String etag = ETAGS.get(url);
            if (etag != null) builder.header("If-None-Match", etag);

            HttpResponse<String> response = HTTP_CLIENT.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 304) {
                // 304 说明接口正常，只是数据没变
                FetchFailureNotifier.recordSuccess(url);
                return null;
            }

            if (response.statusCode() != 200) {
                FetchFailureNotifier.recordFailure(url);
                LOGGER.warn("请求 {} 失败: HTTP {}", url, response.statusCode());
                return null;
            }

            response.headers().firstValue("ETag").ifPresent(value -> ETAGS.put(url, value));
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            FetchFailureNotifier.recordSuccess(url);
            return root;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            FetchFailureNotifier.recordFailure(url);
            LOGGER.warn("请求 {} 失败", url, e);
            return null;
        }
    }

    private static void runSafely(Runnable task) {
        try {
            task.run();
        } catch (Throwable throwable) {
            LOGGER.warn("价格数据刷新任务异常", throwable);
        }
    }

    private static String stringOr(JsonObject object, String key, String defaultValue) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? defaultValue : element.getAsString();
    }

    private static Double doubleOrNull(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsDouble();
    }

    private static long longOr(JsonObject object, String key, long defaultValue) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? defaultValue : element.getAsLong();
    }

    private static int intOr(JsonObject object, String key, int defaultValue) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? defaultValue : element.getAsInt();
    }
}
