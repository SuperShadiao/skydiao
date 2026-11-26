package pers.XiaoShadiao.skydiao.utils.dungeon.puzzle;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import pers.XiaoShadiao.skydiao.utils.dungeon.RoomMatcher;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.List;

/**
 * Tic Tac Toe 谜题：读墙上的 9 个地图画框，用极小化极大（alphabeta）算下一步该点哪里。
 *
 * <p>搬运自 Skyblocker 的 {@code TicTacToe} + {@code TicTacToeUtils}。画框里地图正中间像素
 * 的颜色决定这一格是 X（114）还是 O（33）；棋盘相对坐标固定为 y 72/71/70（从上到下）、
 * z 17/16/15（从第一列到第三列），落子点在这一列前方一格（x=8）。
 */
public final class TicTacToeSolver extends DungeonPuzzleSolver {

    /** 地图中心像素：X 的颜色。 */
    private static final int X_COLOR = 114;
    /** 地图中心像素：O 的颜色。 */
    private static final int O_COLOR = 33;
    /** 地图中心像素在地图数组里的下标（64, 64）。 */
    private static final int MIDDLE_PIXEL = 64 + (64 << 7);

    private BlockPos nextMove;

    public TicTacToeSolver() {
        super("tic-tac-toe-1");
    }

    @Override
    public void tick(Minecraft mc, RoomMatcher.Match room) {
        nextMove = null;
        if (mc.level == null || mc.player == null) return;

        // 和 Skyblocker 一样在玩家周围 21 格内找地图画框
        AABB searchBox = new AABB(
                mc.player.getX() - 21, mc.player.getY() - 21, mc.player.getZ() - 21,
                mc.player.getX() + 21, mc.player.getY() + 21, mc.player.getZ() + 21);
        List<ItemFrame> frames = mc.level.getEntitiesOfClass(ItemFrame.class, searchBox,
                frame -> frame.getItem().getItem() instanceof MapItem);
        // 棋子数不是奇数说明还没轮到玩家（玩家是后手的 O），棋盘满了也不用再算
        if (frames.size() >= 9 || (frames.size() & 1) == 0) return;

        char[][] board = new char[3][3];
        for (ItemFrame frame : frames) {
            MapItemSavedData map = mapData(mc.level, frame.getItem());
            if (map == null) continue;
            BlockPos relative = room.actualToRelative(frame.blockPosition());
            int row = switch (relative.getY()) {
                case 72 -> 0;
                case 71 -> 1;
                case 70 -> 2;
                default -> -1;
            };
            int column = switch (relative.getZ()) {
                case 17 -> 0;
                case 16 -> 1;
                case 15 -> 2;
                default -> -1;
            };
            if (row < 0 || column < 0) continue;
            int color = map.colors[MIDDLE_PIXEL] & 0xFF;
            if (color == X_COLOR) board[row][column] = 'X';
            else if (color == O_COLOR) board[row][column] = 'O';
        }

        BoardIndex best = bestMove(board);
        if (best == null) return;
        nextMove = room.relativeToActual(new BlockPos(8, 72 - best.row(), 17 - best.column()));
    }

    @Override
    public void render(LevelRenderContext context, RoomMatcher.Match room) {
        if (nextMove == null) return;
        RenderUtils.WorldRender fill = RenderUtils.createWorldRenderInstance(
                context, CustomRenderPipeline.THROUGH_WALLS_FILL);
        RenderUtils.WorldRender line = RenderUtils.createWorldRenderInstance(
                context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        RenderUtils.renderESP(fill, nextMove, 0f, 1f, 0f, 0.5f, true, true);
        RenderUtils.renderESP(line, nextMove, 0f, 1f, 0f, 1f, false, true);
        fill.finishDraw();
        line.finishDraw();
    }

    @Override
    public void reset() {
        nextMove = null;
    }

    private static MapItemSavedData mapData(ClientLevel level, ItemStack stack) {
        MapId id = stack.get(DataComponents.MAP_ID);
        return id == null ? null : MapItem.getSavedData(id, level);
    }

    /** 每一列可下的位置里挑分数最高的一个。 */
    private static BoardIndex bestMove(char[][] board) {
        BoardIndex best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (board[row][column] != '\0') continue;
                // 先把玩家当成 O 模拟落子，再看对手最优应对下的得分
                board[row][column] = 'O';
                int score = alphabeta(board, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, false);
                board[row][column] = '\0';
                if (best == null || score > bestScore) {
                    best = new BoardIndex(row, column);
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private static boolean hasMovesAvailable(char[][] board) {
        for (char[] row : board) {
            for (char cell : row) {
                if (cell == '\0') return true;
            }
        }
        return false;
    }

    private static int getScore(char[][] board) {
        // 横向
        for (int row = 0; row < 3; row++) {
            if (board[row][0] == board[row][1] && board[row][0] == board[row][2]) {
                if (board[row][0] == 'X') return -10;
                if (board[row][0] == 'O') return 10;
            }
        }
        // 纵向
        for (int column = 0; column < 3; column++) {
            if (board[0][column] == board[1][column] && board[0][column] == board[2][column]) {
                if (board[0][column] == 'X') return -10;
                if (board[0][column] == 'O') return 10;
            }
        }
        // 两条对角线
        if (board[0][0] == board[1][1] && board[0][0] == board[2][2]) {
            if (board[0][0] == 'X') return -10;
            if (board[0][0] == 'O') return 10;
        }
        if (board[0][2] == board[1][1] && board[0][2] == board[2][0]) {
            if (board[0][2] == 'X') return -10;
            if (board[0][2] == 'O') return 10;
        }
        return 0;
    }

    private static int alphabeta(char[][] board, int alpha, int beta, int depth, boolean maximizePlayer) {
        int score = getScore(board);
        if (score == 10 || score == -10) return score;
        if (!hasMovesAvailable(board)) return 0;

        if (maximizePlayer) {
            int bestScore = Integer.MIN_VALUE;
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    if (board[row][column] != '\0') continue;
                    board[row][column] = 'O';
                    bestScore = Math.max(bestScore, alphabeta(board, alpha, beta, depth + 1, false));
                    board[row][column] = '\0';
                    alpha = Math.max(alpha, bestScore);
                    if (beta <= alpha) break;
                }
            }
            return bestScore - depth;
        }

        int bestScore = Integer.MAX_VALUE;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (board[row][column] != '\0') continue;
                board[row][column] = 'X';
                bestScore = Math.min(bestScore, alphabeta(board, alpha, beta, depth + 1, true));
                board[row][column] = '\0';
                beta = Math.min(beta, bestScore);
                if (beta <= alpha) break;
            }
        }
        return bestScore + depth;
    }

    private record BoardIndex(int row, int column) {
    }
}
