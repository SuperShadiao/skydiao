package pers.XiaoShadiao.skydiao.utils.dungeon.puzzle;

import it.unimi.dsi.fastutil.objects.ObjectDoublePair;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Intersectiond;
import pers.XiaoShadiao.skydiao.utils.dungeon.RoomMatcher;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Creeper Beams 谜题：把房间里的海晶灯两两连线，共 5 条光束，要求连线离中间苦力怕最近，
 * 且每个海晶灯只连一次。已经连好的光束（两端都变成海晶石）用淡绿色显示。
 *
 * <p>搬运自 Skyblocker 的 {@code CreeperBeams}：以苦力怕脚下的海晶灯为基准，
 * 扫描基准附近 31x31x19 的范围找全部海晶灯，再按“点到线段距离”从小到大贪心选出 5 条互不共点的连线。
 */
public final class CreeperBeamsSolver extends DungeonPuzzleSolver {

    /** 房间地板高度。 */
    private static final int FLOOR_Y = 68;
    /** 苦力怕脚下那层海晶灯的高度。 */
    private static final int BASE_Y = 74;
    private static final int BEAM_COUNT = 5;

    private static final float[][] COLORS = {
            color(DyeColor.LIGHT_BLUE),
            color(DyeColor.LIME),
            color(DyeColor.YELLOW),
            color(DyeColor.MAGENTA),
            color(DyeColor.PINK),
    };

    private final List<Beam> beams = new ArrayList<>();
    private BlockPos base;

    public CreeperBeamsSolver() {
        super("creeper-room", "creeper");
    }

    @Override
    public void tick(Minecraft mc, RoomMatcher.Match room) {
        if (mc.level == null || mc.player == null) return;

        if (base == null) {
            base = findBase(mc.player, mc.level);
            if (base == null) return;
            Vec3 creeperPos = new Vec3(base.getX() + 0.5, BASE_Y + 1.75, base.getZ() + 0.5);
            beams.clear();
            beams.addAll(findLines(creeperPos, findTargets(mc.level, base)));
        }

        for (Beam beam : beams) beam.updateState(mc.level);
        // 基准方块没了说明房间已经完成/重置
        if (!isTarget(mc.level, base)) reset();
    }

    @Override
    public void render(LevelRenderContext context, RoomMatcher.Match room) {
        // 已经连好的光束不再渲染
        boolean anyToDo = false;
        for (Beam beam : beams) {
            if (beam.toDo) {
                anyToDo = true;
                break;
            }
        }
        if (!anyToDo) return;
        RenderUtils.WorldRender line = RenderUtils.createWorldRenderInstance(
                context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        for (int i = 0; i < beams.size(); i++) {
            if (beams.get(i).toDo) beams.get(i).render(line, COLORS[i]);
        }
        line.finishDraw();
    }

    @Override
    public void reset() {
        beams.clear();
        base = null;
    }

    /** 找苦力怕脚下那块海晶灯/海晶石。 */
    private static BlockPos findBase(LocalPlayer player, ClientLevel level) {
        List<Creeper> creepers = level.getEntitiesOfClass(Creeper.class,
                player.getBoundingBox().inflate(50), EntitySelector.ENTITY_STILL_ALIVE);
        for (Creeper creeper : creepers) {
            Vec3 pos = creeper.position();
            BlockPos potentialBase = BlockPos.containing(pos.x, BASE_Y, pos.z);
            if (isTarget(level, potentialBase)) return potentialBase;
        }
        return null;
    }

    /** 扫描基准周围全部海晶灯（以及 Hypixel 偶尔放的那一块海晶石）。 */
    private static List<BlockPos> findTargets(ClientLevel level, BlockPos base) {
        List<BlockPos> targets = new ArrayList<>();
        BlockPos start = new BlockPos(base.getX() - 15, BASE_Y + 12, base.getZ() - 15);
        BlockPos end = new BlockPos(base.getX() + 16, FLOOR_Y, base.getZ() + 16);
        for (BlockPos pos : BlockPos.betweenClosed(start, end)) {
            if (isTarget(level, pos)) targets.add(pos.immutable());
        }
        return targets;
    }

    /** 所有两点连线按“苦力怕到线段距离”排序，贪心取 5 条互不共点的。 */
    private static List<Beam> findLines(Vec3 creeperPos, List<BlockPos> targets) {
        List<ObjectDoublePair<Beam>> allLines = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            for (int j = i + 1; j < targets.size(); j++) {
                Beam beam = new Beam(targets.get(i), targets.get(j));
                double distance = Intersectiond.distancePointLine(
                        creeperPos.x, creeperPos.y, creeperPos.z,
                        beam.start.x, beam.start.y, beam.start.z,
                        beam.end.x, beam.end.y, beam.end.z);
                allLines.add(ObjectDoublePair.of(beam, distance));
            }
        }
        allLines.sort(Comparator.comparingDouble(ObjectDoublePair::rightDouble));

        List<Beam> result = new ArrayList<>(BEAM_COUNT);
        while (result.size() < BEAM_COUNT && !allLines.isEmpty()) {
            Beam solution = allLines.getFirst().left();
            result.add(solution);
            allLines.removeFirst();
            // 已经用过的海晶灯不能再连第二次
            allLines.removeIf(line -> solution.sharesTarget(line.left()));
        }
        return result;
    }

    private static boolean isTarget(ClientLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.SEA_LANTERN)
                || level.getBlockState(pos).is(Blocks.PRISMARINE);
    }

    private static float[] color(DyeColor dye) {
        int rgb = dye.getTextureDiffuseColor();
        return new float[]{
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f
        };
    }

    /** 一条光束：两个端点方块 + 渲染用的线段。 */
    private static final class Beam {

        private final BlockPos one;
        private final BlockPos two;
        private final Vec3 start;
        private final Vec3 end;
        /** true 表示还没连（需要玩家去点），false 表示已经连好。 */
        private boolean toDo = true;

        private Beam(BlockPos one, BlockPos two) {
            this.one = one;
            this.two = two;
            this.start = new Vec3(one.getX() + 0.5, one.getY() + 0.5, one.getZ() + 0.5);
            this.end = new Vec3(two.getX() + 0.5, two.getY() + 0.5, two.getZ() + 0.5);
        }

        private boolean sharesTarget(Beam other) {
            return one.equals(other.one) || one.equals(other.two)
                    || two.equals(other.one) || two.equals(other.two);
        }

        /** 两端都变成海晶石就算连好了。 */
        private void updateState(ClientLevel level) {
            toDo = !(level.getBlockState(one).is(Blocks.PRISMARINE)
                    && level.getBlockState(two).is(Blocks.PRISMARINE));
        }

        private void render(RenderUtils.WorldRender render, float[] color) {
            RenderUtils.renderESP(render, one, color[0], color[1], color[2], 1f, false, true);
            RenderUtils.renderESP(render, two, color[0], color[1], color[2], 1f, false, true);
            RenderUtils.renderWorldLine(render, start, end, color[0], color[1], color[2], 1f);
        }
    }
}
