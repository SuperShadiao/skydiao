package pers.XiaoShadiao.skydiao.utils.dungeon.puzzle;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.utils.dungeon.RoomMatcher;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ice Path 谜题：控制蠹虫在冰面上滑行，每次滑到撞墙为止，目标是走到最北边中间那一格。
 *
 * <p>搬运自 Skyblocker 的 {@code IcePath}。棋盘 17x17，左上角相对坐标 (23,67,24)；
 * 蠹虫每次移动都会一直滑到撞墙，所以 BFS 的每一步是"滑到底"而不是走一格。红线画出路径。
 */
public final class IcePathSolver extends DungeonPuzzleSolver {

    private static final int SIZE = 17;
    private static final BlockPos ORIGIN = new BlockPos(23, 67, 24);

    private final boolean[][] board = new boolean[SIZE][SIZE];
    private List<int[]> path = List.of();
    private int fishRow = -1;
    private int fishCol = -1;

    public IcePathSolver() {
        super("ice-silverfish-room", "silverfish");
    }

    @Override
    public void tick(Minecraft mc, RoomMatcher.Match room) {
        if (mc.level == null) return;

        boolean boardChanged = updateBoard(mc, room);
        AABB searchBox = AABB.ofSize(
                Vec3.atCenterOf(room.relativeToActual(new BlockPos(15, 66, 16))), 16, 16, 16);
        List<Silverfish> fish = mc.level.getEntitiesOfClass(Silverfish.class, searchBox);
        if (fish.isEmpty()) return;

        BlockPos relative = room.actualToRelative(fish.getFirst().blockPosition());
        int row = 24 - relative.getZ();
        int col = 23 - relative.getX();
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return;

        boolean fishChanged = row != fishRow || col != fishCol;
        if (!boardChanged && !fishChanged) return;
        fishRow = row;
        fishCol = col;
        path = solve(row, col);
    }

    /** 读取 17x17 棋盘，true 表示有方块（不能滑过去）。 */
    private boolean updateBoard(Minecraft mc, RoomMatcher.Match room) {
        boolean changed = false;
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                BlockPos actual = room.relativeToActual(
                        new BlockPos(ORIGIN.getX() - col, ORIGIN.getY(), ORIGIN.getZ() - row));
                boolean isBlock = !mc.level.getBlockState(actual).isAir();
                if (board[row][col] != isBlock) {
                    board[row][col] = isBlock;
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** 从蠹虫当前位置开始 BFS，每一步都滑到撞墙；终点是最北边中间 (row=0, col 7~9)。 */
    private List<int[]> solve(int startRow, int startCol) {
        Set<Integer> visited = new HashSet<>();
        Deque<List<int[]>> queue = new ArrayDeque<>();
        List<int[]> start = new ArrayList<>();
        start.add(new int[]{startRow, startCol});
        queue.add(start);
        visited.add(startRow * SIZE + startCol);

        while (!queue.isEmpty()) {
            List<int[]> current = queue.poll();
            int[] pos = current.getLast();
            if (pos[0] == 0 && pos[1] >= 7 && pos[1] <= 9) return current;

            for (int[] next : slides(pos[0], pos[1])) {
                if (!visited.add(next[0] * SIZE + next[1])) continue;
                List<int[]> nextPath = new ArrayList<>(current);
                nextPath.add(next);
                queue.add(nextPath);
            }
        }
        return List.of();
    }

    /** 四个方向各自滑到底后停下来的位置。 */
    private List<int[]> slides(int row, int col) {
        List<int[]> result = new ArrayList<>(4);
        int r = row;
        while (r < SIZE && !board[r][col]) r++;
        result.add(new int[]{r - 1, col});
        r = row;
        while (r >= 0 && !board[r][col]) r--;
        result.add(new int[]{r + 1, col});
        int c = col;
        while (c < SIZE && !board[row][c]) c++;
        result.add(new int[]{row, c - 1});
        c = col;
        while (c >= 0 && !board[row][c]) c--;
        result.add(new int[]{row, c + 1});
        return result;
    }

    @Override
    public void render(LevelRenderContext context, RoomMatcher.Match room) {
        if (path.size() < 2) return;
        RenderUtils.WorldRender line = RenderUtils.createWorldRenderInstance(
                context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        for (int i = 0; i < path.size() - 1; i++) {
            Vec3 start = center(room, path.get(i));
            Vec3 end = center(room, path.get(i + 1));
            RenderUtils.renderWorldLine(line, start, end, 1f, 0f, 0f, 1f);
        }
        line.finishDraw();
    }

    private static Vec3 center(RoomMatcher.Match room, int[] point) {
        return Vec3.atCenterOf(room.relativeToActual(
                new BlockPos(ORIGIN.getX() - point[1], ORIGIN.getY(), ORIGIN.getZ() - point[0])));
    }

    @Override
    public void reset() {
        for (boolean[] row : board) java.util.Arrays.fill(row, false);
        path = List.of();
        fishRow = -1;
        fishCol = -1;
    }
}
