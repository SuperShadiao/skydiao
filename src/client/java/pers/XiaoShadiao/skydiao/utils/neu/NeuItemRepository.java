package pers.XiaoShadiao.skydiao.utils.neu;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.MultiThreadDownloader;
import pers.XiaoShadiao.skydiao.utils.text.LegacyText;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * NEU 物品数据仓库。
 *
 * <p>ZIP 由后端托管并支持 HTTP Range 分段下载；下载完成后直接读取 ZIP 内的
 * {@code items.json} 建内存索引，不把 ZIP 解压到磁盘。
 */
public final class NeuItemRepository {

    public static final String BASE_URL = "https://skb.xiaoshadiao.club";
    public static final NeuItemRepository INSTANCE = new NeuItemRepository();

    private static final Logger log = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new Gson();
    private static final String USER_AGENT = "skydiao-neu-repository/1.0";
    private static final Pattern OVERLAY_DYED_COLOR =
            Pattern.compile("\"?minecraft:dyed_color\"?\\s*:\\s*(-?\\d+)");

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Skydiao-NEU");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Map<String, NeuItem> items = Map.of();
    private volatile List<Recipe> recipes = List.of();
    private volatile Map<String, List<Recipe>> recipesByOutput = Map.of();
    private volatile Map<String, List<Recipe>> recipesByInput = Map.of();
    private volatile List<MobDropRecipe> mobDropRecipes = List.of();
    private volatile Map<String, List<MobDropRecipe>> mobDropsByMob = Map.of();
    private volatile Map<String, List<MobDropRecipe>> mobDropsByDrop = Map.of();
    private volatile Map<String, Integer> overlayColors = Map.of();
    private volatile Map<String, List<String>> parents = Map.of();
    private volatile Map<String, Map<String, PetNum>> petNumbers = Map.of();
    private volatile List<Shard> shards = List.of();
    private volatile Map<String, String> shardByKey = Map.of();
    private volatile Manifest manifest;
    private volatile CompletableFuture<Void> refreshFuture;
    private final List<Runnable> loadListeners = new CopyOnWriteArrayList<>();

    private NeuItemRepository() {
    }

    public static Path zipPath() {
        return ConfigManager.config_folder.toPath().resolve("neuItems.zip");
    }

    public static Path manifestPath() {
        return ConfigManager.config_folder.toPath().resolve("neuItems.manifest.json");
    }

    private static Path tempPath() {
        return ConfigManager.config_folder.toPath().resolve("neuItems.zip.tmp");
    }

    public int itemCount() {
        return items.size();
    }

    public boolean isLoaded() {
        return !items.isEmpty();
    }

    public String version() {
        Manifest current = manifest;
        return current == null || current.version() == null ? "" : current.version();
    }

    public Manifest manifest() {
        return manifest;
    }

    public CompletableFuture<Void> refreshAsync(boolean force) {
        synchronized (this) {
            if (refreshFuture != null && !refreshFuture.isDone()) {
                return refreshFuture;
            }
            refreshFuture = CompletableFuture.runAsync(() -> {
                try {
                    doRefresh(force);
                } catch (Throwable throwable) {
                    throw new CompletionException(throwable);
                }
            }, executor);
            return refreshFuture;
        }
    }

    private void doRefresh(boolean force) throws Exception {
        Manifest remote = fetchManifest();
        Path zip = zipPath();
        if (!force && Files.exists(zip) && remote.sha256() != null
                && remote.sha256().equalsIgnoreCase(sha256(zip))) {
            manifest = remote;
            loadLocal();
            saveManifest(remote);
            return;
        }

        Files.createDirectories(zip.getParent());
        Path temp = tempPath();
        Files.deleteIfExists(temp);

        String downloadUrl = remote.url();
        if (downloadUrl == null || downloadUrl.isBlank()) {
            downloadUrl = BASE_URL + "/api/neu/items.zip";
        } else if (downloadUrl.startsWith("/")) {
            downloadUrl = BASE_URL + downloadUrl;
        }

        MultiThreadDownloader.download(downloadUrl, temp.toFile(), remote.md5(), Map.of("User-Agent", USER_AGENT))
                .addToTitle()
                .waitOrThrowExceptionIfFailed();

        String actualSha256 = sha256(temp);
        if (remote.sha256() != null && !remote.sha256().equalsIgnoreCase(actualSha256)) {
            Files.deleteIfExists(temp);
            throw new IOException("NEU ZIP SHA-256 不匹配: " + actualSha256);
        }

        try {
            Files.move(temp, zip, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, zip, StandardCopyOption.REPLACE_EXISTING);
        }

        manifest = remote;
        saveManifest(remote);
        loadLocal();
        log.info("NEU 数据更新完成: {} 项, 版本 {}", itemCount(), remote.version());
    }

    public void ensureLoaded() {
        if (isLoaded()) {
            return;
        }
        try {
            loadLocal();
        } catch (IOException e) {
            log.warn("NEU 数据加载失败", e);
        }
    }

