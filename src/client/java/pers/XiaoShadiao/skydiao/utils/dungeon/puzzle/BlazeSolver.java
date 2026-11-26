package pers.XiaoShadiao.skydiao.utils.dungeon.puzzle;

import it.unimi.dsi.fastutil.objects.ObjectDoublePair;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.phys.AABB;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.utils.dungeon.RoomMatcher;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Higher or Lower（Blaze）谜题：按血量高低决定先打哪只。血量最高的一只如果站在中线上方，
 * 就先把最低的几只打下去；反之先把最高的打上来——总之按从低到高 / 从高到低的顺序击杀。
 *
 * <p>搬运自 Skyblocker 的 {@code DungeonBlaze}，但不再解析盔甲架名字：直接找房间里的 Blaze 实体，
 * 用 E2A 映射拿到的怪物血量排序。
 */
public final class BlazeSolver extends DungeonPuzzleSolver {

    /** 血条中线的绝对高度。 */
    private static final int MIDDLE_Y = 69;
    private static final int ROOM_PITCH = 32;

    private Blaze highest;
    private Blaze lowest;
    private Blaze nextHighest;
    private Blaze nextLowest;

    public BlazeSolver() {
        super("blaze-room-1-high", "blaze-room-1-low");
    }

    @Override
    public void tick(Minecraft mc, RoomMatcher.Match room) {
        if (mc.level == null) return;

        AABB roomBox = new AABB(
                room.minX(), 0, room.minZ(),
                room.minX() + room.cellsX() * ROOM_PITCH, 255, room.minZ() + room.cellsZ() * ROOM_PITCH);
        List<ObjectDoublePair<Blaze>> blazes = new ArrayList<>();
        for (Blaze blaze : mc.level.getEntitiesOfClass(Blaze.class, roomBox)) {
            if (!blaze.isAlive()) continue;
            // E2A 映射里没有的实体不是真正的怪物（NPC/装饰）
            E2AMappingListener.MobInfo info = AbstractListener.e2AMappingListener.getMobInfo(blaze);
            if (info == null) continue;
            blazes.add(ObjectDoublePair.of(blaze, info.health));
        }

        blazes.sort(Comparator.comparingDouble(ObjectDoublePair::rightDouble));
        if (blazes.isEmpty()) {
            highest = null;
            lowest = null;
            nextHighest = null;
            nextLowest = null;
            return;
        }
        lowest = blazes.getFirst().left();
        highest = blazes.getLast().left();
        nextLowest = blazes.size() > 1 ? blazes.get(1).left() : null;
        nextHighest = blazes.size() > 1 ? blazes.get(blazes.size() - 2).left() : null;
    }

    @Override
    public void render(LevelRenderContext context, RoomMatcher.Match room) {
        if (highest == null || lowest == null || !highest.isAlive() || !lowest.isAlive()) return;
        if (highest.getY() < MIDDLE_Y) {
            renderPair(context, highest, nextHighest);
        }
        if (lowest.getY() > MIDDLE_Y) {
            renderPair(context, lowest, nextLowest);
        }
    }

    /** 当前该打的那只用绿框，下一只用白框，中间连一条白线。 */
    private static void renderPair(LevelRenderContext context, Blaze blaze, Blaze nextBlaze) {
        RenderUtils.WorldRender line = RenderUtils.createWorldRenderInstance(
                context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        // AABB box = blaze.getBoundingBox().inflate(0.3, 0.9, 0.3).move(0, -1.1, 0);
        RenderUtils.renderESP(line, blaze,
                0f, 1f, 0f, 1f, false);
        if (nextBlaze != null && nextBlaze.isAlive() && nextBlaze != blaze) {
            // AABB nextBox = nextBlaze.getBoundingBox().inflate(0.3, 0.9, 0.3).move(0, -1.1, 0);
            RenderUtils.renderESP(line, nextBlaze,
                    1f, 1f, 1f, 1f, false);
            // Vec3 center = box.getCenter();
            // Vec3 nextCenter = nextBox.getCenter();
            RenderUtils.renderWorldLine(line, blaze.position(), nextBlaze.position(), 1f, 1f, 1f, 1f);
        }
        line.finishDraw();
    }

    @Override
    public void reset() {
        highest = null;
        lowest = null;
        nextHighest = null;
        nextLowest = null;
    }
}
