package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.Blaze3D;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.io.FileUtils;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

public class WaypointListener extends AbstractListener {

    public static final File waypointFolder = ConfigManager.getCustomConfigFileName("waypoints");
    public static final Map<String, List<Waypoint>> configFileToWaypointMap = new ConcurrentHashMap<>();
    public static final Map<String, List<Waypoint>> configFileToOrderedWaypointMap = new ConcurrentHashMap<>();
    public static final Map<String, Set<Waypoint>> configFileToOrderedWaypointSetMap = new ConcurrentHashMap<>();

    public static final String DEFAULT_FILE_NAME = "_default.json";

    /** 标题文本缓存：key = 原标题 + 颜色。 */
    private static final Map<String, Component> TITLE_CACHE = new HashMap<>();
    /** 距离文本缓存：key = 颜色 + 米数，避免每帧 String.format + Component。 */
    private static final Map<Long, Component> DISTANCE_CACHE = new HashMap<>();
    /** 计分板条件串预解析缓存：原串 -> 条件列表。 */
    private static final Map<String, List<String>> CONDITION_CACHE = new HashMap<>();

    static {
        waypointFolder.mkdirs();
    }

    private static final WatchService watcher;
    static {
        try {
            watcher = FileSystems.getDefault().newWatchService();
            waypointFolder.toPath().register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String currentEditingFileName = DEFAULT_FILE_NAME;

    public static void openWaypointFolder() {
        Blaze3D.openPath(waypointFolder.toPath());
    }

    @Override
    public void run() {
        File[] files = waypointFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                configFileToWaypointMap.put(file.getName(), Waypoint.fromConfig(file.getName()));
            }
        }
        mergeFromConfig();
        while(true) {
            try {
                executeThread();
            } catch (Throwable e) {
                logger.catching(e);
            }
        }
    }

    private void executeThread() throws Throwable {
        WatchKey event = watcher.take();
        for (WatchEvent<?> pollEvent : event.pollEvents()) {
            if (!(pollEvent.context() instanceof Path path)) {
                continue;
            }
            String fileName = path.getFileName().toString();
            if (pollEvent.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                configFileToWaypointMap.remove(fileName);
            } else if (fileName.endsWith(".json")) {
                configFileToWaypointMap.put(fileName, Waypoint.fromConfig(fileName));
            }
        }
        event.reset();
        mergeFromConfig();
    }

    public void mergeFromConfig() {
        waypoints.removeIf(Waypoint::isFromConfig);
        Collection<List<Waypoint>> values = configFileToWaypointMap.values();
        for (List<Waypoint> waypoints : values) {
            this.waypoints.addAll(waypoints);
        }

        configFileToOrderedWaypointMap.clear();
        configFileToOrderedWaypointSetMap.clear();
        for (Map.Entry<String, List<Waypoint>> entry : configFileToWaypointMap.entrySet()) {
            boolean shouldReset = false;
            if(!configCurrentWaypointCursor.containsKey(entry.getKey())) {
                shouldReset = true;
            } else {
                Waypoint currentWaypoint = configCurrentWaypointCursor.get(entry.getKey());
                if(currentWaypoint == null || !entry.getValue().contains(currentWaypoint)) {
                    shouldReset = true;
                }
            }
            List<Waypoint> orderedWaypoints = entry.getValue().stream().filter(Waypoint::ordered).sorted(Comparator.comparingInt(Waypoint::id)).toList();
            Set<Waypoint> orderedWaypointsSet = new HashSet<>(orderedWaypoints);
            if(shouldReset && !orderedWaypointsSet.isEmpty()) {
                configCurrentWaypointCursor.put(entry.getKey(), orderedWaypoints.getFirst());
            }
            configFileToOrderedWaypointMap.put(entry.getKey(), orderedWaypoints);
            configFileToOrderedWaypointSetMap.put(entry.getKey(), orderedWaypointsSet);
        }
    }

    public boolean hasWaypoint(BlockPos pos) {
        return waypoints.stream().anyMatch(waypoint -> waypoint.pos().equals(pos));
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        waypoints.removeIf(w -> !w.isFromConfig && w.removeIfWorldChange);

        configCurrentWaypointCursor.clear();
        for (Map.Entry<String, List<Waypoint>> entry : configFileToWaypointMap.entrySet()) {
            entry.getValue().stream().filter(Waypoint::ordered).min(Comparator.comparingInt(Waypoint::id)).ifPresent(waypoint -> configCurrentWaypointCursor.put(entry.getKey(), waypoint));
        }
    }

    public record Waypoint(int id, boolean enabled, boolean ordered, String displayTitle, BlockPos pos, Color color, String scorebroadCondition, boolean drawBox, boolean showDistance, boolean isFromConfig, boolean removeIfWorldChange, long keepTimeMills) {

        private static boolean isDefaultFileName(String fileName) {
            return fileName.equals(DEFAULT_FILE_NAME);
        }

        public static List<Waypoint> getDefaultWaypoints() {
            return configFileToWaypointMap.get(DEFAULT_FILE_NAME);
        }

