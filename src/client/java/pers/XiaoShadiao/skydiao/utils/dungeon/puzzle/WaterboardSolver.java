package pers.XiaoShadiao.skydiao.utils.dungeon.puzzle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.dungeon.RoomMatcher;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Water Board 谜题（Skyblocker 的 OneFlow 方案）：按预先算好的时间表依次点击拉杆，让水一次流过所有机关。
 *
 * <p>搬运自 Skyblocker 的 {@code Waterboard} + {@code WaterboardOneFlow}。棋盘是相对坐标里
 * z=26 的一整面墙（x 6~24、y 58~81），水从 (15,78,26) 上方流入。房间有 4 种布局、5 扇门（其中 3 扇
 * 初始关闭），根据布局和门组合从 {@code watertimes.json} 里取出每个拉杆的点击时间。
 *
 * <p>时间以 50ms/tick 累计；点水拉杆开始计时，之后每个拉杆上方显示剩余时间，到点显示 CLICK。
 */
public final class WaterboardSolver extends DungeonPuzzleSolver {

    private static final int BOARD_MIN_X = 6;
    private static final int BOARD_MAX_X = 24;
    private static final int BOARD_MIN_Y = 58;
    private static final int BOARD_MAX_Y = 81;
    private static final int BOARD_Z = 26;
    /** 水流入的位置（棋盘顶部正中，两个初始机关之间）。 */
    private static final BlockPos WATER_ENTRANCE = new BlockPos(15, 78, 26);
    private static final String WATER_TIMES = "/assets/skydiao/dungeons/watertimes.json";

    private static final float[] GREEN = color(DyeColor.LIME);
    private static final float[] WHITE = color(DyeColor.WHITE);
    private static final float[] WATER_BLUE = color(DyeColor.LIGHT_BLUE);

    private static JsonObject solutions;
    /** 每局地牢只在第一次进水房时提示一次。 */
    private static boolean announced;

    private ClientLevel world;
    private RoomMatcher.Match currentRoom;
    private int variant;
    private String doors;
    private String initialDoors;
    private EnumMap<LeverType, DoubleList> solution;
    private boolean finished;
    private boolean attempted;
    private long waterStartMillis;
    private long currentTimeMillis;
    /** 每 tick 预计算好的预览：准星指向的机关、水流线段、会被推出/收回的方块。 */
    private LeverType prospective;
    private List<Line> waterLines = List.of();
    private List<BlockPos> previewOutline = List.of();
    private List<BlockPos> previewFill = List.of();

    private record Line(Vec3 head, Vec3 tail) {
    }

    public WaterboardSolver() {
        super("water-puzzle");
    }

    @Override
    public void tick(Minecraft mc, RoomMatcher.Match room) {
        if (mc.level == null || mc.player == null) return;
        world = mc.level;
        currentRoom = room;
        currentTimeMillis += 50;

        if (!announced) {
            announced = true;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e注意: 由于网络延迟和服务器卡顿因素, 水房解密可能不能完好地工作, 因此如果按照提示操作后未能解出, 可以通过预览一个一个地打开活塞完成解密"));
        }

        // 每个房间只解一次：布局、门和拉杆时间都是固定的
        if (!attempted && !finished && solution == null) {
            attempted = true;
            try {
                solvePuzzle();
            } catch (Exception e) {
                ToolList.getInstance().log.warn("水房解算失败", e);
                finished = true;
            }
        }

        if (!finished && isPuzzleSolved()) finished = true;

        // 预览数据每 tick 算一次，渲染线程只负责画缓存，避免每帧 900+ 次 getBlockState
        updatePreview(room);
    }

    private void updatePreview(RoomMatcher.Match room) {
        prospective = findProspective(room);
        waterLines = buildWaterPath(room, prospective);

        if (prospective == null) {
            previewOutline = List.of();
            previewFill = List.of();
            return;
        }
        Block block = prospective.block;
        List<BlockPos> outline = new ArrayList<>();
        List<BlockPos> fill = new ArrayList<>();
        for (int x = BOARD_MIN_X; x <= BOARD_MAX_X; x++) {
            for (int y = BOARD_MIN_Y; y <= BOARD_MAX_Y; y++) {
                BlockPos active = room.relativeToActual(new BlockPos(x, y, BOARD_Z));
                BlockPos inactive = room.relativeToActual(new BlockPos(x, y, BOARD_Z + 1));
                if (world.getBlockState(active).is(block)) {
                    outline.add(active);
                } else if (world.getBlockState(inactive).is(block)) {
                    fill.add(active);
                }
            }
        }
        previewOutline = outline;
        previewFill = fill;
    }

