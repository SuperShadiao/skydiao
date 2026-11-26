package pers.XiaoShadiao.skydiao.eventbuslistener.dungeon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.dungeon.DungeonMapUtils;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.regex.Pattern;

/**
 * 凋零门/血房门 ESP：在地图上从当前房间往外找下一道凋零门（黑色）或血房门（红色），
 * 在世界里画一个 3 宽 x 4 高 x 3 厚的框。手里有钥匙时绿色，否则红色。
 *
 * <p>门的像素位置和框的偏移照搬 Skyblocker 的 {@code DungeonManager#getBloodRushDoorPos}：
 * 竖直墙缝取门的中心像素，水平墙缝取另一方向，框固定在格点 +14~+17 / +30~+33。
 * 钥匙状态完全来自聊天栏的拾取/开门消息。
 */
public class DungeonDoorESPListener extends AbstractListener {

    /** 地图上凋零门是黑色 119。 */
    private static final byte COLOR_WITHER_DOOR = 119;
    /** 门的基准高度和高度。 */
    private static final int DOOR_Y = 69;
    private static final int DOOR_HEIGHT = 4;

    private static final Pattern KEY_FOUND = Pattern.compile(
            "^RIGHT CLICK on (?:the BLOOD DOOR|a WITHER door) to open it\\. This key can only be used to open 1 door!$");
    private static final Pattern WITHER_DOOR_OPENED = Pattern.compile("^\\w+ opened a WITHER door!$");
    private static final String BLOOD_DOOR_OPENED = "The BLOOD DOOR has been opened!";

    private static volatile boolean chatHasKey;
    /** 血房门已经打开后，整张图的特殊门都开完了，不再标记。 */
    private static volatile boolean bloodDoorOpened;
    /** 当前 ESP 标记的门在地图上的墙缝坐标；HUD 用它来决定哪个门做闪烁。 */
    private static volatile int markedMapX = Integer.MIN_VALUE;
    private static volatile int markedMapZ = Integer.MIN_VALUE;
    private static volatile boolean markedEastWest;

    /** 当前是否持有钥匙（聊天栏拾取后为 true，开门后为 false）。 */
    public static boolean hasKey() {
        return chatHasKey;
    }

    /** HUD 用：这个地图门标记是不是当前 ESP 正在标记的那一道。 */
    public static boolean isMarked(DungeonMapScanListener.Snapshot.DoorView door) {
        int x = markedMapX;
        int z = markedMapZ;
        if (x == Integer.MIN_VALUE) return false;
        if (door.eastWest() != markedEastWest) return false;
        if (door.eastWest()) {
            return door.x() == x && Math.abs(door.z() + door.height() / 2 - z) <= 4;
        }
        return door.z() == z && Math.abs(door.x() + door.width() / 2 - x) <= 4;
    }

