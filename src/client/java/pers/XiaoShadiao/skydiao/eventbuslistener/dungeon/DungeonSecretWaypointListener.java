package pers.XiaoShadiao.skydiao.eventbuslistener.dungeon;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.hud.StarRailNotification;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.dungeon.DungeonCheckmark;
import pers.XiaoShadiao.skydiao.utils.dungeon.DungeonRoom;
import pers.XiaoShadiao.skydiao.utils.dungeon.RoomMatcher;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 当前房间的秘密点 waypoint：方块用 ESP 标出来，上方用固定屏幕大小的文字标名字和距离。
 *
 * <p>秘密点数据由 {@link DungeonMapScanListener} 在扫描线程里按房间朝向换算成世界坐标，
 * 这里只负责渲染和“已拿到”的判定，避免渲染线程再去查房间数据。
 *
 * <p>拿到判定：
 * <ul>
 *   <li>箱子：方块是箱子且已经打开；</li>
 *   <li>精华：右键后头颅方块变成空气；</li>
 *   <li>拉杆：右键后立即算拿掉；</li>
 *   <li>蝙蝠：玩家在 3 格内待满 2 秒，或附近有死掉的蝙蝠；</li>
 *   <li>物品：玩家在 3 格内待满 2 秒，或附近的掉落物实体消失。</li>
 * </ul>
 * 秘密拿掉后，同一编号（名字前缀，如 "2/3 - Lever" 的 2 和 3）的所有节点一并隐藏。
 */
public class DungeonSecretWaypointListener extends AbstractListener {

    /** 玩家在秘密点 3 格内连续待满这么多 tick（2 秒）就算拿到。 */
    private static final int NEAR_TICKS = 40;
    /** “2 格内”的平方距离。 */
    private static final double NEAR_DISTANCE_SQ = 3 * 3;
    /** 右键后保留判定的时长（毫秒）；10 秒内还没被消费的点击直接移除。 */
    private static final long CLICK_TTL_MILLIS = 10_000L;

    /** 最近右键过的方块：key = 点击时间（毫秒），value = 方块位置；10 秒内没被消费的点击直接移除。 */
    private final Long2ObjectMap<BlockPos> recentClicks = new Long2ObjectOpenHashMap<>();
    /** 已经完成的秘密编号。 */
    private final Set<String> completed = new HashSet<>();
    /** 单独排除的节点（拉杆、没有编号的节点）。 */
    private final Set<BlockPos> excluded = new HashSet<>();
    /** 玩家在某个秘密点 3 格内连续待了多少 tick。 */
    private final Object2IntMap<BlockPos> nearTicks = new Object2IntOpenHashMap<>();
    /** 当前房间锚点（minX:minZ），换房间时重置勾状态跟踪。 */
    private String lastRoomScope;
    /** 上一次看到的当前房间勾状态，用来检测“灰 -> 白”。 */
    private DungeonCheckmark lastCheckmark = DungeonCheckmark.NONE;
    /** 已经提示过“清理完毕”的房间，避免重复提示。 */
    private String notifiedRoom;
    /** 已经提示过“秘密找完”的房间。 */
    private String notifiedSecretsRoom;
    /** onTick 里算好的可见秘密，渲染线程只遍历这个列表，避免每帧重算完成状态。 */
    private volatile List<DungeonMapScanListener.Snapshot.SecretView> visibleSecrets = List.of();
    /** 秘密名标签缓存：key = 类别 + 名字，颜色只跟类别有关。 */
    private static final Map<String, Component> SECRET_LABELS = new HashMap<>();

