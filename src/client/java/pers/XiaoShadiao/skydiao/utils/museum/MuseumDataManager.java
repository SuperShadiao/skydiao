package pers.XiaoShadiao.skydiao.utils.museum;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.XiaoShadiao.skydiao.utils.FetchFailureNotifier;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 博物馆数据管理：从 skb.xiaoshadiao.club 拉取可捐赠物品目录与当前 profile 的已捐赠集合。
 *
 * <p>数据在后台线程定时刷新，tooltip 渲染时只做内存查表，不要在 getter 里发请求。</p>
 */
public final class MuseumDataManager {

    private static final Logger LOGGER = LogManager.getLogger("XSD Museum");

    private static final String BASE_URL = "https://skb.xiaoshadiao.club";
    private static final String CATALOG_URL = BASE_URL + "/api/museum/catalog";
    private static final String PROFILE_URL = BASE_URL + "/api/museum/profile?uuid=";

    private static final long CATALOG_INTERVAL_MS = 60 * 60 * 1000L;
    private static final long PROFILE_INTERVAL_MS = 5 * 60 * 1000L;
    private static final Duration CONNECT_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    /** endpoint -> ETag */
    private static final Map<String, String> ETAGS = new ConcurrentHashMap<>();

    private static volatile Set<String> eligibleIds = Set.of();
    private static volatile Map<String, String> mappedIds = Map.of();
    private static volatile String catalogVersion = "";

    private static volatile Set<String> collectedIds = Set.of();
    private static volatile String profileId = "";
    private static volatile boolean profileReady;
    private static volatile boolean apiDisabled;
    private static volatile long profileUpdatedAt;

    private static ScheduledExecutorService executor;

    private MuseumDataManager() {
    }

    public static synchronized void init() {
        if (executor != null) return;

        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "skydiao-museum-data");
            thread.setDaemon(true);
            return thread;
        });

        executor.execute(() -> runSafely(MuseumDataManager::refreshAll));
        executor.scheduleWithFixedDelay(
                () -> runSafely(MuseumDataManager::refreshCatalog),
                CATALOG_INTERVAL_MS, CATALOG_INTERVAL_MS, TimeUnit.MILLISECONDS);
        executor.scheduleWithFixedDelay(
                () -> runSafely(MuseumDataManager::refreshProfile),
                PROFILE_INTERVAL_MS, PROFILE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    // ------------------------------------------------------------------
    // 查询接口
    // ------------------------------------------------------------------

    /**
     * 该物品 id 是否属于可捐赠物品（套装 id、套装散件、升级链两端等）。
     */
    public static boolean isEligible(String itemId) {
        if (itemId == null || itemId.isEmpty()) return false;

        Set<String> eligible = eligibleIds;
        if (eligible.isEmpty()) return false;
        if (eligible.contains(itemId)) return true;

        String resolved = resolveId(itemId);
        return resolved != null && eligible.contains(resolved);
    }

    /**
     * 该物品 id 是否已捐赠到博物馆。
     *
     * @return {@code null} 表示数据未就绪（此时不要展示任何结论）
     */
    public static Boolean isDonated(String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;
        if (!profileReady || apiDisabled) return null;

        Set<String> collected = collectedIds;
        if (collected.contains(itemId)) return Boolean.TRUE;

        String resolved = resolveId(itemId);
        return resolved != null && collected.contains(resolved);
    }

    public static boolean isProfileReady() {
        return profileReady && !apiDisabled;
    }

    public static String getCatalogVersion() {
        return catalogVersion;
    }

    public static String getProfileId() {
        return profileId;
    }

    public static long getProfileUpdatedAt() {
        return profileUpdatedAt;
    }

    // ------------------------------------------------------------------
    // 刷新
    // ------------------------------------------------------------------

    private static void refreshAll() {
        refreshCatalog();
        refreshProfile();
    }

    private static void refreshCatalog() {
        JsonObject root = getJson(CATALOG_URL);
        if (root == null) return;

        JsonArray eligibleArray = root.getAsJsonArray("museumEligibleIds");
        if (eligibleArray == null) {
            LOGGER.warn("博物馆目录缺少 museumEligibleIds 字段");
            return;
        }

        Set<String> parsedEligible = new HashSet<>();
        for (JsonElement element : eligibleArray) {
            if (element != null && element.isJsonPrimitive()) {
                parsedEligible.add(element.getAsString());
            }
        }

        Map<String, String> parsedMapped = new HashMap<>();
        JsonObject mappedObject = root.getAsJsonObject("mappedIds");
        if (mappedObject != null) {
            for (Map.Entry<String, JsonElement> entry : mappedObject.entrySet()) {
                if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                    parsedMapped.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }

        boolean firstLoad = eligibleIds.isEmpty();
        eligibleIds = Set.copyOf(parsedEligible);
        mappedIds = Map.copyOf(parsedMapped);
        catalogVersion = stringOr(root, "version", catalogVersion);

        if (firstLoad) {
            LOGGER.info("博物馆目录已加载: {} 个可捐赠 id, 版本 {}", parsedEligible.size(), catalogVersion);
        }
    }

    private static void refreshProfile() {
        String uuid = currentUuid();
        if (uuid == null) return;

        JsonObject root = getJson(PROFILE_URL + uuid);
        if (root == null) return;

        JsonArray collectedArray = root.getAsJsonArray("collectedItemIds");
        if (collectedArray == null) {
            LOGGER.warn("博物馆 profile 响应缺少 collectedItemIds 字段");
            return;
        }

        Set<String> parsedCollected = new HashSet<>();
        for (JsonElement element : collectedArray) {
            if (element != null && element.isJsonPrimitive()) {
                parsedCollected.add(element.getAsString());
            }
        }

        boolean firstLoad = collectedIds.isEmpty();
        collectedIds = Set.copyOf(parsedCollected);
        profileId = stringOr(root, "profileId", profileId);
        apiDisabled = booleanOr(root, "apiDisabled", false);
        profileReady = true;
        profileUpdatedAt = longOr(root, "fetchedAt", System.currentTimeMillis());

        if (firstLoad || parsedCollected.isEmpty()) {
            LOGGER.info("博物馆 profile 已加载: profile={} 捐赠 {} 个 id, apiDisabled={}",
                    profileId, parsedCollected.size(), apiDisabled);
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static String currentUuid() {
        try {
            UUID uuid = ToolList.mc.getUser().getProfileId();
            return uuid == null ? null : uuid.toString().replace("-", "");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 原始物品 id -> 博物馆目录使用的 id。优先 mappedIds，其次去掉 STARRED_ 前缀。
     */
    private static String resolveId(String itemId) {
        String mapped = mappedIds.get(itemId);
        if (mapped != null) return mapped;
        if (itemId.startsWith("STARRED_")) return itemId.substring("STARRED_".length());
        return itemId;
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
            if (!booleanOr(root, "success", true)) {
                FetchFailureNotifier.recordFailure(url);
                LOGGER.warn("请求 {} 返回 success=false", url);
                return null;
            }
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
            LOGGER.warn("博物馆数据刷新任务异常", throwable);
        }
    }

    private static String stringOr(JsonObject object, String key, String defaultValue) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? defaultValue : element.getAsString();
    }

    private static boolean booleanOr(JsonObject object, String key, boolean defaultValue) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? defaultValue : element.getAsBoolean();
    }

    private static long longOr(JsonObject object, String key, long defaultValue) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? defaultValue : element.getAsLong();
    }
}