    @Override
    public String getListenerName() {
        return "DungeonDoorESP";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onRender);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((mc, level) -> {
            chatHasKey = false;
            bloodDoorOpened = false;
            markedMapX = Integer.MIN_VALUE;
            markedMapZ = Integer.MIN_VALUE;
        });
    }

    private void onChat(Component component, boolean overlay) {
        if (overlay) return;
        String message = ToolList.getInstance().deleteColorCode(component.getString());
        if (KEY_FOUND.matcher(message).matches()) {
            chatHasKey = true;
        } else if (message.equals(BLOOD_DOOR_OPENED)) {
            chatHasKey = false;
            bloodDoorOpened = true;
        } else if (WITHER_DOOR_OPENED.matcher(message).matches()) {
            chatHasKey = false;
        }
    }

    private void onRender(LevelRenderContext context) {
        if (!ConfigManager.dungeonDoorESP.getValue() || !ConfigManager.dungeonMapXray.getValue()) return;
        DungeonMapScanListener.Snapshot snapshot = DungeonMapScanListener.getCurrent();
        if (snapshot == null) return;

        RenderUtils.WorldRender fill = RenderUtils.createWorldRenderInstance(
                context, CustomRenderPipeline.THROUGH_WALLS_FILL);
        RenderUtils.WorldRender line = RenderUtils.createWorldRenderInstance(
                context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        boolean hasKey = hasKey();

        // 仙女房的门：有钥匙粉色（不要绿色），没钥匙红色
        for (DungeonMapScanListener.Snapshot.DoorView door : snapshot.doors()) {
            if (door.kind() != DungeonMapScanListener.DOOR_FAIRY) continue;
            AABB box = doorBox(snapshot, door);
            if (box == null) continue;
            if (hasKey) {
                renderBox(fill, line, box, 1f, 0.41f, 0.71f);
            } else {
                renderBox(fill, line, box, 1f, 0f, 0f);
            }
            return;
        }

        DoorTarget target = bloodDoorOpened ? null : findNextDoor(snapshot);
        if (target == null) {
            markedMapX = Integer.MIN_VALUE;
            markedMapZ = Integer.MIN_VALUE;
        } else {
            markedMapX = target.mapX();
            markedMapZ = target.mapZ();
            markedEastWest = target.eastWest();
            if (hasKey) {
                renderBox(fill, line, target.box(), 0f, 1f, 0f);
            } else {
                renderBox(fill, line, target.box(), 1f, 0f, 0f);
            }
        }
        fill.finishDraw();
        line.finishDraw();
    }

    private record DoorTarget(AABB box, int mapX, int mapZ, boolean eastWest) {
    }

    private static void renderBox(RenderUtils.WorldRender fill, RenderUtils.WorldRender line, AABB box,
                                  float r, float g, float b) {
        RenderUtils.renderESP(fill, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, 0.4f, true);
        RenderUtils.renderESP(line, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, 1f, false);
    }

    /** 把 HUD 门标记（地图像素）换算回世界里 3x4x3 的门框。 */
    private static AABB doorBox(DungeonMapScanListener.Snapshot snapshot,
                                DungeonMapScanListener.Snapshot.DoorView door) {
        int size = snapshot.mapRoomSize();
        if (size <= 0) return null;
        int pitch = DungeonMapUtils.mapPitch(size);
        int phaseX = Math.floorMod(snapshot.mapEntrance()[0], pitch);
        int phaseZ = Math.floorMod(snapshot.mapEntrance()[1], pitch);
        int cellX;
        int cellZ;
        if (door.eastWest()) {
            cellX = door.x() - size;
            cellZ = phaseZ + Math.floorDiv(door.z() - phaseZ, pitch) * pitch;
        } else {
            cellX = phaseX + Math.floorDiv(door.x() - phaseX, pitch) * pitch;
            cellZ = door.z() - size;
        }
        int[] cell = DungeonMapUtils.physicalFromMap(snapshot.physicalEntrance(), snapshot.mapEntrance(),
                size, new int[]{cellX, cellZ});
        if (door.eastWest()) {
            return new AABB(cell[0] + 30, DOOR_Y, cell[1] + 14,
                    cell[0] + 33, DOOR_Y + DOOR_HEIGHT, cell[1] + 17);
        }
        return new AABB(cell[0] + 14, DOOR_Y, cell[1] + 30,
                cell[0] + 17, DOOR_Y + DOOR_HEIGHT, cell[1] + 33);
    }

    /** 按 Skyblocker 的方式扫描地图墙缝上的门像素，返回第一道门的 3x4x3 框和墙缝坐标。 */
    private static DoorTarget findNextDoor(DungeonMapScanListener.Snapshot snapshot) {
        MapItemSavedData map = snapshot.map();
        int size = snapshot.mapRoomSize();
        if (map == null || size <= 0) return null;
        int pitch = DungeonMapUtils.mapPitch(size);
        int phaseX = Math.floorMod(snapshot.mapEntrance()[0], pitch);
        int phaseZ = Math.floorMod(snapshot.mapEntrance()[1], pitch);

        // 上下相邻房间之间的门：取房间底部墙缝的中心像素
        for (int x = phaseX + size / 2; x < 128; x += pitch) {
            for (int z = phaseZ + size; z < 128; z += pitch) {
                if (!isDoorColor(DungeonMapUtils.color(map, x, z))) continue;
                int[] cell = DungeonMapUtils.physicalFromMap(snapshot.physicalEntrance(), snapshot.mapEntrance(),
                        size, new int[]{x - size / 2, z - size});
                AABB box = new AABB(cell[0] + 14, DOOR_Y, cell[1] + 30,
                        cell[0] + 17, DOOR_Y + DOOR_HEIGHT, cell[1] + 33);
                return new DoorTarget(box, x, z, false);
            }
        }

        // 左右相邻房间之间的门：取房间右侧墙缝的中心像素
        for (int x = phaseX + size; x < 128; x += pitch) {
            for (int z = phaseZ + size / 2; z < 128; z += pitch) {
                if (!isDoorColor(DungeonMapUtils.color(map, x, z))) continue;
                int[] cell = DungeonMapUtils.physicalFromMap(snapshot.physicalEntrance(), snapshot.mapEntrance(),
                        size, new int[]{x - size, z - size / 2});
                AABB box = new AABB(cell[0] + 30, DOOR_Y, cell[1] + 14,
                        cell[0] + 33, DOOR_Y + DOOR_HEIGHT, cell[1] + 17);
                return new DoorTarget(box, x, z, true);
            }
        }
        return null;
    }

    private static boolean isDoorColor(byte color) {
        return color == COLOR_WITHER_DOOR || color == DungeonMapUtils.COLOR_BLOOD;
    }
}
