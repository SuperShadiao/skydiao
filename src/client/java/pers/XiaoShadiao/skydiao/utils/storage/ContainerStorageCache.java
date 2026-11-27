package pers.XiaoShadiao.skydiao.utils.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 缓存玩家打开过的末影箱/背包内容。
 *
 * <p>Hypixel 不提供背包/末影箱 API，所以只有打开过一次的页才有数据。
 * 未缓存的页在预览界面显示“点击加载”，点击后发送对应指令打开容器。
 */
public final class ContainerStorageCache {

    public static final ContainerStorageCache INSTANCE = new ContainerStorageCache();

    private static final Logger LOGGER = LogManager.getLogger("XSD ContainerPreview");
    private static final Pattern ENDER_CHEST_TITLE =
            Pattern.compile("^Ender Chest\\s*(?:✦\\s*)?\\((\\d+)/(\\d+)\\)$");
    private static final Pattern BACKPACK_TITLE =
            Pattern.compile("^.+Backpack\\s*(?:✦\\s*)?\\(Slot #(\\d+)\\)$");

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("skydiao/storage-preview");

    private final Map<String, CachedPage> pages = new HashMap<>();
    /** 页面 key -> 是否已解锁；没有记录的页面按未知处理（保持可点击）。 */
    private final Map<String, Boolean> availability = new HashMap<>();
    private String playerId;
    private String profileId;
    private Path loadedFile;
    private volatile boolean loaded;
    private volatile boolean dirty;
    private long lastSaveTime;

    private ContainerStorageCache() {
    }

    public record CachedPage(int rows, List<ItemStack> items) {
        public CachedPage {
            items = List.copyOf(items);
        }

        public ItemStack item(int slot) {
            return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
        }
    }

    public CachedPage page(ContainerPage page) {
        return pages.get(page.key());
    }

    /**
     * 由聊天栏的 {@code Profile ID: <uuid>} 调用。
     * 缓存文件按 玩家 UUID / Profile ID 分开，避免切 profile 后看到别的存档容器。
     */
    public void setProfileId(String newProfileId) {
        if (newProfileId == null || newProfileId.isBlank()) {
            return;
        }
        // 先同步玩家 UUID，避免 Profile ID 先于首次 tick 到达时落到 unknown-player 目录。
        ensureIdentity();

        String normalized = newProfileId.toLowerCase(Locale.ROOT);
        if (normalized.equals(profileId)) {
            return;
        }

        // 切换存档前先把当前存档里还没落盘的页面写回旧文件。
        if (dirty && ToolList.mc.level != null) {
            save();
        }

        boolean previousUnknown = profileId == null || "unknown".equals(profileId);
        Map<String, CachedPage> previousPages = previousUnknown ? Map.copyOf(pages) : Map.of();
        Map<String, Boolean> previousAvailability =
                previousUnknown ? Map.copyOf(availability) : Map.of();

        profileId = normalized;
        pages.clear();
        availability.clear();
        loaded = false;
        loadedFile = currentFile();
        dirty = false;
        ensureLoaded();

        // 聊天栏 Profile ID 到达前可能已经缓存过页面，迁移到新 profile 文件。
        if (!previousPages.isEmpty()) {
            previousPages.forEach(pages::putIfAbsent);
            dirty = true;
        }
        if (!previousAvailability.isEmpty()) {
            previousAvailability.forEach(availability::putIfAbsent);
            dirty = true;
        }
    }

    public void ensureLoaded() {
        ensureIdentity();
        if (loaded || ToolList.mc.level == null) {
            return;
        }
        Path file = currentFile();
        if (!file.equals(loadedFile)) {
            pages.clear();
            availability.clear();
            loadedFile = file;
        }
        if (!Files.exists(file)) {
            loaded = true;
            LOGGER.info("容器预览缓存文件不存在，使用空缓存: {}", file);
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonObject pageObject = root.getAsJsonObject("pages");
            if (pageObject != null) {
                for (Map.Entry<String, JsonElement> entry : pageObject.entrySet()) {
                    try {
                        JsonObject pageJson = entry.getValue().getAsJsonObject();
                        int rows = pageJson.get("rows").getAsInt();
                        if (rows <= 0 || rows > 6) {
                            continue;
                        }
                        List<ItemStack> items = new ArrayList<>(rows * 9);
                        for (int i = 0; i < rows * 9; i++) {
                            items.add(ItemStack.EMPTY);
                        }
                        JsonArray itemArray = pageJson.getAsJsonArray("items");
                        if (itemArray != null) {
                            for (JsonElement itemElement : itemArray) {
                                JsonObject itemJson = itemElement.getAsJsonObject();
                                int slot = itemJson.get("slot").getAsInt();
                                if (slot < 0 || slot >= items.size()) {
                                    continue;
                                }
                                ItemStack stack = decodeStack(itemJson.get("data").getAsString());
                                if (!stack.isEmpty()) {
                                    items.set(slot, stack);
                                }
                            }
                        }
                        pages.put(entry.getKey(), new CachedPage(rows, items));
                    } catch (Exception exception) {
                        LOGGER.warn("读取存储页 {} 失败", entry.getKey(), exception);
                    }
                }
            }
            JsonObject availabilityObject = root.getAsJsonObject("availability");
            if (availabilityObject != null) {
                availability.clear();
                for (Map.Entry<String, JsonElement> entry : availabilityObject.entrySet()) {
                    availability.put(entry.getKey(), entry.getValue().getAsBoolean());
                }
            }
            // 有缓存说明这一页曾经成功打开过，必然是已解锁的。
            for (String key : pages.keySet()) {
                availability.put(key, true);
            }
            // 全 false 只可能是内容包到达前误扫出来的结果，直接丢弃。
            if (!availability.isEmpty() && !availability.containsValue(true)) {
                availability.clear();
            }
            loaded = true;
            LOGGER.info("已加载 {} 个容器预览页: {}", pages.size(), file);
        } catch (Exception exception) {
            loaded = true;
            LOGGER.warn("读取容器预览缓存失败: {}", file, exception);
        }
    }

