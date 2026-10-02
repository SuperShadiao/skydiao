package pers.XiaoShadiao.skydiao.eventbuslistener.macro;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.BlockPos;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.pathfinderv2.PathFinderV2;
import pers.XiaoShadiao.skydiao.utils.pathfinderv2.PathNode;
import pers.XiaoShadiao.skydiao.utils.pathfinderv2.PathNodes;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.PathRenderer;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
        boolean xray = ConfigManager.pfXRay.getValue();

        RenderUtils.WorldRender wrLine = RenderUtils.createWorldRenderInstance(context, xray ? CustomRenderPipeline.THROUGH_WALLS_LINE : CustomRenderPipeline.NO_THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wrFill = RenderUtils.createWorldRenderInstance(context, xray ? CustomRenderPipeline.THROUGH_WALLS_FILL : CustomRenderPipeline.NO_THROUGH_WALLS_FILL);

        if(pathfinder != null && task != null) {
            BlockPos startPos = pathfinder.getStartPos();
            BlockPos endPos = pathfinder.getEndPos();

            RenderUtils.renderTrace(wrLine, endPos, 0, 1, 0, 1);

            RenderUtils.renderESP(wrLine, startPos, 0, 1, 1, 1, false);
            RenderUtils.renderESP(wrLine, endPos, 0, 1, 0, 1, false);

            if(!task.isDone()) {
                PathRenderer.renderPath(context, pathfinder.getCurrentContext().getForwardPath().stream().map(PathNode::pos).toList(), 0, 0, 1f, xray, false);
                PathRenderer.renderPath(context, pathfinder.getLowestCostContext().getForwardPath().stream().map(PathNode::pos).toList(), 0.5f, 0, 1, xray, true);
                PathRenderer.renderPath(context, pathfinder.getLowestDistanceContext().getForwardPath().stream().map(PathNode::pos).toList(), 1, 0, 0.5f, xray, true);
            } else if(!task.isCompletedExceptionally()) {
                try {
                    PathRenderer.renderPath(context, task.get().getPath().stream().map(PathNode::pos).toList(), 1f, 1, 0, xray, false);
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }

    public void startExecution(BlockPos target) {
        stopExecution();
        pathfinder = new PathFinderV2();
        task = pathfinder.findPathSync(mc.player.blockPosition(), target);
    }

    public void stopExecution() {
        if(task != null) {
            task.cancel(true);
            task = null;
        }
        pathfinder = null;
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