    private void solvePuzzle() {
        variant = findVariant();
        if (variant == 0) {
            finished = true;
            return;
        }

        initialDoors = findDoors();
        if (doors == null) {
            // 默认目标是打开所有门
            doors = initialDoors;
            if (doors.isEmpty()) {
                solution = emptySolution();
                finished = true;
                return;
            }
            if (doors.length() != 3) {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §c水房门的状态异常（关闭的门不是 3 扇），请重置谜题"));
                finished = true;
                return;
            }
        }

        if (!checkWater()) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c水房里已经有水了，请先把谜题重置"));
            finished = true;
            return;
        }

        JsonObject root = solutions();
        if (root == null) {
            finished = true;
            return;
        }
        JsonElement variantData = root.get(String.valueOf(variant));
        JsonElement doorData = variantData == null || !variantData.isJsonObject()
                ? null : variantData.getAsJsonObject().get(doors);
        if (doorData == null || !doorData.isJsonObject()) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c没有找到这组门的水房解法"));
            finished = true;
            return;
        }
        solution = setupSolution(doorData.getAsJsonObject());
    }

    private static JsonObject solutions() {
        if (solutions != null) return solutions;
        try (InputStream stream = WaterboardSolver.class.getResourceAsStream(WATER_TIMES)) {
            if (stream == null) {
                ToolList.getInstance().log.warn("水房解法缺失：{}", WATER_TIMES);
                return null;
            }
            solutions = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            return solutions;
        } catch (Exception e) {
            ToolList.getInstance().log.warn("水房解法加载失败", e);
            return null;
        }
    }

    private EnumMap<LeverType, DoubleList> emptySolution() {
        EnumMap<LeverType, DoubleList> result = new EnumMap<>(LeverType.class);
        for (LeverType leverType : LeverType.values()) result.put(leverType, new DoubleArrayList());
        return result;
    }

    /** 水房只有 4 种布局，靠入口处两个初始机关区分（第一种布局整体矮一格）。 */
    private int findVariant() {
        Set<LeverType> firstSwitches = new HashSet<>();
        AABB firstSwitchBlocks = AABB.encapsulatingFullBlocks(
                relative(WATER_ENTRANCE.offset(-1, -1, 0)),
                relative(WATER_ENTRANCE.offset(1, 0, 1)));
        for (BlockState state : world.getBlockStates(firstSwitchBlocks).toList()) {
            LeverType leverType = LeverType.fromBlock(state.getBlock());
            if (leverType != null) firstSwitches.add(leverType);
        }

        if (firstSwitches.contains(LeverType.GOLD) && firstSwitches.contains(LeverType.TERRACOTTA)) return 1;
        if (firstSwitches.contains(LeverType.EMERALD) && firstSwitches.contains(LeverType.QUARTZ)) return 2;
        if (firstSwitches.contains(LeverType.QUARTZ) && firstSwitches.contains(LeverType.DIAMOND)) return 3;
        if (firstSwitches.contains(LeverType.GOLD) && firstSwitches.contains(LeverType.QUARTZ)) return 4;
        return 0;
    }

    /** 5 扇门从 (15,57,19) 往北排，非空气表示关着；返回关闭门的编号串。 */
    private String findDoors() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (!world.getBlockState(relative(new BlockPos(15, 57, 19 - i))).isAir()) {
                builder.append(i);
            }
        }
        return builder.toString();
    }

    private boolean checkWater() {
        for (int x = BOARD_MIN_X; x <= BOARD_MAX_X; x++) {
            for (int y = BOARD_MIN_Y; y <= BOARD_MAX_Y; y++) {
                if (world.getBlockState(relative(new BlockPos(x, y, BOARD_Z))).is(Blocks.WATER)) return false;
            }
        }
        return true;
    }

    private EnumMap<LeverType, DoubleList> setupSolution(JsonObject data) {
        EnumMap<LeverType, DoubleList> result = emptySolution();
        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            LeverType leverType = LeverType.fromName(entry.getKey());
            if (leverType == null) continue;
            DoubleList times = new DoubleArrayList();
            for (JsonElement element : entry.getValue().getAsJsonArray()) times.add(element.getAsDouble());
            result.put(leverType, times);
        }

        // 如果进房前已经有拉杆是激活状态，时间表里要补一次 0 秒把它拨回去
        for (LeverType leverType : LeverType.values()) {
            DoubleList times = result.get(leverType);
            if (leverType != LeverType.WATER && isLeverActive(leverType)) {
                if (times.isEmpty() || times.getFirst() != 0.0) {
                    times.addFirst(0.0);
                } else {
                    times.removeFirst();
                }
            }
        }
        return result;
    }

    private boolean isLeverActive(LeverType leverType) {
        BlockPos offset = leverType.initialPositions[variant - 1];
        if (offset == null) return false;
        return !world.getBlockState(relative(WATER_ENTRANCE.offset(offset))).is(leverType.block);
    }

    /** 每扇门的状态应该都和初始相反（初始关的变开、初始开的变关）才算完成。 */
    private boolean isPuzzleSolved() {
        if (doors == null || initialDoors == null || waterStartMillis == 0) return false;
        String currentDoors = findDoors();
        for (int i = 0; i < 5; i++) {
            String s = String.valueOf(i);
            boolean initial = initialDoors.contains(s);
            boolean current = currentDoors.contains(s);
            boolean expected = doors.contains(s);
            if ((initial == current) == expected) return false;
        }
        return true;
    }

    @Override
    public void onUseBlock(BlockPos pos, RoomMatcher.Match room) {
        if (solution == null || world == null) return;
        LeverType leverType = LeverType.fromPos(room.actualToRelative(pos));
        if (leverType == null) return;

        DoubleList times = solution.get(leverType);
        if (waterStartMillis == 0 && leverType != LeverType.WATER && (times.isEmpty() || times.getFirst() != 0.0)) {
            // 水还没开始就点了不该点的拉杆，提示把它拨回来
            times.addFirst(0.0);
        } else {
            if (!times.isEmpty()) times.removeFirst();
            if (waterStartMillis == 0 && leverType == LeverType.WATER) waterStartMillis = currentTimeMillis;
        }
    }

    @Override
    public void render(LevelRenderContext context, RoomMatcher.Match room) {
        if (world == null) return;
        renderWaterPath(context, room);
        renderProspectiveChanges(context, room);
        renderSolution(context, room);
    }

    /** 预计算水流线段，渲染时直接复用。 */
    private List<Line> buildWaterPath(RoomMatcher.Match room, LeverType prospective) {
        List<BlockPair> path = new ArrayList<>();
        path.add(new BlockPair(WATER_ENTRANCE.above(5), WATER_ENTRANCE.above(3)));
        findWaterPathVertical(room, prospective, WATER_ENTRANCE.above(3), path);
        List<Line> lines = new ArrayList<>(path.size());
        for (BlockPair pair : path) {
            lines.add(new Line(Vec3.atCenterOf(room.relativeToActual(pair.start())),
                    Vec3.atCenterOf(room.relativeToActual(pair.end()))));
        }
        return lines;
    }

    /** 水会从哪里流到哪里的预览；看着机关时用虚线表示拨动后的流向。 */
    private void renderWaterPath(LevelRenderContext context, RoomMatcher.Match room) {
        if (waterLines.isEmpty()) return;
        RenderUtils.WorldRender line = RenderUtils.createWorldRenderInstance(
                context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        for (Line segment : waterLines) {
            Vec3 head = segment.head();
            Vec3 tail = segment.tail();
            if (prospective == null) {
                RenderUtils.renderWorldLine(line, head, tail, WATER_BLUE[0], WATER_BLUE[1], WATER_BLUE[2], 1f);
                continue;
            }
            // 有预测机关时画成虚线，避免和“当前水流”混淆
            Vec3 forward = tail.subtract(head).normalize();
            double distance = head.distanceTo(tail);
            for (int i = 0; i < distance; i++) {
                RenderUtils.renderWorldLine(line, head, head.add(forward.scale(0.3)),
                        WATER_BLUE[0], WATER_BLUE[1], WATER_BLUE[2], 1f);
                RenderUtils.renderWorldLine(line, head.add(forward.scale(0.7)), head.add(forward),
                        WATER_BLUE[0], WATER_BLUE[1], WATER_BLUE[2], 1f);
                head = head.add(forward);
            }
        }
        line.finishDraw();
    }

    /** 看板上某个机关方块时，把会被推出的位置填色、会被收回的位置描边（数据每 tick 预算好）。 */
    private void renderProspectiveChanges(LevelRenderContext context, RoomMatcher.Match room) {
        if (prospective == null || (previewOutline.isEmpty() && previewFill.isEmpty())) return;
        float[] color = color(prospective.color);
        RenderUtils.WorldRender fill = RenderUtils.createWorldRenderInstance(
                context, CustomRenderPipeline.THROUGH_WALLS_FILL);
        RenderUtils.WorldRender line = RenderUtils.createWorldRenderInstance(
                context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        for (BlockPos pos : previewOutline) {
            RenderUtils.renderESP(line, pos, color[0], color[1], color[2], 1f, false);
        }
        for (BlockPos pos : previewFill) {
            // 推出后的位置现在可能是水（碰撞箱为空），必须按整格画，否则什么都看不到
            RenderUtils.renderESP(fill, pos, color[0], color[1], color[2], 0.8f, true);
        }
        fill.finishDraw();
        line.finishDraw();
    }

    /** 射线打在棋盘上，返回准星指着的机关（包括缩在棋盘后面一格的机关）。 */
    private LeverType findProspective(RoomMatcher.Match room) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        Vec3 camera = room.actualToRelative(mc.player.getEyePosition());
        Vec3 look = room.actualToRelative(mc.player.getEyePosition().add(mc.player.getLookAngle())).subtract(camera);
        if (Math.abs(look.z) < 1.0E-6) return null;
        double t1 = (BOARD_Z - 1.5 - camera.z) / look.z;
        double t2 = (BOARD_Z + 1.5 - camera.z) / look.z;
        Vec3 start = camera.add(look.scale(t1));
        Vec3 end = camera.add(look.scale(t2));
        double distance = start.distanceTo(end);
        if (distance <= 0) return null;
        Vec3 direction = end.subtract(start).scale(1 / distance);
        for (double step = 0; step <= distance; step += 0.02) {
            BlockPos relative = BlockPos.containing(start.add(direction.scale(step)));
            if (relative.getZ() != BOARD_Z) continue;
            LeverType leverType = LeverType.fromBlock(
                    world.getBlockState(room.relativeToActual(relative)).getBlock());
            if (leverType == null) {
                leverType = LeverType.fromBlock(
                        world.getBlockState(room.relativeToActual(relative.offset(0, 0, 1))).getBlock());
            }
            if (leverType != null) return leverType;
        }
        return null;
    }

    private void findWaterPathVertical(RoomMatcher.Match room, LeverType prospective, BlockPos root,
                                       List<BlockPair> path) {
        if (!isWaterPassable(room, prospective, root.below())) return;
        BlockPos.MutableBlockPos tail = root.mutable();
        tail.move(Direction.DOWN);
        while (isWaterPassable(room, prospective, tail.below())) tail.move(Direction.DOWN);
        path.add(new BlockPair(root, tail.immutable()));
        findWaterPathHorizontal(room, prospective, tail.immutable(), path);
    }

    private void findWaterPathHorizontal(RoomMatcher.Match room, LeverType prospective, BlockPos root,
                                         List<BlockPair> path) {
        if (isWaterPassable(room, prospective, root.below())) return;
        BlockPos.MutableBlockPos left = root.mutable();
        int leftSteps = 0;
        while (isWaterPassable(room, prospective, left.east())
                && !isWaterPassable(room, prospective, left.below()) && leftSteps < 7) {
            left.move(Direction.EAST);
            leftSteps++;
        }
        BlockPos.MutableBlockPos right = root.mutable();
        int rightSteps = 0;
        while (isWaterPassable(room, prospective, right.west())
                && !isWaterPassable(room, prospective, right.below()) && rightSteps < 7) {
            right.move(Direction.WEST);
            rightSteps++;
        }

        // 离水源更近的一侧有空气时水只会往那边流；左右 5 格内都没空气时两边都流，最多 7 格
        if (isWaterPassable(room, prospective, left.below()) && leftSteps <= 5
                && (leftSteps < rightSteps || !isWaterPassable(room, prospective, right.below()))) {
            path.add(new BlockPair(root, left.immutable()));
            findWaterPathVertical(room, prospective, left.immutable(), path);
        } else if (isWaterPassable(room, prospective, right.below()) && rightSteps <= 5
                && (rightSteps < leftSteps || !isWaterPassable(room, prospective, left.below()))) {
            path.add(new BlockPair(root, right.immutable()));
            findWaterPathVertical(room, prospective, right.immutable(), path);
        } else {
            if (leftSteps > 0) {
                path.add(new BlockPair(root, left.immutable()));
                findWaterPathVertical(room, prospective, left.immutable(), path);
            }
            if (rightSteps > 0) {
                path.add(new BlockPair(root, right.immutable()));
                findWaterPathVertical(room, prospective, right.immutable(), path);
            }
        }
    }

    private boolean isWaterPassable(RoomMatcher.Match room, LeverType prospective, BlockPos pos) {
        if (pos.getX() < BOARD_MIN_X || pos.getX() > BOARD_MAX_X
                || pos.getY() < BOARD_MIN_Y || pos.getY() > BOARD_MAX_Y
                || pos.getZ() != BOARD_Z) {
            return false;
        }
        BlockState state = world.getBlockState(room.relativeToActual(pos));
        BlockState behind = world.getBlockState(room.relativeToActual(pos.offset(0, 0, 1)));
        boolean open = state.isAir() || state.is(Blocks.WATER);
        if (prospective == null) return open;
        return open && !behind.is(prospective.block) || state.is(prospective.block);
    }

    private void renderSolution(LevelRenderContext context, RoomMatcher.Match room) {
        if (solution == null) return;

        List<LeverTime> sorted = new ArrayList<>();
        for (Map.Entry<LeverType, DoubleList> entry : solution.entrySet()) {
            DoubleList times = entry.getValue();
            for (int i = 0; i < times.size(); i++) {
                sorted.add(new LeverTime(entry.getKey(), times.getDouble(i)));
            }
        }
        sorted.sort(Comparator.comparingDouble(LeverTime::sortKey)
                .thenComparingInt(time -> time.lever().ordinal()));
        LeverType nextLever = sorted.isEmpty() ? null : sorted.getFirst().lever();
        LeverType nextNextLever = sorted.size() < 2 ? null : sorted.get(1).lever();

        if (nextLever != null) {
            RenderUtils.WorldRender line = RenderUtils.createWorldRenderInstance(
                    context, CustomRenderPipeline.THROUGH_WALLS_LINE);
            BlockPos nextBlock = room.relativeToActual(nextLever.leverPos);
            // 用 tracer 从镜头连到当前该拉的拉杆，方块本身也一眼能看到
            RenderUtils.renderTrace(line, nextBlock, GREEN[0], GREEN[1], GREEN[2], 1f);
            if (nextNextLever != null) { 
                Vec3 nextPos = Vec3.atCenterOf(nextBlock);
                Vec3 nextNextPos = Vec3.atCenterOf(room.relativeToActual(nextNextLever.leverPos));
                RenderUtils.renderWorldLine(line, nextPos, nextNextPos, WHITE[0], WHITE[1], WHITE[2], 1f);
            }
            line.finishDraw();
        }

        // 每个待点击的拉杆上方显示倒计时
        for (Map.Entry<LeverType, DoubleList> entry : solution.entrySet()) {
            LeverType lever = entry.getKey();
            DoubleList times = entry.getValue();
            for (int i = 0; i < times.size(); i++) {
                double nextTime = times.getDouble(i);
                long remaining = waterStartMillis + (long) (nextTime * 1000) - currentTimeMillis;
                Component text;
                if (lever == LeverType.WATER && nextTime == 0.0 && nextLever != LeverType.WATER) {
                    // 时间表里的 0 秒表示在水之前点
                    text = Component.literal("WAIT").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                } else if (waterStartMillis == 0 && nextTime == 0.0
                        || waterStartMillis > 0 && remaining <= 0) {
                    text = Component.literal("CLICK").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
                } else {
                    double timeToShow = waterStartMillis == 0 ? nextTime : remaining / 1000.0;
                    text = Component.literal(String.format("%.2f", timeToShow)).withStyle(ChatFormatting.YELLOW);
                }
                Vec3 textPos = Vec3.atCenterOf(room.relativeToActual(lever.leverPos))
                        .add(0, 0.5 * (i + 1), 0);
                RenderUtils.renderWaypoint(context, textPos, text, 1.2f);
            }
        }
    }

    /** 换层/换局时重置“首次进入提示”。 */
    public static void resetAnnouncement() {
        announced = false;
    }

    @Override
    public void reset() {
        world = null;
        currentRoom = null;
        variant = 0;
        doors = null;
        initialDoors = null;
        solution = null;
        finished = false;
        attempted = false;
        waterStartMillis = 0;
        currentTimeMillis = 0;
        prospective = null;
        waterLines = List.of();
        previewOutline = List.of();
        previewFill = List.of();
    }

    private BlockPos relative(BlockPos relative) {
        return currentRoom.relativeToActual(relative);
    }

    private static float[] color(DyeColor dye) {
        int rgb = dye.getTextureDiffuseColor();
        return new float[]{
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f
        };
    }

    private record LeverTime(LeverType lever, double time) {
        /** 水拉杆在同一时刻的其他拉杆之后点。 */
        private double sortKey() {
            return time + (lever == LeverType.WATER ? 0.001 : 0.0);
        }
    }

    /** 水流路径的一段：从 start 流到 end（都是模板相对坐标）。 */
    private record BlockPair(BlockPos start, BlockPos end) {
    }

    /**
     * 6 种机关方块 + 水拉杆（岩浆块）。{@code leverPos} 是模板相对坐标；
     * {@code initialPositions} 是 4 种布局下初始状态里机关方块相对水入口的偏移，用来判断拉杆当前是否激活。
     */
    private enum LeverType {

        COAL(Blocks.COAL_BLOCK, new BlockPos(20, 61, 10), DyeColor.RED, new BlockPos[]{
                new BlockPos(0, -2, 0), new BlockPos(2, -1, 1),
                null, new BlockPos(5, -1, 0)
        }),
        GOLD(Blocks.GOLD_BLOCK, new BlockPos(20, 61, 15), DyeColor.YELLOW, new BlockPos[]{
                new BlockPos(1, -1, 0), new BlockPos(3, -2, 0),
                new BlockPos(-4, -1, 1), new BlockPos(1, 0, 0)
        }),
        QUARTZ(Blocks.QUARTZ_BLOCK, new BlockPos(20, 61, 20), DyeColor.LIGHT_GRAY, new BlockPos[]{
                new BlockPos(1, -4, 1), new BlockPos(-1, 0, 0),
                new BlockPos(1, 0, 0), new BlockPos(-1, 0, 1)
        }),
        DIAMOND(Blocks.DIAMOND_BLOCK, new BlockPos(10, 61, 20), DyeColor.CYAN, new BlockPos[]{
                new BlockPos(0, -5, 1), new BlockPos(-2, -1, 0),
                new BlockPos(-1, 0, 1), new BlockPos(-3, -4, 1)
        }),
        EMERALD(Blocks.EMERALD_BLOCK, new BlockPos(10, 61, 15), DyeColor.LIME, new BlockPos[]{
                new BlockPos(-1, -10, 1), new BlockPos(1, 0, 1),
                new BlockPos(-6, 0, 0), new BlockPos(1, -4, 0)
        }),
        TERRACOTTA(Blocks.TERRACOTTA, new BlockPos(10, 61, 10), DyeColor.ORANGE, new BlockPos[]{
                new BlockPos(-1, -1, 1), new BlockPos(0, -3, 1),
                null, new BlockPos(-4, -5, 1)
        }),
        WATER(Blocks.LAVA, new BlockPos(15, 60, 5), DyeColor.LIGHT_BLUE, null);

        private final Block block;
        private final BlockPos leverPos;
        private final DyeColor color;
        private final BlockPos[] initialPositions;

        LeverType(Block block, BlockPos leverPos, DyeColor color, BlockPos[] initialPositions) {
            this.block = block;
            this.leverPos = leverPos;
            this.color = color;
            this.initialPositions = initialPositions;
        }

        private static LeverType fromName(String name) {
            for (LeverType leverType : values()) {
                if (leverType.name().equalsIgnoreCase(name)) return leverType;
            }
            return null;
        }

        private static LeverType fromBlock(Block block) {
            for (LeverType leverType : values()) {
                if (leverType.block == block) return leverType;
            }
            return null;
        }

        private static LeverType fromPos(BlockPos leverPos) {
            for (LeverType leverType : values()) {
                if (leverPos.equals(leverType.leverPos)) return leverType;
            }
            return null;
        }
    }
}