    @Override
    public String getListenerName() {
        return "DungeonSecretWaypoint";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ClientEntityEvents.ENTITY_UNLOAD.register(this::onEntityUnload);
        CustomFabricEvents.ON_INGAME_CLICK.register(this::onIngameClick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((mc, level) -> reset());
    }

    private void reset() {
        recentClicks.clear();
        completed.clear();
        excluded.clear();
        nearTicks.clear();
        lastRoomScope = null;
        lastCheckmark = DungeonCheckmark.NONE;
        notifiedRoom = null;
        notifiedSecretsRoom = null;
        visibleSecrets = List.of();
    }

    private void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        recentClicks.long2ObjectEntrySet().removeIf(entry -> now - entry.getLongKey() > CLICK_TTL_MILLIS);
        DungeonMapScanListener.Snapshot snapshot = DungeonMapScanListener.getCurrent();
        if (snapshot == null) {
            nearTicks.clear();
            visibleSecrets = List.of();
            return;
        }
        checkRoomCleared(snapshot);
        if (snapshot.secrets().isEmpty()) {
            nearTicks.clear();
            visibleSecrets = List.of();
            return;
        }

        for (DungeonMapScanListener.Snapshot.SecretView secret : snapshot.secrets()) {
            BlockPos pos = new BlockPos(secret.x(), secret.y(), secret.z());
            if (isHidden(snapshot, secret)) continue;
            switch (secret.category()) {
                case "chest" -> {
                    BlockEntity blockEntity = mc.level.getBlockEntity(pos);
                    if (blockEntity instanceof ChestBlockEntity chest && chest.getOpenNess(0f) > 0.01f) {
                        completeSecret(snapshot, secret);
                    }
                }
                case "bat" -> {
                    if (deadBatNear(mc.level, pos) || nearLongEnough(mc, secret)) completeSecret(snapshot, secret);
                }
                case "item" -> {
                    if (nearLongEnough(mc, secret)) completeSecret(snapshot, secret);
                }
                case "lever" -> {
                    if (consumeClick(pos)) excluded.add(pos);
                }
                case "prince" -> {
                    // Prince 不是秘密，房间秘密找完后仍要显示，只有靠近判定才隐藏
                    if (nearLongEnough(mc, secret)) excluded.add(pos);
                }
                case "wither", "essence" -> {
                    // 头颅被右键后变成空气才算真正拿到
                    if (mc.level.getBlockState(pos).isAir() && consumeClick(pos)) {
                        completeSecret(snapshot, secret);
                    }
                }
                default -> {
                }
            }
        }

        // 每 tick 算一次可见列表，渲染线程不再重复做编号解析
        List<DungeonMapScanListener.Snapshot.SecretView> visible = new ArrayList<>(snapshot.secrets().size());
        for (DungeonMapScanListener.Snapshot.SecretView secret : snapshot.secrets()) {
            if (!isHidden(snapshot, secret)) visible.add(secret);
        }
        visibleSecrets = visible;
    }

    /**
     * 只在玩家当前所在的房间里检测勾状态变化：别的房间（包括别人正在清理或已经清完）变白/变绿都不提示。
     * 刚进房间时只记录初始状态，避免走入已经清理过的房间也弹提示。
     */
    private void checkRoomCleared(DungeonMapScanListener.Snapshot snapshot) {
        RoomMatcher.Match room = snapshot.room();
        if (room == null) {
            lastRoomScope = null;
            lastCheckmark = DungeonCheckmark.NONE;
            return;
        }
        String scope = room.minX() + ":" + room.minZ();
        if (!scope.equals(lastRoomScope)) {
            // 换房间/刚进房：只记初始勾状态，不触发提示
            lastRoomScope = scope;
            lastCheckmark = snapshot.checkmark();
            return;
        }
        DungeonCheckmark checkmark = snapshot.checkmark();
        if (lastCheckmark == DungeonCheckmark.NONE && checkmark == DungeonCheckmark.WHITE
                && !scope.equals(notifiedRoom)) {
            notifiedRoom = scope;
            if (ConfigManager.dungeonTipIfCleared.getValue()) {
                XSDHUD.starRailNotification.updateMessage("当前房间已清理完毕!", StarRailNotification.Type.success);
            }
        }
        if ((lastCheckmark == DungeonCheckmark.NONE || lastCheckmark == DungeonCheckmark.WHITE)
                && checkmark == DungeonCheckmark.GREEN && !scope.equals(notifiedSecretsRoom)) {
            notifiedSecretsRoom = scope;
            if (ConfigManager.dungeonTipIfAllSecretFound.getValue()) {
                XSDHUD.starRailNotification.updateMessage("当前房间的秘密已寻找完毕!", StarRailNotification.Type.success);
            }
        }
        lastCheckmark = checkmark;
    }

