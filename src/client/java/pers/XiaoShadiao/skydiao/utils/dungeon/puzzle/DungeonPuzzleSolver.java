package pers.XiaoShadiao.skydiao.utils.dungeon.puzzle;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.utils.dungeon.RoomMatcher;

import java.util.Set;

/**
 * 地牢谜题 solver 基类，对应 Skyblocker 的 {@code DungeonPuzzle}。
 *
 * <p>每个 solver 绑定若干 SkDR 房间 id，只有玩家当前所在房间被识别成这些模板之一时才会
 * {@link #tick} / {@link #render}。模板相对坐标统一用 {@link RoomMatcher.Match#relativeToActual} 换算。
 */
public abstract class DungeonPuzzleSolver {

    private final Set<String> roomIds;

    protected DungeonPuzzleSolver(String... roomIds) {
        this.roomIds = Set.of(roomIds);
    }

    /** 当前房间是不是这个 solver 负责的谜题房。 */
    public final boolean matches(RoomMatcher.Match room) {
        return room != null && roomIds.contains(room.room().id());
    }

    /** 每 tick 跑一次，负责算答案、更新状态。 */
    public abstract void tick(Minecraft mc, RoomMatcher.Match room);

    /** 收到游戏聊天/动作栏消息；返回 {@code false} 表示拦截。只有当前房间匹配时才调用。 */
    public boolean onGameMessage(Component message, boolean overlay, RoomMatcher.Match room) {
        return true;
    }

    /**
     * 不在对应房间里时是否也要接收聊天消息。
     * Quiz 可以在房间外参与、半途离开，所以它需要。
     */
    public boolean receivesMessagesOutsideRoom() {
        return false;
    }

    /** 右键某个方块（拉杆类谜题用）；只有当前房间匹配时才调用。 */
    public void onUseBlock(BlockPos pos, RoomMatcher.Match room) {
    }

    /** 每帧画一次答案。 */
    public void render(LevelRenderContext context, RoomMatcher.Match room) {
    }

    /** 换房间或离开地牢时清掉上一间的状态。 */
    public void reset() {
    }
}
