package pers.XiaoShadiao.skydiao.eventbuslistener.dungeon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.dungeon.RoomMatcher;
import pers.XiaoShadiao.skydiao.utils.dungeon.puzzle.BlazeSolver;
import pers.XiaoShadiao.skydiao.utils.dungeon.puzzle.BoulderSolver;
import pers.XiaoShadiao.skydiao.utils.dungeon.puzzle.CreeperBeamsSolver;
import pers.XiaoShadiao.skydiao.utils.dungeon.puzzle.DungeonPuzzleSolver;
import pers.XiaoShadiao.skydiao.utils.dungeon.puzzle.IceFillSolver;
import pers.XiaoShadiao.skydiao.utils.dungeon.puzzle.IcePathSolver;
import pers.XiaoShadiao.skydiao.utils.dungeon.puzzle.ThreeWeirdosSolver;
import pers.XiaoShadiao.skydiao.utils.dungeon.puzzle.TicTacToeSolver;
import pers.XiaoShadiao.skydiao.utils.dungeon.puzzle.TriviaSolver;
import pers.XiaoShadiao.skydiao.utils.dungeon.puzzle.WaterboardSolver;

import java.util.List;

/**
 * 地牢谜题 solver 的统一入口：当前房间被识别成某个谜题模板后，交给对应 solver 计算并渲染。
 *
 * <p>和 Skyblocker 一样按房间 id 绑定，离开房间/换层时重置。
 */
public class DungeonPuzzleSolverListener extends AbstractListener {

    private List<DungeonPuzzleSolver> solvers = createSolvers();

    private DungeonPuzzleSolver activeSolver;
    private RoomMatcher.Match activeRoom;

    private static List<DungeonPuzzleSolver> createSolvers() {
        return List.of(
                new TicTacToeSolver(),
                new ThreeWeirdosSolver(),
                new CreeperBeamsSolver(),
                new WaterboardSolver(),
                new BoulderSolver(),
                new BlazeSolver(),
                new IceFillSolver(),
                new IcePathSolver(),
                new TriviaSolver()
        );
    }

    @Override
    public String getListenerName() {
        return "DungeonPuzzleSolver";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onRender);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ClientReceiveMessageEvents.ALLOW_GAME.register(this::onGameMessage);
        CustomFabricEvents.ON_INGAME_CLICK.register(this::onIngameClick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((mc, level) -> {
            reset();
            // 新的一局地牢：水房提示和 Quiz 状态都要重来
            WaterboardSolver.resetAnnouncement();
            TriviaSolver.resetRunState();
        });
    }

    private boolean onIngameClick(CustomFabricEvents.ClickType clickType) {
        if (clickType != CustomFabricEvents.ClickType.RIGHT) return false;
        if (activeSolver == null || activeRoom == null) return false;
        if (mc.hitResult instanceof BlockHitResult hit) {
            activeSolver.onUseBlock(hit.getBlockPos(), activeRoom);
        }
        return false;
    }

    private boolean onGameMessage(Component message, boolean overlay) {
        boolean allowed = true;
        if (activeSolver != null && activeRoom != null) {
            allowed = activeSolver.onGameMessage(message, overlay, activeRoom);
        }
        // Quiz 这类允许在房间外作答的 solver，即使当前不在房间里也要收到消息
        for (DungeonPuzzleSolver solver : solvers) {
            if (solver != activeSolver && solver.receivesMessagesOutsideRoom()) {
                allowed &= solver.onGameMessage(message, overlay, null);
            }
        }
        return allowed;
    }

    private void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null || !ConfigManager.dungeonPuzzleHelper.getValue()) {
            reset();
            return;
        }
        DungeonMapScanListener.Snapshot snapshot = DungeonMapScanListener.getCurrent();
        RoomMatcher.Match room = snapshot == null ? null : snapshot.room();

        DungeonPuzzleSolver solver = null;
        if (room != null) {
            for (DungeonPuzzleSolver candidate : solvers) {
                if (candidate.matches(room)) {
                    solver = candidate;
                    break;
                }
            }
        }
        if (solver != activeSolver || room != activeRoom) {
            if (activeSolver != null) activeSolver.reset();
            activeSolver = solver;
            activeRoom = room;
        }
        if (solver != null) solver.tick(mc, room);
    }

    private void onRender(LevelRenderContext context) {
        if (activeSolver == null || activeRoom == null) return;
        if (!ConfigManager.dungeonPuzzleHelper.getValue()) return;
        activeSolver.render(context, activeRoom);
    }

    public void reset() {
        if (activeSolver != null) activeSolver.reset();
        activeSolver = null;
        activeRoom = null;

        if (ToolList.getInstance().isDevEnvironment()) {
            solvers = createSolvers();
        }
    }
}