    /**
     * 找到并移除这个方块最近的点击记录；命中且未超时才返回 {@code true}。
     * 点击记录很少，直接遍历即可。
     */
    private boolean consumeClick(BlockPos pos) {
        long now = System.currentTimeMillis();
        var iterator = recentClicks.keySet().longIterator();
        while (iterator.hasNext()) {
            long time = iterator.nextLong();
            if (!pos.equals(recentClicks.get(time))) continue;
            iterator.remove();
            return now - time <= CLICK_TTL_MILLIS;
        }
        return false;
    }

    /** 玩家在秘密点 3 格内连续待满 2 秒。 */
    private boolean nearLongEnough(Minecraft mc, DungeonMapScanListener.Snapshot.SecretView secret) {
        BlockPos key = new BlockPos(secret.x(), secret.y(), secret.z());
        double distanceSq = mc.player.distanceToSqr(secret.x() + 0.5, secret.y() + 0.5, secret.z() + 0.5);
        if (distanceSq > NEAR_DISTANCE_SQ) {
            nearTicks.removeInt(key);
            return false;
        }
        int ticks = nearTicks.getInt(key) + 1;
        nearTicks.put(key, ticks);
        return ticks >= NEAR_TICKS;
    }

    /** 秘密点 3 格内有没有已经死亡的蝙蝠。 */
    private static boolean deadBatNear(ClientLevel level, BlockPos pos) {
        for (Bat bat : level.getEntitiesOfClass(Bat.class, new AABB(pos).inflate(3))) {
            if (bat.isDeadOrDying()) return true;
        }
        return false;
    }

    /** 附近的掉落物实体消失（被捡起或移除）时，认为物品秘密已拿到。 */
    private void onEntityUnload(Entity entity, ClientLevel level) {
        if (!(entity instanceof ItemEntity)) return;
        // 玩家必须在秘密点 3 格内，才算“自己捡到了”；否则别处物品/区块卸载会误排除
        if (mc.player == null) return;
        DungeonMapScanListener.Snapshot snapshot = DungeonMapScanListener.getCurrent();
        if (snapshot == null) return;
        for (DungeonMapScanListener.Snapshot.SecretView secret : snapshot.secrets()) {
            if (!"item".equals(secret.category())) continue;
            if (mc.player.distanceToSqr(secret.x() + 0.5, secret.y() + 0.5, secret.z() + 0.5) > NEAR_DISTANCE_SQ) {
                continue;
            }
            if (entity.distanceToSqr(secret.x() + 0.5, secret.y() + 0.5, secret.z() + 0.5) <= NEAR_DISTANCE_SQ) {
                completeSecret(snapshot, secret);
                return;
            }
        }
    }

    /** 记录右键的方块；拉杆/精华的判定都靠它。 */
    private boolean onIngameClick(CustomFabricEvents.ClickType clickType) {
        if (clickType == CustomFabricEvents.ClickType.RIGHT && mc.hitResult instanceof BlockHitResult hit) {
            recentClicks.put(System.currentTimeMillis(), hit.getBlockPos().immutable());
        }
        return false;
    }

    /**
     * 秘密拿到后，把它所属的编号记为已完成。
     *
     * <p>名字形如 {@code "2/3 - Lever"}，斜杠前是它所属的秘密编号。编号只在当前房间内有效，
     * 所以按房间锚点区分；节点只有在<b>全部</b>编号都完成时才隐藏，{@code "2/3 - Lever"} 要等
     * 秘密 2 和 3 都拿到才消失。没有编号的节点只排除自己。
     */
    private void completeSecret(DungeonMapScanListener.Snapshot snapshot,
                                DungeonMapScanListener.Snapshot.SecretView trigger) {
        Set<String> tokens = DungeonRoom.secretGroups(trigger.name());
        if (tokens.isEmpty()) {
            excluded.add(new BlockPos(trigger.x(), trigger.y(), trigger.z()));
            return;
        }
        String scope = scope(snapshot);
        for (String token : tokens) completed.add(scope + "#" + token);
    }

    /** 秘密节点是否已经不需要再画了。 */
    private boolean isHidden(DungeonMapScanListener.Snapshot snapshot,
                             DungeonMapScanListener.Snapshot.SecretView secret) {
        if (excluded.contains(new BlockPos(secret.x(), secret.y(), secret.z()))) return true;
        // Prince 不是秘密，不受房间秘密完成状态影响
        if ("prince".equals(secret.category())) return false;
        Set<String> tokens = DungeonRoom.secretGroups(secret.name());
        if (tokens.isEmpty()) return false;
        String scope = scope(snapshot);
        for (String token : tokens) {
            if (!completed.contains(scope + "#" + token)) return false;
        }
        return true;
    }

