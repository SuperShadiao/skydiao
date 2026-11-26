package pers.XiaoShadiao.skydiao.utils.dungeon.puzzle;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.utils.dungeon.RoomMatcher;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Ice Fill 谜题：把棋盘上所有空位刚好走一遍（哈密顿路径），从底部中间出发、顶部中间结束。
 *
 * <p>搬运自 Skyblocker 的 {@code IceFill}。房间里有 3 块棋盘（3x3、5x5、7x7），
 * 分别位于相对坐标 (16,70,9)、(17,71,16)、(18,72,25)；每块棋盘独立读方块并求解，
 * 红色线画出完整走法。
 */
public final class IceFillSolver extends DungeonPuzzleSolver {

    private static final BlockPos[] ORIGINS = {
            new BlockPos(16, 70, 9),
            new BlockPos(17, 71, 16),
            new BlockPos(18, 72, 25)
    };
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final boolean[][][] boards = {new boolean[3][3], new boolean[5][5], new boolean[7][7]};
    private final AtomicReferenceArray<List<int[]>> paths =
            new AtomicReferenceArray<>(new List[]{List.of(), List.of(), List.of()});
    private final AtomicReferenceArray<CompletableFuture<Void>> solves =
            new AtomicReferenceArray<>(new CompletableFuture[3]);
    /** 换房间后旧的后台求解结果要丢掉。 */
    private volatile int generation;

    public IceFillSolver() {
        super("ice-path", "ice-fill");
    }

    @Override
    public void tick(Minecraft mc, RoomMatcher.Match room) {
        if (mc.level == null) return;
        for (int i = 0; i < ORIGINS.length; i++) {
            if (!updateBoard(mc.level, room, boards[i], ORIGINS[i])) continue;
            CompletableFuture<Void> running = solves.get(i);
            if (running != null && !running.isDone()) continue;
            boolean[][] snapshot = copy(boards[i]);
            int index = i;
            int currentGeneration = generation;
            solves.set(i, CompletableFuture.runAsync(() -> {
                List<int[]> path = solve(snapshot);
                if (currentGeneration == generation) paths.set(index, path);
            }));
        }
    }

    /**
     * 读取一块棋盘：true 表示这一格有方块（不能走）。棋盘下方是空气说明还没加载好，直接放弃这一轮。
     */
    private static boolean updateBoard(Level level, RoomMatcher.Match room, boolean[][] board, BlockPos origin) {
        boolean changed = false;
        for (int row = 0; row < board.length; row++) {
            for (int col = 0; col < board[row].length; col++) {
                BlockPos actual = room.relativeToActual(
                        new BlockPos(origin.getX() - col, origin.getY(), origin.getZ() - row));
                if (level.getBlockState(actual.below()).isAir()) return false;
                boolean isBlock = !level.getBlockState(actual).isAir();
                if (board[row][col] != isBlock) {
                    board[row][col] = isBlock;
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** 从底部中间出发，找到一条走遍所有空位、终点在顶部中间的路径。 */
    private static List<int[]> solve(boolean[][] board) {
        int rows = board.length;
        int cols = board[0].length;
        int empty = rows * cols;
        for (boolean[] row : board) {
            for (boolean cell : row) {
                if (cell) empty--;
            }
        }
        if (empty <= 0) return List.of();

        int[] start = {rows - 1, cols / 2};
        boolean[][] visited = new boolean[rows][cols];
        visited[start[0]][start[1]] = true;
        List<int[]> path = new ArrayList<>(empty);
        path.add(start);
        List<int[]> result = dfs(board, empty - 1, path, visited);
        return result != null ? result : List.of();
    }

    private static List<int[]> dfs(boolean[][] board, int remaining, List<int[]> path, boolean[][] visited) {
        int[] pos = path.getLast();
        if (remaining == 0) {
            return pos[0] == 0 && pos[1] == board[0].length / 2 ? new ArrayList<>(path) : null;
        }
        for (int[] direction : DIRECTIONS) {
            int x = pos[0] + direction[0];
            int y = pos[1] + direction[1];
            if (x < 0 || x >= board.length || y < 0 || y >= board[0].length) continue;
            if (board[x][y] || visited[x][y]) continue;
            path.add(new int[]{x, y});
            visited[x][y] = true;
            List<int[]> result = dfs(board, remaining - 1, path, visited);
            if (result != null) return result;
            path.removeLast();
            visited[x][y] = false;
        }
        return null;
    }

    @Override
    public void render(LevelRenderContext context, RoomMatcher.Match room) {
        RenderUtils.WorldRender line = RenderUtils.createWorldRenderInstance(
                context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        for (int i = 0; i < ORIGINS.length; i++) {
            List<int[]> path = paths.get(i);
            for (int p = 0; p < path.size() - 1; p++) {
                Vec3 start = center(room, ORIGINS[i], path.get(p));
                Vec3 end = center(room, ORIGINS[i], path.get(p + 1));
                RenderUtils.renderWorldLine(line, start, end, 1f, 0f, 0f, 1f);
            }
        }
        line.finishDraw();
    }

    private static Vec3 center(RoomMatcher.Match room, BlockPos origin, int[] point) {
        return Vec3.atCenterOf(room.relativeToActual(
                new BlockPos(origin.getX() - point[1], origin.getY(), origin.getZ() - point[0])));
    }

    @Override
    public void reset() {
        generation++;
        for (int i = 0; i < ORIGINS.length; i++) {
            paths.set(i, List.of());
            solves.set(i, null);
            for (boolean[] row : boards[i]) Arrays.fill(row, false);
        }
    }

    private static boolean[][] copy(boolean[][] board) {
        boolean[][] copy = new boolean[board.length][];
        for (int i = 0; i < board.length; i++) copy[i] = board[i].clone();
        return copy;
    }
}