    private void ensureIdentity() {
        String currentPlayer = currentPlayerId();
        if (!Objects.equals(playerId, currentPlayer)) {
            playerId = currentPlayer;
            profileId = null;
            pages.clear();
            availability.clear();
            loaded = false;
            loadedFile = null;
        }
    }

    private static String currentPlayerId() {
        return ToolList.mc.getUser() == null
                ? null
                : ToolList.mc.getUser().getProfileId().toString();
    }

    private Path currentFile() {
        String player = playerId == null || playerId.isBlank() ? "unknown-player" : playerId;
        String profile = profileId == null || profileId.isBlank() ? "unknown" : profileId;
        return CONFIG_DIR.resolve(player).resolve(profile + ".json");
    }

    /**
     * 每 tick 调用：如果当前打开的是末影箱/背包，就把顶部容器内容复制到缓存。
     */
    public void captureCurrentScreen() {
        ensureLoaded();
        Screen screen = ToolList.mc.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return;
        }
        String rawTitle = container.getTitle().getString();
        if (isStorageOverview(rawTitle)) {
            if (container.getMenu() instanceof ChestMenu menu) {
                captureOverview(menu);
            }
            return;
        }
        ContainerPage page = parsePage(rawTitle);
        if (page == null || !(container.getMenu() instanceof ChestMenu menu)) {
            return;
        }
        observeTitle(rawTitle);
        capture(menu, page);
    }

    /**
     * 扫描 Hypixel 的 Storage 概览页，记录哪些末影箱/背包页已经解锁。
     *
     * <p>概览页槽位布局与 Firmament 一致：9~17 是 9 个末影箱图标，
     * 27~44 是 18 个背包图标；红色/棕色玻璃板和灰色染料表示未解锁。
     */
    public void captureOverview(ChestMenu menu) {
        ensureLoaded();
        if (menu.slots.size() < 45) {
            return;
        }
        int nonEmpty = 0;
        for (int i = 0; i < 9; i++) {
            if (!menu.slots.get(9 + i).getItem().isEmpty()) {
                nonEmpty++;
            }
        }
        for (int i = 0; i < 18; i++) {
            if (!menu.slots.get(27 + i).getItem().isEmpty()) {
                nonEmpty++;
            }
        }
        // 内容包还没到时所有槽位都是空的，此时不能把整页都判成未解锁。
        if (nonEmpty == 0) {
            return;
        }
        boolean changed = false;
        for (int i = 1; i <= 9; i++) {
            changed |= setAvailability(ContainerPage.enderChest(i).key(),
                    isUnlockedIcon(menu.slots.get(8 + i).getItem()));
        }
        for (int i = 1; i <= 18; i++) {
            changed |= setAvailability(ContainerPage.backpack(i).key(),
                    isUnlockedIcon(menu.slots.get(26 + i).getItem()));
        }
        if (changed) {
            dirty = true;
        }
    }

    /**
     * 末影箱页面标题形如 {@code Ender Chest (2/3)}，分母就是已解锁的末影箱数量，
     * 打开任意一页即可推断 1~9 的解锁状态。
     */
    public void observeTitle(String rawTitle) {
        ensureLoaded();
        String title = ToolList.getInstance().deleteColorCode(rawTitle == null ? "" : rawTitle).trim();
        Matcher matcher = ENDER_CHEST_TITLE.matcher(title);
        if (!matcher.matches()) {
            return;
        }
        int total = Integer.parseInt(matcher.group(2));
        if (total < 1 || total > 9) {
            return;
        }
        boolean changed = false;
        for (int i = 1; i <= 9; i++) {
            changed |= setAvailability(ContainerPage.enderChest(i).key(), i <= total);
        }
        if (changed) {
            dirty = true;
        }
    }

    public static boolean isStorageOverview(String rawTitle) {
        return "Storage".equals(
                ToolList.getInstance().deleteColorCode(rawTitle == null ? "" : rawTitle).trim());
    }

    /** 没有记录时按未知处理，保持可点击，避免旧缓存看不到任何页面。 */
    public boolean isAvailable(ContainerPage page) {
        ensureLoaded();
        return availability.getOrDefault(page.key(), true);
    }

    private boolean setAvailability(String key, boolean available) {
        Boolean previous = availability.put(key, available);
        return previous == null || previous != available;
    }

    private static boolean isUnlockedIcon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return !stack.is(Items.STAINED_GLASS_PANE.red())
                && !stack.is(Items.STAINED_GLASS_PANE.brown())
                && !stack.is(Items.DYE.gray());
    }

    /**
     * 复制一页容器的物品内容。
     *
     * <p>Hypixel 末影箱/背包的首行是返回按钮和页信息，实际物品从第二行开始，
     * 与 Firmament 一样跳过首行。
     */
    public void capture(ChestMenu menu, ContainerPage page) {
        ensureLoaded();
        int rows = menu.getRowCount() - 1;
        int slotCount = rows * 9;
        if (rows <= 0 || rows > 6 || menu.slots.size() < 9 + slotCount) {
            return;
        }
        if (setAvailability(page.key(), true)) {
            dirty = true;
        }

        CachedPage existing = pages.get(page.key());
        if (existing != null && existing.rows() == rows && matches(existing, menu)) {
            return;
        }

        List<ItemStack> items = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            items.add(menu.slots.get(9 + i).getItem().copy());
        }
        pages.put(page.key(), new CachedPage(rows, items));
        dirty = true;
    }

    private static boolean matches(CachedPage page, ChestMenu menu) {
        List<ItemStack> items = page.items();
        for (int i = 0; i < items.size(); i++) {
            if (!ItemStack.matches(items.get(i), menu.slots.get(9 + i).getItem())) {
                return false;
            }
        }
        return true;
    }

    public void flushIfDirty() {
        if (!dirty || ToolList.mc.level == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSaveTime < 1_000L) {
            return;
        }
        lastSaveTime = now;
        save();
    }

    private void save() {
        try {
            RegistryOps<Tag> ops = ops();
            JsonObject pageObject = new JsonObject();
            for (Map.Entry<String, CachedPage> entry : pages.entrySet()) {
                CachedPage page = entry.getValue();
                JsonObject pageJson = new JsonObject();
                pageJson.addProperty("rows", page.rows());
                JsonArray itemArray = new JsonArray();
                for (int i = 0; i < page.items().size(); i++) {
                    ItemStack stack = page.items().get(i);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    try {
                        JsonObject itemJson = new JsonObject();
                        itemJson.addProperty("slot", i);
                        itemJson.addProperty("data", encodeStack(stack, ops));
                        itemArray.add(itemJson);
                    } catch (Exception exception) {
                        LOGGER.warn("序列化存储页 {} 的第 {} 个物品失败", entry.getKey(), i, exception);
                    }
                }
                pageJson.add("items", itemArray);
                pageObject.add(entry.getKey(), pageJson);
            }
            JsonObject root = new JsonObject();
            root.add("pages", pageObject);
            if (!availability.isEmpty()) {
                JsonObject availabilityObject = new JsonObject();
                availability.forEach(availabilityObject::addProperty);
                root.add("availability", availabilityObject);
            }

            Path file = currentFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception exception) {
            LOGGER.warn("保存容器预览缓存失败: {}", currentFile(), exception);
        }
    }

    private static String encodeStack(ItemStack stack, RegistryOps<Tag> ops) {
        Tag tag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
        CompoundTag root = new CompoundTag();
        root.put("item", tag);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(root, output);
        } catch (Exception exception) {
            throw new IllegalStateException("写入物品 NBT 失败", exception);
        }
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private static ItemStack decodeStack(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            CompoundTag root = NbtIo.readCompressed(
                    new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            Tag tag = root.get("item");
            if (tag == null) {
                return ItemStack.EMPTY;
            }
            return ItemStack.CODEC.parse(ops(), tag).getOrThrow();
        } catch (Exception exception) {
            LOGGER.warn("读取缓存的物品失败", exception);
            return ItemStack.EMPTY;
        }
    }

    private static RegistryOps<Tag> ops() {
        return RegistryOps.create(NbtOps.INSTANCE, ToolList.mc.level.registryAccess());
    }

    public static ContainerPage parsePage(String rawTitle) {
        String title = ToolList.getInstance().deleteColorCode(rawTitle == null ? "" : rawTitle).trim();
        Matcher enderChest = ENDER_CHEST_TITLE.matcher(title);
        if (enderChest.matches()) {
            int index = Integer.parseInt(enderChest.group(1));
            return index >= 1 && index <= 9 ? ContainerPage.enderChest(index) : null;
        }
        Matcher backpack = BACKPACK_TITLE.matcher(title);
        if (backpack.matches()) {
            int index = Integer.parseInt(backpack.group(1));
            return index >= 1 && index <= 18 ? ContainerPage.backpack(index) : null;
        }
        return null;
    }
}