    /** 秘密编号的作用域：同一房间里编号才是同一套。 */
    private static String scope(DungeonMapScanListener.Snapshot snapshot) {
        RoomMatcher.Match match = snapshot.room();
        return match == null ? "-" : match.minX() + ":" + match.minZ();
    }

    private void onLastRender(LevelRenderContext context) {
        if (mc.level == null || mc.player == null) return;
        int mode = ConfigManager.dungeonSecretWaypoint.getValue();
        List<DungeonMapScanListener.Snapshot.SecretView> visible = visibleSecrets;
        if (mode == 0 || visible.isEmpty()) return;

        // 方块高亮：填充和描边各用一条管线，所有秘密点合并成两次绘制
        if (mode == 2 || mode == 3) {
            RenderUtils.WorldRender fill = RenderUtils.createWorldRenderInstance(
                    context, CustomRenderPipeline.THROUGH_WALLS_FILL);
            RenderUtils.WorldRender line = RenderUtils.createWorldRenderInstance(
                    context, CustomRenderPipeline.THROUGH_WALLS_LINE);
            for (DungeonMapScanListener.Snapshot.SecretView secret : visible) {
                int rgb = colorFor(secret);
                float r = ((rgb >> 16) & 0xFF) / 255f;
                float g = ((rgb >> 8) & 0xFF) / 255f;
                float b = (rgb & 0xFF) / 255f;
                BlockPos pos = new BlockPos(secret.x(), secret.y(), secret.z());
                RenderUtils.renderESP(fill, pos, r, g, b, 0.45f, true);
                RenderUtils.renderESP(line, pos, r, g, b, 1f, false);
            }
            fill.finishDraw();
            line.finishDraw();
        }

        // 文字标签：独立渲染，保证大小不随距离变化
        if (mode == 1 || mode == 3) {
            for (DungeonMapScanListener.Snapshot.SecretView secret : visible) {
                double distance = Math.sqrt(mc.player.distanceToSqr(
                        secret.x() + 0.5, secret.y() + 0.5, secret.z() + 0.5));
                int rgb = colorFor(secret);
                Component nameLabel = SECRET_LABELS.computeIfAbsent(
                        rgb + "\u0000" + secret.name(),
                        key -> Component.literal(secret.name()).withStyle(style -> style.withColor(rgb)));
                Component label = Component.empty()
                        .append(nameLabel)
                        .append(Component.literal(" " + Math.round(distance) + "m")
                                .withStyle(ChatFormatting.GRAY));
                RenderUtils.renderWaypoint(context,
                        new Vec3(secret.x() + 0.5, secret.y() + 1.4, secret.z() + 0.5), label, 1.5f);
            }
        }
    }

    /** 陷阱箱在服务端用的是原版 TRAPPED_CHEST 方块，单独标成深红。 */
    private int colorFor(DungeonMapScanListener.Snapshot.SecretView secret) {
        if ("chest".equals(secret.category()) && mc.level != null
                && mc.level.getBlockState(new BlockPos(secret.x(), secret.y(), secret.z())).is(Blocks.TRAPPED_CHEST)) {
            return 0xFF8B0000;
        }
        return color(secret.category());
    }

    /** 秘密类别对应的 waypoint 颜色，参照 Skyblocker 的分类习惯。 */
    private static int color(String category) {
        return switch (category) {
            case "chest" -> 0xFFFFAA00;
            case "item" -> 0xFFFFFF55;
            case "superboom" -> 0xFFFF5555;
            case "wither" -> 0xFFB45AFF;
            case "lever" -> 0xFF55FFFF;
            case "bat" -> 0xFFAA55FF;
            case "stonk" -> 0xFF55AAFF;
            case "prince" -> 0xFFFF55FF;
            case "aotv" -> 0xFF55FF55;
            case "fairysoul" -> 0xFFFF88DD;
            case "key" -> 0xFFFFD700;
            case "pearl" -> 0xFFAA00FF;
            case "entrance" -> 0xFFAAAAAA;
            default -> 0xFFFFFFFF;
        };
    }
}
