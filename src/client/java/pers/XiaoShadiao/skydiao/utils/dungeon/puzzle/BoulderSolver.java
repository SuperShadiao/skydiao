package pers.XiaoShadiao.skydiao.utils.dungeon.puzzle;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.utils.dungeon.RoomMatcher;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Boulder（推箱子）谜题：把箱子推到指定位置，A* 算出最短推法并沿路线画线，
 * 路上第一个石按钮用红框标出来（点它触发下一步）。
 *
 * <p>搬运自 Skyblocker 的 {@code Boulder} + {@code BoulderBoard} + {@code BoulderSolver}。
 * 棋盘是 7x7：可推的箱子是白桦/丛林木板，玩家从最南边一排出发，目标是到达最北边（x=0）。
 */
public final class BoulderSolver extends DungeonPuzzleSolver {

    private static final int BASE_Y = 65;
    private static final int BOARD_SIZE = 7;
    private static final float[] ORANGE = color(DyeColor.ORANGE);
    private static final float[] RED = color(DyeColor.RED);
    private static final int[][] DIRECTIONS = {{-1, 0}, {0, -1}, {0, 1}, {1, 0}};
    private static final int MAX_ITERATIONS = 10000;
    /** 棋盘没变化时也隔这么久重算一次：按钮被按掉、箱子小幅移动时标记不会过期。 */
    private static final int RESOLVE_INTERVAL = 10;

    private Vec3[] linePoints;
    private BlockPos button;
    /** 上一次解算时的棋盘，箱子没动就不用每 tick 跑 A*。 */
    private String lastBoard;
    private int ticks;

    public BoulderSolver() {
        super("boxes-room", "boulder");
    }

    @Override
    public void tick(Minecraft mc, RoomMatcher.Match room) {
        if (mc.level == null) return;
        ticks++;

        Board board = readBoard(mc.level, room);
        String key = board.key();
        boolean boardChanged = !key.equals(lastBoard);
        // 标记的按钮被按掉后会变成空气，这时必须立刻重算
        boolean buttonGone = button != null && !mc.level.getBlockState(button).is(Blocks.STONE_BUTTON);
        if (!boardChanged && !buttonGone && ticks % RESOLVE_INTERVAL != 0) return;
        lastBoard = key;

        List<int[]> solution = aStarSolve(initialStates(board));
        if (solution == null) {
            // 可能是箱子正在被活塞推动的中间状态，等下一次重算
            linePoints = null;
            button = null;
            return;
        }

        Vec3[] points = new Vec3[solution.size()];
        for (int i = 0; i < solution.size(); i++) {
            int[] coordinate = solution.get(i);
            BlockPos relative = board.position(coordinate[0], coordinate[1])
                    .relative(Direction.Axis.Y, -1);
            points[i] = Vec3.atCenterOf(room.relativeToActual(relative));
        }
        linePoints = points;
        button = findButton(mc.level, points);
        // 路线上已经没有按钮了，说明已经推完
        if (button == null) linePoints = null;
    }

    @Override
    public void render(LevelRenderContext context, RoomMatcher.Match room) {
        if (linePoints == null) return;
        RenderUtils.WorldRender line = RenderUtils.createWorldRenderInstance(
                context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        for (int i = 0; i < linePoints.length - 1; i++) {
            RenderUtils.renderWorldLine(line, linePoints[i], linePoints[i + 1],
                    ORANGE[0], ORANGE[1], ORANGE[2], 1f);
        }
        if (button != null) {
            RenderUtils.renderESP(line, button, RED[0], RED[1], RED[2], 1f, false, true);
            line.finishDraw();
            RenderUtils.WorldRender fill = RenderUtils.createWorldRenderInstance(
                    context, CustomRenderPipeline.THROUGH_WALLS_FILL);
            RenderUtils.renderESP(fill, button, RED[0], RED[1], RED[2], 0.5f, true, true);
            fill.finishDraw();
        } else {
            line.finishDraw();
        }
    }

    @Override
    public void reset() {
        linePoints = null;
        button = null;
        lastBoard = null;
        ticks = 0;
    }

    /** 把房间里的箱子读成 7x7 棋盘；第一排是固定的玩家出发线。 */
    private static Board readBoard(ClientLevel level, RoomMatcher.Match room) {
        Board board = new Board();
        int column = 0;
        for (int z = 25; z > 8; z--) {
            int row = 0;
            for (int x = 25; x > 5; x--) {
                if (Math.abs(25 - x) % 3 == 1 && Math.abs(25 - z) % 3 == 1) {
                    Block block = level.getBlockState(room.relativeToActual(new BlockPos(x, BASE_Y, z))).getBlock();
                    boolean box = block == Blocks.BIRCH_PLANKS || block == Blocks.JUNGLE_PLANKS;
                    board.set(column, row, new BlockPos(x, BASE_Y, z), box ? 'B' : '.');
                    row++;
                }
            }
            if (row == BOARD_SIZE) column++;
        }
        return board;
    }

    /** 玩家可能从出发线的任意一格开始，7 种初始状态一起丢给 A*。 */
    private static List<GameState> initialStates(Board board) {
        char[][] cells = board.cells();
        List<GameState> states = new ArrayList<>(BOARD_SIZE);
        for (int column = 0; column < BOARD_SIZE; column++) {
            states.add(new GameState(cells, BOARD_SIZE - 1, column, 0));
        }
        return states;
    }

    /** 路线经过的方块里第一个石按钮就是要点的那个。 */
    private static BlockPos findButton(ClientLevel level, Vec3[] points) {
        for (int i = 0; i < points.length - 1; i++) {
            Vec3 first = points[i];
            Vec3 second = points[i + 1];
            double x1 = first.x();
            double y1 = first.y() + 1;
            double z1 = first.z();
            double x2 = second.x();
            double y2 = second.y() + 1;
            double z2 = second.z();
            int steps = (int) Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
            if (steps <= 0) continue;
            double xStep = (x2 - x1) / steps;
            double yStep = (y2 - y1) / steps;
            double zStep = (z2 - z1) / steps;
            for (int step = 0; step <= steps; step++) {
                BlockPos pos = BlockPos.containing(x1 + step * xStep, y1 + step * yStep, z1 + step * zStep);
                if (level.getBlockState(pos).is(Blocks.STONE_BUTTON)) return pos;
            }
        }
        return null;
    }

    /** A*：以“路径长度 + 推箱次数 * 2 + 到目标行的距离”为代价找最短解。 */
    private static List<int[]> aStarSolve(List<GameState> initialStates) {
        Set<GameState> visited = new HashSet<>();
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingInt(Node::cost));
        for (GameState state : initialStates) queue.add(new Node(state, List.of()));

        int iterations = 0;
        while (!queue.isEmpty() && iterations < MAX_ITERATIONS) {
            Node node = queue.poll();
            if (!visited.add(node.state())) continue;

            List<int[]> path = new ArrayList<>(node.path());
            path.add(new int[]{node.state().playerX, node.state().playerY});
            if (node.state().isSolved()) return path;

            for (int[] direction : DIRECTIONS) {
                GameState next = new GameState(node.state().grid, node.state().playerX,
                        node.state().playerY, node.state().movedBoxes);
                if (next.movePlayer(direction[0], direction[1])) {
                    queue.add(new Node(next, new ArrayList<>(path)));
                }
            }
            iterations++;
        }
        return null;
    }