        public static List<Waypoint> fromConfig(String fileName) {
            if(!fileName.endsWith(".json")) {
                fileName += ".json";
            }
            File file = new File(waypointFolder, fileName);
            if(!file.exists()) {
                return new ArrayList<>();
            }
            try {
                String array = FileUtils.readFileToString(file, "UTF-8");
                JsonArray jsonArray = JsonParser.parseString(array).getAsJsonArray();
                List<Waypoint> waypoints = new ArrayList<>();
                for (JsonElement jsonElement : jsonArray) {
                    waypoints.add(fromJson(jsonElement.getAsJsonObject()));
                }
                return waypoints;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return new ArrayList<>();
        }

        public static void saveToConfig(String fileName, List<Waypoint> waypoints) {
            if(!fileName.endsWith(".json")) {
                fileName += ".json";
            }

            JsonArray jsonArray = new JsonArray();
            for (Waypoint waypoint : waypoints) {
                jsonArray.add(waypoint.asConfig().toJson());
            }
            try {
                FileUtils.writeStringToFile(new File(waypointFolder, fileName), jsonArray.toString(), "UTF-8");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static Waypoint fromJson(JsonObject json) {
            return new Waypoint(
                    json.has("id") ? json.get("id").getAsInt() : -1,
                    json.has("enabled") ? json.get("enabled").getAsBoolean() : true,
                    json.has("ordered") ? json.get("ordered").getAsBoolean() : false,
                    json.has("displayTitle") ? json.get("displayTitle").getAsString() : "",
                    json.has("pos") ? BlockPos.of(json.get("pos").getAsLong()) : BlockPos.ZERO,
                    json.has("color") ? new Color(json.get("color").getAsInt()) : Color.WHITE,
                    json.has("scorebroadCondition") ? json.get("scorebroadCondition").getAsString() : "",
                    json.has("drawBox") ? json.get("drawBox").getAsBoolean() : true,
                    json.has("showDistance") ? json.get("showDistance").getAsBoolean() : false,
                    json.has("isFromConfig") ? json.get("isFromConfig").getAsBoolean() : false,
                    json.has("removeIfWorldChange") ? json.get("removeIfWorldChange").getAsBoolean() : false,
                    json.has("keepTimeMills") ? json.get("keepTimeMills").getAsLong() : 0
            );
        }

        public Waypoint asConfig() {
            return new Waypoint(id, enabled, ordered, displayTitle, pos, color, scorebroadCondition, drawBox, showDistance, true, removeIfWorldChange, keepTimeMills);
        }

        public Waypoint asNonConfig() {
            return new Waypoint(id, enabled, ordered, displayTitle, pos, color, scorebroadCondition, drawBox, showDistance, false, removeIfWorldChange, keepTimeMills);
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("id", id);
            json.addProperty("enabled", enabled);
            json.addProperty("ordered", ordered);
            json.addProperty("displayTitle", displayTitle);
            json.addProperty("pos", pos.asLong());
            json.addProperty("color", color.getRGB());
            json.addProperty("scorebroadCondition", scorebroadCondition);
            json.addProperty("isFromConfig", isFromConfig);
            json.addProperty("removeIfWorldChange", removeIfWorldChange);
            json.addProperty("keepTimeMills", keepTimeMills);
            json.addProperty("drawBox", drawBox);
            json.addProperty("showDistance", showDistance);
            return json;
        }

    }

    private final List<Waypoint> waypoints = new CopyOnWriteArrayList<>();

    private final Map<String, Waypoint> configCurrentWaypointCursor = new HashMap<>();

    @Override
    public String getListenerName() {
        return "WaypointListener";
    }

    @Override
    public void registerListeners() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onUnload);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private final Object2LongMap<Waypoint> expireTime = new Object2LongOpenHashMap<>();

    private void onLastRender(LevelRenderContext context) {
        if(!waypoints.isEmpty() && mc.player != null) {
            RenderUtils.WorldRender wrLine = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
            RenderUtils.WorldRender wrFill = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);

            List<String> sbLines = ToolList.getInstance().fetchScoreboardLinesNoColor();
            Object2BooleanMap<String> conditionVisibleCache = new Object2BooleanOpenHashMap<>();

            for (Waypoint waypoint : waypoints) {
                if (waypoint.keepTimeMills > 0) {
                    if (!expireTime.containsKey(waypoint)) {
                        expireTime.put(waypoint, System.currentTimeMillis() + waypoint.keepTimeMills);
                    } else if (expireTime.getLong(waypoint) < System.currentTimeMillis()) {
                        waypoints.remove(waypoint);
                        continue;
                    }
                }
                if (!waypoint.enabled) continue;
                if (ToolList.getInstance().stringHasContext(waypoint.scorebroadCondition)
                        && !conditionVisibleCache.computeIfAbsent(waypoint.scorebroadCondition,
                        condition -> conditions((String) condition).stream()
                                .anyMatch(cond -> sbLines.stream().anyMatch(line -> line.contains(cond))))) {
                    continue;
                }

                if (waypoint.ordered) {
                    if (!configCurrentWaypointCursor.containsValue(waypoint)) continue;
                    Waypoint nextOrderedWaypoint = waypoint;
                    for (int i = 0; i < 2 && nextOrderedWaypoint != null; i++) {
                        drawWaypoint(context, nextOrderedWaypoint, wrFill, wrLine);
                        switch (i) {
                            case 0 -> RenderUtils.renderESP(wrLine, nextOrderedWaypoint.pos, 0, 1, 0, 1f, false);
                            case 1 -> RenderUtils.renderESP(wrLine, nextOrderedWaypoint.pos, 1, 1, 0, 1f, false);
                        }
                        Waypoint nextOrderedWaypoint1 = tryGetNextOrderedWaypoint(nextOrderedWaypoint);
                        if (nextOrderedWaypoint1 == nextOrderedWaypoint) break;
                        nextOrderedWaypoint = nextOrderedWaypoint1;
                    }
                } else {
                    drawWaypoint(context, waypoint, wrFill, wrLine);
                }
            }

            configCurrentWaypointCursor.replaceAll((key, value) -> {
                if(mc.player != null) {
                    Waypoint newValue = tryGetNextOrderedWaypoint(value);
                    if(newValue != null) {
                        if(mc.player.position().distanceTo(Vec3.atBottomCenterOf(value.pos())) < 3) {
                            return newValue;
                        }
                        if(mc.player.position().distanceTo(Vec3.atBottomCenterOf(newValue.pos())) < 3) {
                            Waypoint newNewValue = tryGetNextOrderedWaypoint(newValue);
                            if(newNewValue != null) {
                                return newNewValue;
                            }
                        }
                    }
                }
                return value;
            });

            wrLine.finishDraw();
            wrFill.finishDraw();
        }
    }

