package pers.XiaoShadiao.skydiao.utils.dungeon;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;

/**
 * 房间模板数据加载器。
 *
 * <p>数据放在 {@code assets/skydiao/dungeons/<dungeon>/}，通过 classpath 读取，所以开发环境和正式包都能用。
 * 索引 {@code index.json} 一次读完（体积很小），房间方块按形状分组懒加载：只有真的要在某个形状里匹配时才会
 * 解压那批 {@code .bin}，避免进游戏就把几十 MB 全读进内存。
 */
public final class DungeonRoomData {

    private static final String ROOT = "/assets/skydiao/dungeons/catacombs/";
    private static final int BIN_MAGIC = 0x534B4452;
    private static final int BIN_VERSION = 1;
    private static final Gson GSON = new Gson();

    private static volatile JsonObject index;
    private static final Map<String, Integer> BLOCK_IDS = new HashMap<>();
    private static final Map<Block, Integer> BLOCK_CACHE = new HashMap<>();
    private static final Map<RoomShape, List<DungeonRoom>> SHAPES = new EnumMap<>(RoomShape.class);

    /** 普通房间包含的全部形状分组。 */
    private static final RoomShape[] NORMAL_SHAPES = {
            RoomShape.ONE_BY_ONE, RoomShape.ONE_BY_TWO, RoomShape.ONE_BY_THREE,
            RoomShape.ONE_BY_FOUR, RoomShape.TWO_BY_TWO, RoomShape.L_SHAPE};

    private DungeonRoomData() {
    }

    /** 按房间类型取需要参与匹配的模板；入口/仙女/血门没有模板，返回空表。 */
    public static List<DungeonRoom> roomsFor(DungeonRoomType type) {
        return switch (type) {
            case ROOM -> rooms(NORMAL_SHAPES);
            case PUZZLE -> rooms(RoomShape.PUZZLE);
            case TRAP -> rooms(RoomShape.TRAP);
            case MINIBOSS -> rooms(RoomShape.MINIBOSS);
            default -> List.of();
        };
    }

    /** 所有带模板的房间；透视扫描事先不知道房间类型，只能全部试一遍。 */
    public static List<DungeonRoom> allRooms() {
        return rooms(RoomShape.values());
    }

    /** 某类房间的模板是否已经解压好；没加载时调用方可以后台预热，避免扫描线程被解压卡住。 */
    public static boolean isLoaded(DungeonRoomType type) {
        ensureIndex();
        if (index == null) return false;
        RoomShape[] shapes = switch (type) {
            case ROOM -> NORMAL_SHAPES;
            case PUZZLE -> new RoomShape[]{RoomShape.PUZZLE};
            case TRAP -> new RoomShape[]{RoomShape.TRAP};
            case MINIBOSS -> new RoomShape[]{RoomShape.MINIBOSS};
            default -> new RoomShape[0];
        };
        synchronized (SHAPES) {
            for (RoomShape shape : shapes) {
                if (!SHAPES.containsKey(shape)) return false;
            }
        }
        return true;
    }

    /** 后台加载某类房间模板；重复调用会命中已解压的缓存。 */
    public static void loadAsync(DungeonRoomType type) {
        CompletableFuture.runAsync(() -> roomsFor(type));
    }

    public static List<DungeonRoom> rooms(RoomShape... shapes) {
        ensureIndex();
        if (index == null) return List.of();
        List<DungeonRoom> result = new ArrayList<>();
        synchronized (SHAPES) {
            for (RoomShape shape : shapes) {
                result.addAll(SHAPES.computeIfAbsent(shape, DungeonRoomData::loadShape));
            }
        }
        return result;
    }

    /** 方块名 -> 模板里的数字 id；未知方块返回 -1。 */
    public static int blockId(Block block) {
        ensureIndex();
        synchronized (BLOCK_CACHE) {
            return BLOCK_CACHE.computeIfAbsent(block,
                    key -> BLOCK_IDS.getOrDefault(BuiltInRegistries.BLOCK.getKey(key).toString(), -1));
        }
    }