    public void loadLocal() throws IOException {
        Path zip = zipPath();
        if (!Files.exists(zip)) {
            items = Map.of();
            recipes = List.of();
            recipesByOutput = Map.of();
            recipesByInput = Map.of();
            mobDropRecipes = List.of();
            mobDropsByMob = Map.of();
            mobDropsByDrop = Map.of();
            overlayColors = Map.of();
            parents = Map.of();
            petNumbers = Map.of();
            shards = List.of();
            shardByKey = Map.of();
            manifest = null;
            return;
        }

        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            JsonObject root = readJson(zipFile, "items.json");
            if (root == null) {
                throw new IOException("NEU ZIP 缺少 items.json");
            }

            JsonObject itemsObject = root.getAsJsonObject("items");
            if (itemsObject == null) {
                throw new IOException("items.json 缺少 items 字段");
            }

            Map<String, NeuItem> loaded = new HashMap<>(itemsObject.size());
            for (Map.Entry<String, JsonElement> entry : itemsObject.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    loaded.put(entry.getKey(), NeuItem.from(entry.getValue().getAsJsonObject()));
                }
            }
            items = Map.copyOf(loaded);

            loadRecipes(zipFile);
            loadOverlayColors(zipFile);
            loadParents(zipFile);
            loadPetNumbers(zipFile);
            loadShards(zipFile);

