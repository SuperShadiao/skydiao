package pers.XiaoShadiao.skydiao.eventbuslistener.macro;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.BlockPos;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.pathfinderv2.PathFinderV2;
import pers.XiaoShadiao.skydiao.utils.pathfinderv2.PathNodes;

import java.util.concurrent.CompletableFuture;

public class PathFinderV2Executor extends AbstractListener implements IMacro {

    private PathFinderV2 pathfinder = null;
    private CompletableFuture<PathNodes> task = null;

    @Override
    public String getListenerName() {
        return "PathFinderV2Executor";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {

    }

    public void startExecution(BlockPos target) {

    }

    @Override
    public boolean isMacroActive() {
        return false;
    }

    @Override
    public boolean onMacroCheck(PositionInfo beforeTP, PositionInfo afterTP) {
        return false;
    }

    @Override
    public boolean onMacroCheck(int beforeSlot, int afterSlot) {
        return false;
    }

    @Override
    public String getMacroName() {
        return "PathFinder V2";
    }

}