    private static void ensureIndex() {
        if (index != null) return;
        synchronized (DungeonRoomData.class) {
            if (index != null) return;
            try (InputStream stream = open("index.json")) {
                if (stream == null) {
                    ToolList.getInstance().log.warn("地牢数据缺失：{}index.json", ROOT);
                    return;
                }
                JsonObject parsed = GSON.fromJson(new String(readAll(stream), java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
                JsonObject ids = parsed.getAsJsonObject("blockIds");
                for (Map.Entry<String, JsonElement> entry : ids.entrySet()) {
                    // index.json 里是 数字id -> 方块名，这里反过来存成 方块名 -> 数字id
                    BLOCK_IDS.put(entry.getValue().getAsString(), Integer.parseInt(entry.getKey()));
                }
                index = parsed;
                ToolList.getInstance().log.info("地牢数据索引已加载，方块 id {} 个", BLOCK_IDS.size());
            } catch (Exception e) {
                // 不缓存失败结果，下次调用还会重试（资源可能还没被 processResources 复制出来）
                ToolList.getInstance().log.warn("地牢数据索引加载失败", e);
            }
        }
    }

    private static List<DungeonRoom> loadShape(RoomShape shape) {
        JsonObject shapes = index.getAsJsonObject("shapes");
        JsonArray array = shapes == null ? null : shapes.getAsJsonArray(shape.folder());
        if (array == null) return List.of();

        List<DungeonRoom> rooms = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            String id = object.get("id").getAsString();
            try {
                rooms.add(new DungeonRoom(id, object.get("name").getAsString(), shape,
                        readSecrets(shape, id), readBlocks(shape, id)));
            } catch (Exception e) {
                ToolList.getInstance().log.warn("房间模板加载失败：{}/{}", shape.folder(), id, e);
            }
        }
        ToolList.getInstance().log.info("地牢房间已加载：{} 个（{}）", rooms.size(), shape.folder());
        return List.copyOf(rooms);
    }

    /** 秘密点在每个房间自己的 json 里；索引里只存了数量。 */
    private static List<DungeonRoom.Secret> readSecrets(RoomShape shape, String id) throws Exception {
        try (InputStream stream = open("rooms/" + shape.folder() + "/" + id + ".json")) {
            if (stream == null) return List.of();
            JsonObject object = GSON.fromJson(new String(readAll(stream), java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
            JsonArray array = object.getAsJsonArray("secrets");
            if (array == null) return List.of();
            List<DungeonRoom.Secret> secrets = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                JsonObject secret = element.getAsJsonObject();
                secrets.add(new DungeonRoom.Secret(
                        secret.get("secretName").getAsString(),
                        secret.get("category").getAsString(),
                        secret.get("x").getAsInt(),
                        secret.get("y").getAsInt(),
                        secret.get("z").getAsInt()));
            }
            return secrets;
        }
    }

    private static int[] readBlocks(RoomShape shape, String id) throws Exception {
        try (InputStream stream = open("rooms/" + shape.folder() + "/" + id + ".bin")) {
            if (stream == null) throw new IllegalStateException("缺少 " + id + ".bin");
            try (var data = new java.io.DataInputStream(new GZIPInputStream(stream))) {
                if (data.readInt() != BIN_MAGIC) throw new IllegalStateException("magic 不匹配：" + id);
                if (data.readUnsignedByte() != BIN_VERSION) throw new IllegalStateException("版本不支持：" + id);
                int[] blocks = new int[data.readInt()];
                for (int i = 0; i < blocks.length; i++) blocks[i] = data.readInt();
                return blocks;
            }
        }
    }

    private static InputStream open(String path) {
        return DungeonRoomData.class.getResourceAsStream(ROOT + path);
    }

    private static byte[] readAll(InputStream stream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = stream.read(chunk)) > 0) buffer.write(chunk, 0, read);
        return buffer.toByteArray();
    }
}