            JsonObject manifestRoot = readJson(zipFile, "manifest.json");
            if (manifestRoot != null) {
                manifest = Manifest.from(manifestRoot);
            }
        }

        log.info("NEU 数据已加载: {} 物品, {} 配方, {} 怪物掉落, {} 覆盖层颜色, {} 折叠组, {} 宠物, {} 碎片",
                items.size(), recipes.size(), mobDropRecipes.size(), overlayColors.size(),
                parents.size(), petNumbers.size(), shards.size());
        notifyLoadListeners();
    }

    private static JsonObject readJson(ZipFile zipFile, String name) throws IOException {
        ZipEntry entry = zipFile.getEntry(name);
        if (entry == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        }
    }

    private void loadRecipes(ZipFile zipFile) throws IOException {
        JsonObject root = readJson(zipFile, "recipes.json");
        if (root == null) {
            recipes = List.of();
            recipesByOutput = Map.of();
            recipesByInput = Map.of();
            mobDropRecipes = List.of();
            mobDropsByMob = Map.of();
            mobDropsByDrop = Map.of();
            return;
        }

        JsonArray array = root.getAsJsonArray("recipes");
        List<Recipe> loaded = new ArrayList<>(array == null ? 0 : array.size());
        if (array != null) {
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    loaded.add(Recipe.from(element.getAsJsonObject()));
                }
            }
        }
        recipes = List.copyOf(loaded);

        Map<String, List<Recipe>> byOutput = new HashMap<>();
        Map<String, List<Recipe>> byInput = new HashMap<>();
        for (Recipe recipe : recipes) {
            String output = baseId(recipe.output());
            if (!output.isEmpty()) {
                byOutput.computeIfAbsent(output, key -> new ArrayList<>()).add(recipe);
            }
            Set<String> seenInputs = new HashSet<>();
            for (String token : recipe.inputTokens()) {
                String input = baseId(token);
                if (!input.isEmpty() && seenInputs.add(input)) {
                    byInput.computeIfAbsent(input, key -> new ArrayList<>()).add(recipe);
                }
            }
        }
        recipesByOutput = Map.copyOf(byOutput);
        recipesByInput = Map.copyOf(byInput);

        List<MobDropRecipe> loadedMobDrops = new ArrayList<>();
        Map<String, List<MobDropRecipe>> byMob = new HashMap<>();
        Map<String, List<MobDropRecipe>> byDrop = new HashMap<>();
        for (Recipe recipe : recipes) {
            if (!"drops".equals(recipe.type())) {
                continue;
            }
            MobDropRecipe mobDrop = MobDropRecipe.from(recipe);
            loadedMobDrops.add(mobDrop);
            addUnique(byMob, baseId(mobDrop.sourceItem()), mobDrop);
            addUnique(byMob, baseId(mobDrop.mob()), mobDrop);
            Set<String> seenDrops = new HashSet<>();
            for (MobDrop drop : mobDrop.drops()) {
                String dropId = baseId(drop.id());
                if (!dropId.isEmpty() && seenDrops.add(dropId)) {
                    addUnique(byDrop, dropId, mobDrop);
                }
            }
        }
        mobDropRecipes = List.copyOf(loadedMobDrops);
        mobDropsByMob = freezeListMap(byMob);
        mobDropsByDrop = freezeListMap(byDrop);
    }

    private static void addUnique(Map<String, List<MobDropRecipe>> map, String key, MobDropRecipe value) {
        if (key == null || key.isEmpty()) {
            return;
        }
        List<MobDropRecipe> list = map.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    private static Map<String, List<MobDropRecipe>> freezeListMap(Map<String, List<MobDropRecipe>> map) {
        Map<String, List<MobDropRecipe>> copy = new HashMap<>(map.size());
        map.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }

    private void loadOverlayColors(ZipFile zipFile) {
        Map<String, ZipEntry> latestEntries = new HashMap<>();
        Map<String, Integer> latestVersions = new HashMap<>();

        var entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith("overlays/") || !name.endsWith(".snbt")) {
                continue;
            }
            String[] parts = name.split("/");
            if (parts.length < 3) {
                continue;
            }
            String id = parts[parts.length - 1];
            id = id.substring(0, id.length() - ".snbt".length());
            int version;
            try {
                version = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                version = 0;
            }
            Integer current = latestVersions.get(id);
            if (current == null || version > current) {
                latestVersions.put(id, version);
                latestEntries.put(id, entry);
            }
        }

        Map<String, Integer> loaded = new HashMap<>();
        for (Map.Entry<String, ZipEntry> entry : latestEntries.entrySet()) {
            NeuItem item = items.get(entry.getKey());
            if (item == null || !isLeatherArmorItemId(item.itemId())) {
                continue;
            }
            try (InputStream input = zipFile.getInputStream(entry.getValue())) {
                String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                Matcher matcher = OVERLAY_DYED_COLOR.matcher(text);
                if (matcher.find()) {
                    loaded.put(entry.getKey(), Integer.parseInt(matcher.group(1)));
                }
            } catch (Exception ignored) {
            }
        }
        overlayColors = Map.copyOf(loaded);
    }

    private static boolean isLeatherArmorItemId(String itemId) {
        if (itemId == null) {
            return false;
        }
        return switch (itemId) {
            case "minecraft:leather_helmet", "minecraft:leather_chestplate",
                    "minecraft:leather_leggings", "minecraft:leather_boots" -> true;
            default -> false;
        };
    }

    private void loadParents(ZipFile zipFile) throws IOException {
        JsonObject root = readJson(zipFile, "parents.json");
        if (root == null) {
            parents = Map.of();
            return;
        }
        Map<String, List<String>> loaded = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonArray()) {
                continue;
            }
            List<String> children = new ArrayList<>();
            for (JsonElement child : value.getAsJsonArray()) {
                if (child.isJsonPrimitive()) {
                    children.add(child.getAsString());
                }
            }
            if (!children.isEmpty()) {
                loaded.put(entry.getKey(), List.copyOf(children));
            }
        }
        parents = Map.copyOf(loaded);
    }

    private void loadPetNumbers(ZipFile zipFile) throws IOException {
        JsonObject root = readJson(zipFile, "petnums.json");
        if (root == null) {
            petNumbers = Map.of();
            return;
        }
        Map<String, Map<String, PetNum>> loaded = new HashMap<>();
        for (Map.Entry<String, JsonElement> petEntry : root.entrySet()) {
            if (!petEntry.getValue().isJsonObject()) {
                continue;
            }
            Map<String, PetNum> rarities = new HashMap<>();
            for (Map.Entry<String, JsonElement> rarityEntry : petEntry.getValue().getAsJsonObject().entrySet()) {
                if (!rarityEntry.getValue().isJsonObject()) {
                    continue;
                }
                PetNum numbers = parsePetNum(rarityEntry.getValue().getAsJsonObject());
                if (numbers != null) {
                    rarities.put(rarityEntry.getKey(), numbers);
                }
            }
            if (!rarities.isEmpty()) {
                loaded.put(petEntry.getKey(), Map.copyOf(rarities));
            }
        }
        petNumbers = Map.copyOf(loaded);
    }

    private static PetNum parsePetNum(JsonObject levels) {
        int lowLevel = Integer.MAX_VALUE;
        int highLevel = Integer.MIN_VALUE;
        JsonObject low = null;
        JsonObject high = null;
        for (Map.Entry<String, JsonElement> entry : levels.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            int level;
            try {
                level = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            if (level < lowLevel) {
                lowLevel = level;
                low = entry.getValue().getAsJsonObject();
            }
            if (level > highLevel) {
                highLevel = level;
                high = entry.getValue().getAsJsonObject();
            }
        }
        if (low == null || high == null || lowLevel == Integer.MAX_VALUE) {
            return null;
        }
        return new PetNum(lowLevel, highLevel,
                readDoubleMap(low.getAsJsonObject("statNums")),
                readDoubleMap(high.getAsJsonObject("statNums")),
                readDoubleList(low.getAsJsonArray("otherNums")),
                readDoubleList(high.getAsJsonArray("otherNums")));
    }

    private static Map<String, Double> readDoubleMap(JsonObject object) {
        if (object == null) {
            return Map.of();
        }
        Map<String, Double> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                try {
                    map.put(entry.getKey(), entry.getValue().getAsDouble());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return Map.copyOf(map);
    }

    private static List<Double> readDoubleList(JsonArray array) {
        if (array == null) {
            return List.of();
        }
        List<Double> list = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                try {
                    list.add(element.getAsDouble());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return List.copyOf(list);
    }

    private void loadShards(ZipFile zipFile) throws IOException {
        JsonObject root = readJson(zipFile, "attribute_shards.json");
        if (root == null) {
            shards = List.of();
            shardByKey = Map.of();
            return;
        }
        JsonArray array = root.getAsJsonArray("attributes");
        List<Shard> loaded = new ArrayList<>(array == null ? 0 : array.size());
        Map<String, String> keys = new HashMap<>();
        if (array != null) {
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                Shard shard = Shard.from(element.getAsJsonObject());
                if (shard.internalName() == null || shard.internalName().isBlank()) {
                    continue;
                }
                loaded.add(shard);

                String key = shard.internalName();
                if (key.startsWith("ATTRIBUTE_SHARD_")) {
                    key = key.substring("ATTRIBUTE_SHARD_".length());
                }
                int semicolon = key.indexOf(';');
                if (semicolon >= 0) {
                    key = key.substring(0, semicolon);
                }
                if (!key.isEmpty()) {
                    keys.put(key.toLowerCase(Locale.ROOT), shard.internalName());
                }
                if (shard.bazaarName() != null) {
                    keys.putIfAbsent(shard.bazaarName().toLowerCase(Locale.ROOT), shard.internalName());
                }
                if (shard.displayName() != null) {
                    keys.putIfAbsent(shard.displayName().toLowerCase(Locale.ROOT), shard.internalName());
                }
            }
        }
        shards = List.copyOf(loaded);
        shardByKey = Map.copyOf(keys);
    }

    private void notifyLoadListeners() {
        for (Runnable listener : loadListeners) {
            try {
                listener.run();
            } catch (Throwable throwable) {
                log.warn("NEU 加载回调执行失败", throwable);
            }
        }
    }

    /**
     * 数据加载完成后执行；如果已经加载则立即执行。
     */
    public void runAfterLoad(Runnable runnable) {
        if (isLoaded()) {
            runnable.run();
        } else {
            loadListeners.add(runnable);
        }
    }

    public Optional<NeuItem> find(String internalName) {
        if (internalName == null || internalName.isBlank()) {
            return Optional.empty();
        }
        String key = internalName.startsWith("skyblock:")
                ? internalName.substring("skyblock:".length())
                : internalName;
        NeuItem item = items.get(key);
        if (item != null) {
            return Optional.of(item);
        }
        item = items.get(key.toUpperCase(Locale.ROOT));
        if (item != null) {
            return Optional.of(item);
        }
        if (!key.contains(";")) {
            item = items.get(key + ";1");
            if (item != null) {
                return Optional.of(item);
            }
            String prefix = key + ";";
            for (Map.Entry<String, NeuItem> entry : items.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    return Optional.of(entry.getValue());
                }
            }
        }
        return Optional.empty();
    }

    public List<NeuItem> browse(int limit) {
        return items.values().stream()
                .sorted(Comparator.comparing(item -> stripColors(item.displayName()), String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(1, limit))
                .toList();
    }

    public List<NeuItem> search(String query, int limit) {
        String needle = stripColors(query == null ? "" : query).trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return List.of();
        }
        return items.values().stream()
                .filter(item -> stripColors(item.displayName()).toLowerCase(Locale.ROOT).contains(needle)
                        || item.internalName().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(item -> stripColors(item.displayName()), String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(1, limit))
                .toList();
    }

    public ItemStack createItemStack(String internalName) {
        return find(internalName).map(this::createItemStack).orElseGet(() -> new ItemStack(Items.BARRIER));
    }

    public ItemStack createItemStack(NeuItem item) {
        Identifier itemId = parseIdentifier(item.itemId());
        boolean mappedLegacy = false;
        if (itemId != null) {
            Identifier legacy = LegacyItemIds.map(itemId.toString(), item.damage());
            if (legacy != null) {
                itemId = legacy;
                mappedLegacy = true;
            }
        }
        Item baseItem = itemId == null
                ? Items.AIR
                : BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.AIR);
        if (baseItem == Items.AIR && itemId != null
                && "skull".equals(itemId.getPath()) && item.damage() == 3) {
            baseItem = Items.PLAYER_HEAD;
        }
        if (baseItem == Items.AIR) {
            return new ItemStack(Items.BARRIER);
        }

        ItemStack stack = new ItemStack(baseItem);
        JsonObject nbt = item.nbt();
        if (nbt != null) {
            try {
                Tag tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, nbt);
                if (tag instanceof CompoundTag compoundTag) {
                    // Hypixel 现代物品把 Skyblock 字段放在 custom_data 根下；
                    // NEU 的旧 NBT 则把它们包在 ExtraAttributes 里，这里展开。
                    CompoundTag extra = compoundTag.getCompoundOrEmpty("ExtraAttributes");
                    CompoundTag customData = new CompoundTag();
                    if (extra.keySet().isEmpty()) {
                        customData = compoundTag;
                    } else {
                        for (String key : extra.keySet()) {
                            customData.put(key, extra.get(key));
                        }
                    }
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
                }
            } catch (Throwable throwable) {
                log.warn("NEU 物品 {} 的 nbttag 转换失败", item.internalName(), throwable);
            }

            JsonObject skullOwner = nbt.getAsJsonObject("SkullOwner");
            if (skullOwner != null) {
                ResolvableProfile profile = parseProfile(skullOwner);
                if (profile != null) {
                    stack.set(DataComponents.PROFILE, profile);
                }
            }

            if (hasEnchantments(nbt)) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
        }

        String model = mappedLegacy ? itemId.toString() : item.itemModel();
        if ((model == null || model.isBlank()) && nbt != null) {
            JsonElement modelElement = nbt.get("ItemModel");
            if (modelElement == null) {
                modelElement = nbt.get("itemModel");
            }
            if (modelElement != null && modelElement.isJsonPrimitive()) {
                model = modelElement.getAsString();
            }
        }
        if (model != null && !model.isBlank()) {
            Identifier modelId = parseIdentifier(model);
            if (modelId != null) {
                stack.set(DataComponents.ITEM_MODEL, modelId);
            }
        }

        if (isDyeableLeatherArmor(stack)) {
            // NEU 物品本体没有颜色时，以 overlays/<dataVersion>/<ID>.snbt 里的
            // minecraft:dyed_color 为准；注意不要从玩家当前装备读取，Dye 会改变颜色。
            Integer color = overlayColors.get(item.internalName());
            if (color == null) {
                color = parseDyedColor(nbt);
            }
            if (color != null) {
                stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
            }
        }
        return stack;
    }

    private static boolean isDyeableLeatherArmor(ItemStack stack) {
        return stack.is(Items.LEATHER_HELMET)
                || stack.is(Items.LEATHER_CHESTPLATE)
                || stack.is(Items.LEATHER_LEGGINGS)
                || stack.is(Items.LEATHER_BOOTS);
    }

    /**
     * 兼容旧版 {@code display.color}、直接 {@code dyed_color} 和组件形式。
     */
    private static Integer parseDyedColor(JsonObject nbt) {
        if (nbt == null) {
            return null;
        }
        JsonObject display = nbt.getAsJsonObject("display");
        if (display != null) {
            Integer color = readColor(display.get("color"));
            if (color != null) {
                return color;
            }
        }

        Integer direct = readColor(nbt.get("dyed_color"));
        if (direct != null) {
            return direct;
        }

        JsonObject components = nbt.getAsJsonObject("components");
        if (components != null) {
            Integer component = readColor(components.get("minecraft:dyed_color"));
            if (component != null) {
                return component;
            }
        }
        return null;
    }

    private static Integer readColor(JsonElement element) {
        try {
            if (element == null) {
                return null;
            }
            if (element.isJsonPrimitive()) {
                return element.getAsInt();
            }
            if (element.isJsonObject()) {
                JsonElement rgb = element.getAsJsonObject().get("rgb");
                return rgb != null && rgb.isJsonPrimitive() ? rgb.getAsInt() : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 生成带名称和 lore 的 ItemStack，供 REI 等物品列表展示。
     */
    public ItemStack createDisplayStack(NeuItem item) {
        ItemStack stack = createItemStack(item);
        if (stack.isEmpty() || stack.is(Items.BARRIER)) {
            return stack;
        }

        stack.set(DataComponents.CUSTOM_NAME, LegacyText.parse(injectPetPlaceholders(item.displayName(), item.internalName())));

        List<Component> lore = new ArrayList<>(item.lore().size());
        for (String line : item.lore()) {
            lore.add(LegacyText.parse(injectPetPlaceholders(line, item.internalName())));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    public java.util.Collection<NeuItem> items() {
        return items.values();
    }

    public List<Recipe> recipesFor(String internalName) {
        return recipesByOutput.getOrDefault(internalName, List.of());
    }

    public List<Recipe> usagesFor(String internalName) {
        return recipesByInput.getOrDefault(internalName, List.of());
    }

    public List<MobDropRecipe> mobDropRecipes() {
        return mobDropRecipes;
    }

    public List<MobDropRecipe> mobDropsForMob(String internalName) {
        return mobDropsByMob.getOrDefault(internalName, List.of());
    }

    public List<MobDropRecipe> mobDropsForDrop(String internalName) {
        return mobDropsByDrop.getOrDefault(internalName, List.of());
    }

    /**
     * 读取 ZIP 内的 JSON 资源（例如 {@code mobs/miner_zombie.json}）。
     * 每次调用都会重新打开 ZIP，调用方应自行缓存结果。
     */
    public JsonObject repositoryJson(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Path zip = zipPath();
        if (!Files.exists(zip)) {
            return null;
        }
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            return readJson(zipFile, path);
        } catch (IOException e) {
            log.warn("读取 NEU ZIP 资源失败: {}", path, e);
            return null;
        }
    }

    /**
     * 读取 ZIP 内的任意二进制资源（例如 {@code mobs/abyssal_miner.png}）。
     */
    public byte[] repositoryBytes(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Path zip = zipPath();
        if (!Files.exists(zip)) {
            return null;
        }
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            ZipEntry entry = zipFile.getEntry(path);
            if (entry == null) {
                return null;
            }
            try (InputStream input = zipFile.getInputStream(entry)) {
                return input.readAllBytes();
            }
        } catch (IOException e) {
            log.warn("读取 NEU ZIP 资源失败: {}", path, e);
            return null;
        }
    }

    public List<Recipe> recipes() {
        return recipes;
    }

    public Map<String, List<String>> parents() {
        return parents;
    }

    public Map<String, Map<String, PetNum>> petNumbers() {
        return petNumbers;
    }

    public List<Shard> shards() {
        return shards;
    }

    public String shardInternalName(String key) {
        return key == null ? null : shardByKey.get(key.toLowerCase(Locale.ROOT));
    }

    private static final String[] PET_RARITIES = {
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL"
    };

    private String injectPetPlaceholders(String text, String internalName) {
        if (text == null || text.indexOf('{') < 0 || internalName == null) {
            return text;
        }
        int semicolon = internalName.indexOf(';');
        if (semicolon <= 0) {
            return text;
        }
        String petType = internalName.substring(0, semicolon);
        Map<String, PetNum> rarities = petNumbers.get(petType);
        if (rarities == null) {
            return text;
        }

        int rarityIndex;
        try {
            String suffix = internalName.substring(semicolon + 1);
            int plus = suffix.indexOf('+');
            rarityIndex = Integer.parseInt(plus >= 0 ? suffix.substring(0, plus) : suffix);
        } catch (NumberFormatException e) {
            return text;
        }
        if (rarityIndex < 0 || rarityIndex >= PET_RARITIES.length) {
            return text;
        }
        PetNum numbers = rarities.get(PET_RARITIES[rarityIndex]);
        if (numbers == null) {
            return text;
        }

        text = text.replace("{LVL}", numbers.lowLevel() + " ➡ " + numbers.highLevel());
        for (Map.Entry<String, Double> entry : numbers.lowStats().entrySet()) {
            Double high = numbers.highStats().get(entry.getKey());
            if (high != null) {
                text = text.replace("{" + entry.getKey() + "}", entry.getValue() + " ➡ " + high);
            }
        }
        for (int i = 0; i < numbers.lowOther().size() && i < numbers.highOther().size(); i++) {
            text = text.replace("{" + i + "}", numbers.lowOther().get(i) + " ➡ " + numbers.highOther().get(i));
        }
        return text;
    }

    private static String baseId(String token) {
        if (token == null) {
            return "";
        }
        int semicolon = token.indexOf(':');
        return (semicolon >= 0 ? token.substring(0, semicolon) : token).trim();
    }

    private static ResolvableProfile parseProfile(JsonObject skullOwner) {
        JsonObject properties = skullOwner.getAsJsonObject("Properties");
        JsonArray textures = properties == null ? null : properties.getAsJsonArray("textures");
        if (textures == null || textures.isEmpty()) {
            return null;
        }

        Multimap<String, Property> map = ArrayListMultimap.create();
        byte[] seed = null;
        for (JsonElement element : textures) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject texture = element.getAsJsonObject();
            String value = getString(texture, "Value");
            if (value == null || value.isBlank()) {
                continue;
            }
            String signature = getString(texture, "Signature");
            map.put("textures", signature == null || signature.isBlank()
                    ? new Property("textures", value)
                    : new Property("textures", value, signature));
            if (seed == null) {
                seed = value.getBytes(StandardCharsets.UTF_8);
            }
        }
        if (map.isEmpty()) {
            return null;
        }

        UUID uuid;
        String id = getString(skullOwner, "Id");
        try {
            uuid = id == null ? UUID.nameUUIDFromBytes(seed) : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            uuid = UUID.nameUUIDFromBytes(seed);
        }
        String name = getString(skullOwner, "Name");
        GameProfile profile = new GameProfile(uuid, name == null ? "" : name, new PropertyMap(map));
        return ResolvableProfile.createResolved(profile);
    }

    private static boolean hasEnchantments(JsonObject nbt) {
        JsonObject extra = nbt.getAsJsonObject("ExtraAttributes");
        if (extra != null) {
            JsonElement enchantments = extra.get("enchantments");
            if (enchantments != null) {
                if (enchantments.isJsonObject() && !enchantments.getAsJsonObject().isEmpty()) {
                    return true;
                }
                if (enchantments.isJsonArray() && !enchantments.getAsJsonArray().isEmpty()) {
                    return true;
                }
            }
        }
        JsonElement ench = nbt.get("ench");
        return ench != null && ench.isJsonArray() && !ench.getAsJsonArray().isEmpty();
    }

    private static Identifier parseIdentifier(String value) {
        try {
            return value == null ? null : Identifier.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static JsonArray getArray(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static final java.util.regex.Pattern COLOR_PATTERN = java.util.regex.Pattern.compile("§.");

    private static String stripColors(String value) {
        return value == null ? "" : COLOR_PATTERN.matcher(value).replaceAll("");
    }

    private Manifest fetchManifest() throws IOException {
        URI uri = URI.create(BASE_URL + "/api/neu/manifest");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        try {
            int code = connection.getResponseCode();
            if (code != 200) {
                throw new IOException("获取 NEU manifest 失败: HTTP " + code);
            }
            try (InputStream input = connection.getInputStream();
                 Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return Manifest.from(JsonParser.parseReader(reader).getAsJsonObject());
            }
        } finally {
            connection.disconnect();
        }
    }

    private void saveManifest(Manifest value) {
        try {
            Path path = manifestPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(value.toJson()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("保存 NEU manifest 失败", e);
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder builder = new StringBuilder(64);
        for (byte b : digest.digest()) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    public record Manifest(int format, String version, String source, String file, String url,
                           long size, String sha256, String md5, int items, int recipes, int parents,
                           int petnums, int attributeShards, int overlays, int dataVersion,
                           int minClientVersion, boolean petLoreInjected) {

        static Manifest from(JsonObject json) {
            return new Manifest(
                    getInt(json, "format", 1),
                    getString(json, "version"),
                    getString(json, "source"),
                    getString(json, "file"),
                    getString(json, "url"),
                    getLong(json, "size", 0),
                    getString(json, "sha256"),
                    getString(json, "md5"),
                    getInt(json, "items", 0),
                    getInt(json, "recipes", 0),
                    getInt(json, "parents", 0),
                    getInt(json, "petnums", 0),
                    getInt(json, "attributeShards", 0),
                    getInt(json, "overlays", 0),
                    getInt(json, "dataVersion", 0),
                    getInt(json, "minClientVersion", 1),
                    getBoolean(json, "petLoreInjected", false)
            );
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("format", format);
            if (version != null) json.addProperty("version", version);
            if (source != null) json.addProperty("source", source);
            if (file != null) json.addProperty("file", file);
            if (url != null) json.addProperty("url", url);
            json.addProperty("size", size);
            if (sha256 != null) json.addProperty("sha256", sha256);
            if (md5 != null) json.addProperty("md5", md5);
            json.addProperty("items", items);
            json.addProperty("recipes", recipes);
            json.addProperty("parents", parents);
            json.addProperty("petnums", petnums);
            json.addProperty("attributeShards", attributeShards);
            json.addProperty("overlays", overlays);
            json.addProperty("dataVersion", dataVersion);
            json.addProperty("minClientVersion", minClientVersion);
            json.addProperty("petLoreInjected", petLoreInjected);
            return json;
        }

        public String asJson() {
            return COMPACT_GSON.toJson(toJson());
        }
    }

    public record NeuItem(String internalName, String displayName, String itemId, int damage,
                          String itemModel, String rarity, List<String> lore, String craftText,
                          List<String> info, Integer x, Integer y, Integer z, String island,
                          JsonObject nbt) {

        static NeuItem from(JsonObject json) {
            return new NeuItem(
                    getString(json, "internalname"),
                    getString(json, "displayname"),
                    getString(json, "itemid"),
                    getInt(json, "damage", 0),
                    getString(json, "item_model"),
                    getString(json, "rarity"),
                    readStringList(getArray(json, "lore")),
                    getStringOrEmpty(json, "crafttext"),
                    readStringList(getArray(json, "info")),
                    getNullableInt(json, "x"),
                    getNullableInt(json, "y"),
                    getNullableInt(json, "z"),
                    getString(json, "island"),
                    json.getAsJsonObject("nbttag")
            );
        }

        public boolean hasLocation() {
            return x != null && y != null && z != null;
        }

        public String locationText() {
            if (!hasLocation()) {
                return "";
            }
            return (island == null || island.isBlank() ? "?" : island)
                    + " · " + x + ", " + y + ", " + z;
        }

        public String cleanName() {
            String name = displayName == null ? internalName : displayName.replaceAll("§.", "");
            name = name.replaceAll("\\s*\\((Rift\\s+)?NPC\\)\\s*$", "").trim();
            return name.isEmpty() ? internalName : name;
        }
    }

    /**
     * 配方模型。不同类型只使用其中一部分字段：
     * crafting -> grid；forge -> inputs/duration；npc_shop -> npc/cost；
     * katgrade -> input/items/coins/time；trade -> costText；drops -> raw。
     */
    public record Recipe(String type, String sourceItem, String output, int count,
                         Map<String, String> grid, List<String> inputs, List<String> cost,
                         String npc, String input, List<String> items, int duration,
                         int coins, int time, String costText, String extraText,
                         Boolean supercraftable, JsonObject raw) {

        static Recipe from(JsonObject json) {
            Map<String, String> grid = new LinkedHashMap<>();
            JsonObject gridObject = json.getAsJsonObject("grid");
            if (gridObject != null) {
                for (String key : List.of("A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3")) {
                    JsonElement value = gridObject.get(key);
                    grid.put(key, value != null && value.isJsonPrimitive() ? value.getAsString() : "");
                }
            }

            String output = getString(json, "output");
            if (output == null) {
                output = getString(json, "result");
            }

            return new Recipe(
                    getStringOrEmpty(json, "type"),
                    getString(json, "sourceItem"),
                    output,
                    Math.max(1, getInt(json, "count", 1)),
                    Map.copyOf(grid),
                    readStringList(getArray(json, "inputs")),
                    readStringList(getArray(json, "cost")),
                    getString(json, "npc"),
                    getString(json, "input"),
                    readStringList(getArray(json, "items")),
                    getInt(json, "duration", 0),
                    getInt(json, "coins", 0),
                    getInt(json, "time", 0),
                    getString(json, "cost"),
                    getString(json, "extraText"),
                    json.has("supercraftable") && json.get("supercraftable").isJsonPrimitive()
                            ? json.get("supercraftable").getAsBoolean() : null,
                    json
            );
        }

        public List<String> inputTokens() {
            return switch (type) {
                case "crafting" -> new ArrayList<>(grid.values());
                case "forge" -> inputs;
                case "npc_shop" -> cost;
                case "katgrade" -> {
                    List<String> all = new ArrayList<>(items);
                    if (input != null) {
                        all.add(input);
                    }
                    yield all;
                }
                case "trade" -> costText == null ? List.of() : List.of(costText);
                default -> List.of();
            };
        }

        public List<String> outputTokens() {
            return output == null ? List.of() : List.of(output);
        }
    }

    /**
     * NEU {@code type: "drops"} 配方。怪物本身是 items.json 里的伪物品，
     * drops 的每个 id 都是该怪物的掉落物。
     */
    public record MobDropRecipe(String sourceItem, String mob, String name, String render,
                                int level, int coins, String panorama, List<String> extra,
                                Map<String, Integer> experience, List<MobDrop> drops,
                                Recipe recipe) {

        static MobDropRecipe from(Recipe recipe) {
            JsonObject json = recipe.raw();
            String source = firstNonBlank(
                    getString(json, "sourceItem"),
                    recipe.sourceItem(),
                    getString(json, "mob")
            );
            String mob = firstNonBlank(getString(json, "mob"), source);

            Map<String, Integer> experience = new LinkedHashMap<>();
            for (String key : List.of("combat_xp", "farming_xp", "fishing_xp",
                    "hunting_xp", "mining_xp", "xp")) {
                addExperience(experience, json, key);
            }
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                if (entry.getKey().endsWith("_xp")) {
                    addExperience(experience, json, entry.getKey());
                }
            }

            List<MobDrop> drops = new ArrayList<>();
            JsonArray array = getArray(json, "drops");
            if (array != null) {
                for (JsonElement element : array) {
                    MobDrop drop = MobDrop.from(element);
                    if (drop != null) {
                        drops.add(drop);
                    }
                }
            }

            return new MobDropRecipe(
                    source,
                    mob,
                    firstNonBlank(getString(json, "name"), source),
                    getString(json, "render"),
                    Math.max(0, getInt(json, "level", 0)),
                    Math.max(0, getInt(json, "coins", 0)),
                    getString(json, "panorama"),
                    readStringList(getArray(json, "extra")),
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(experience)),
                    List.copyOf(drops),
                    recipe
            );
        }

        private static void addExperience(Map<String, Integer> map, JsonObject json, String key) {
            if (map.containsKey(key) || !json.has(key)) {
                return;
            }
            int value = getInt(json, key, 0);
            if (value > 0) {
                map.put(key, value);
            }
        }
    }

    public record MobDrop(String id, String chance, List<String> extra,
                          List<MobDropAlternative> alternatives) {

        static MobDrop from(JsonElement element) {
            if (element == null || element.isJsonNull()) {
                return null;
            }
            if (element.isJsonPrimitive()) {
                return new MobDrop(element.getAsString(), null, List.of(), List.of());
            }
            if (!element.isJsonObject()) {
                return null;
            }
            JsonObject json = element.getAsJsonObject();
            String id = getString(json, "id");
            if (id == null || id.isBlank()) {
                return null;
            }

            List<MobDropAlternative> alternatives = new ArrayList<>();
            JsonArray array = getArray(json, "alternatives");
            if (array != null) {
                for (JsonElement alternative : array) {
                    if (!alternative.isJsonObject()) {
                        continue;
                    }
                    JsonObject alternativeJson = alternative.getAsJsonObject();
                    String alternativeId = getString(alternativeJson, "id");
                    if (alternativeId != null && !alternativeId.isBlank()) {
                        alternatives.add(new MobDropAlternative(
                                alternativeId,
                                getString(alternativeJson, "chance")
                        ));
                    }
                }
            }

            return new MobDrop(
                    id,
                    getString(json, "chance"),
                    readStringList(getArray(json, "extra")),
                    List.copyOf(alternatives)
            );
        }
    }

    public record MobDropAlternative(String id, String chance) {
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    public record PetNum(int lowLevel, int highLevel,
                         Map<String, Double> lowStats, Map<String, Double> highStats,
                         List<Double> lowOther, List<Double> highOther) {
    }

    public record Shard(String bazaarName, String displayName, String rarity, String internalName,
                        String abilityName, String alignment, List<String> family, String shardId) {

        static Shard from(JsonObject json) {
            return new Shard(
                    getString(json, "bazaarName"),
                    getString(json, "displayName"),
                    getString(json, "rarity"),
                    getString(json, "internalName"),
                    getString(json, "abilityName"),
                    getString(json, "alignment"),
                    readStringList(getArray(json, "family")),
                    getString(json, "shardId")
            );
        }
    }

    private static List<String> readStringList(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<String> list = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element != null && element.isJsonPrimitive()) {
                list.add(element.getAsString());
            }
        }
        return List.copyOf(list);
    }

    private static String getStringOrEmpty(JsonObject object, String key) {
        String value = getString(object, key);
        return value == null ? "" : value;
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        try {
            return element == null ? fallback : element.getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        try {
            return element == null ? fallback : element.getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Integer getNullableInt(JsonObject object, String key) {
        JsonElement element = object.get(key);
        try {
            return element == null || !element.isJsonPrimitive() ? null : element.getAsInt();
        } catch (Exception e) {
            return null;
        }
    }

    private static long getLong(JsonObject object, String key, long fallback) {
        JsonElement element = object.get(key);
        try {
            return element == null ? fallback : element.getAsLong();
        } catch (Exception e) {
            return fallback;
        }
    }
}