    private Waypoint tryGetNextOrderedWaypoint(Waypoint currentOrderedWaypoint) {
        if(currentOrderedWaypoint == null) {
            return null;
        }
        for (Map.Entry<String, List<Waypoint>> entry : configFileToWaypointMap.entrySet()) {
            List<Waypoint> orderedWaypoints = configFileToOrderedWaypointMap.get(entry.getKey());
            Set<Waypoint> orderedWaypointsSet = configFileToOrderedWaypointSetMap.get(entry.getKey());
            if(orderedWaypoints != null && orderedWaypointsSet != null && orderedWaypointsSet.contains(currentOrderedWaypoint)) {
                return orderedWaypoints.get((orderedWaypoints.indexOf(currentOrderedWaypoint) + 1) % orderedWaypoints.size());
            }
        }
        return null;
    }

    private void drawWaypoint(LevelRenderContext context, Waypoint waypoint, RenderUtils.WorldRender wrFill, RenderUtils.WorldRender wrLine) {
        Vec3 pos = Vec3.atBottomCenterOf(waypoint.pos());
        Component title = TITLE_CACHE.computeIfAbsent(waypoint.displayTitle + '\u0000' + waypoint.color.getRGB(),
                key -> Component.literal(waypoint.displayTitle.replace("&", "§")).withColor(waypoint.color.getRGB()));
        RenderUtils.renderWaypoint(context, pos.add(0, 1, 0), title, 1.414f);
        if(waypoint.showDistance && mc.player != null) {
            int meters = (int) Math.round(Math.sqrt(mc.player.distanceToSqr(pos)));
            long key = ((long) waypoint.color.getRGB() << 32) | (meters & 0xFFFFFFFFL);
            Component distance = DISTANCE_CACHE.computeIfAbsent(key,
                    k -> Component.literal("§e" + meters + "m").withColor(waypoint.color.getRGB()));
            RenderUtils.renderWaypoint(context, pos.add(0, 1, 0), distance, 1.414f, 10);
        }
        if(waypoint.drawBox) {
            RenderUtils.renderESP(wrFill, pos, waypoint.color.getRed() / 255f, waypoint.color.getGreen() / 255f, waypoint.color.getBlue() / 255f, 0.5f, true);
            RenderUtils.renderESP(wrLine, pos, waypoint.color.getRed() / 255f, waypoint.color.getGreen() / 255f, waypoint.color.getBlue() / 255f, 1f, false);
        }
    }

    /** 条件串按 {@code |} 拆分并缓存，避免每次判条件都 split/stream。 */
    private static List<String> conditions(String raw) {
        return CONDITION_CACHE.computeIfAbsent(raw, value -> Stream.of(value.split("\\|"))
                .map(String::trim).filter(cond -> !cond.isEmpty()).toList());
    }

    public void addTempWaypoint(Waypoint waypoint) {
        waypoints.add(waypoint.asNonConfig());
    }

    public void addTempWaypoint(String displayName, BlockPos pos, Color color, boolean drawBox) {
        waypoints.add(new Waypoint(-1, true, false, displayName, pos, color, "", drawBox, true, false, true, 0));
    }

    public void addTempWaypoint(String displayName, BlockPos pos, Color color, boolean drawBox, long keepTimeMills) {
        waypoints.add(new Waypoint(-1, true, false, displayName, pos, color, "", drawBox, true, false, true, keepTimeMills));
    }

}