    private static float[] color(DyeColor dye) {
        int rgb = dye.getTextureDiffuseColor();
        return new float[]{
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f
        };
    }

    private record Node(GameState state, List<int[]> path) {
        private int cost() {
            return path.size() + state.movedBoxes() * 2 + state.heuristic();
        }
    }

    /** 7x7 棋盘：cells 是给 A* 用的字符盘，positions 记录每一格对应的模板相对坐标。 */
    private static final class Board {

        private final char[][] cells = new char[BOARD_SIZE][BOARD_SIZE];
        private final BlockPos[][] positions = new BlockPos[BOARD_SIZE][BOARD_SIZE];

        private Board() {
            for (char[] row : cells) Arrays.fill(row, '.');
            // 最后一排是玩家出发线，3D 坐标固定在最南边
            for (int column = 0; column < BOARD_SIZE; column++) {
                cells[BOARD_SIZE - 1][column] = 'P';
                positions[BOARD_SIZE - 1][column] = new BlockPos(24 - 3 * column, BASE_Y, 6);
            }
        }

        private void set(int column, int row, BlockPos position, char type) {
            cells[column][row] = type;
            positions[column][row] = position;
        }

        private char[][] cells() {
            return cells;
        }

        private BlockPos position(int column, int row) {
            return positions[column][row];
        }

        private String key() {
            StringBuilder builder = new StringBuilder(BOARD_SIZE * BOARD_SIZE);
            for (char[] row : cells) builder.append(row);
            return builder.toString();
        }
    }

    /** A* 的一个局面；playerX 是朝向终点方向的进度，playerY 是横向位置。 */
    private static final class GameState {

        private final char[][] grid;
        private int playerX;
        private int playerY;
        private int movedBoxes;

        private GameState(char[][] grid, int playerX, int playerY, int movedBoxes) {
            this.grid = copyGrid(grid);
            this.playerX = playerX;
            this.playerY = playerY;
            this.movedBoxes = movedBoxes;
        }

        private int movedBoxes() {
            return movedBoxes;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof GameState other && Arrays.deepEquals(grid, other.grid)
                    && playerX == other.playerX && playerY == other.playerY && movedBoxes == other.movedBoxes;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.deepHashCode(grid), playerX, playerY, movedBoxes);
        }

        private boolean movePlayer(int dx, int dy) {
            int newX = playerX + dx;
            int newY = playerY + dy;
            if (!isValidPosition(newX, newY)) return false;
            if (grid[newX][newY] == 'B') {
                int boxX = newX + dx;
                int boxY = newY + dy;
                if (!isValidPosition(boxX, boxY) || grid[boxX][boxY] != '.') return false;
                grid[newX][newY] = '.';
                grid[boxX][boxY] = 'B';
                playerX = newX;
                playerY = newY;
                movedBoxes++;
                return true;
            }
            playerX = newX;
            playerY = newY;
            return true;
        }

        private boolean isValidPosition(int x, int y) {
            return x >= 0 && y >= 0 && x < grid.length && y < grid[0].length;
        }

        private boolean isSolved() {
            return playerX == 0;
        }

        private int heuristic() {
            return Math.abs(playerX) + Math.abs(playerY - grid[0].length / 2);
        }

        private static char[][] copyGrid(char[][] original) {
            char[][] copy = new char[original.length][];
            for (int i = 0; i < original.length; i++) copy[i] = original[i].clone();
            return copy;
        }
    }
}
