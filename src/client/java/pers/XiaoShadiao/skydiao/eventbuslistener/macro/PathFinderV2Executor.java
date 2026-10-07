package pers.XiaoShadiao.skydiao.eventbuslistener.macro;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.HitResult;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.pathfinder.PathFinder;
import pers.XiaoShadiao.skydiao.utils.pathfinderv2.*;
import pers.XiaoShadiao.skydiao.utils.playerinput.AimHelper;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.PathRenderer;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

public class PathFinderV2Executor extends AbstractListener implements IMacro {

    private PathFinderV2 pathfinder = null;
    private CompletableFuture<PathNodes> task = null;

    private Queue<RePathInfo> rePathInfoQueue = new ArrayDeque<>();

    private final List<PathNode> path = new ArrayList<>();
    private final List<BlockPos> pathBlockPos = new ArrayList<>();
    private PathNode lastPassedNode = null;

    private boolean canUseAOTE;
    private boolean canUseEWarp;

    private boolean isPaused;

    @Override
    public String getListenerName() {
        return "PathFinderV2Executor";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onUnload);
    }

    private int tickCounter = 0;

    private void onStartTick(Minecraft mc) {
        tickCounter++;
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        stopExecution(true);
    }

    private void onLastRender(LevelRenderContext context) {
        boolean xray = ConfigManager.pfXRay.getValue();

        RenderUtils.WorldRender wrLine = RenderUtils.createWorldRenderInstance(context, xray ? CustomRenderPipeline.THROUGH_WALLS_LINE : CustomRenderPipeline.NO_THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wrFill = RenderUtils.createWorldRenderInstance(context, xray ? CustomRenderPipeline.THROUGH_WALLS_FILL : CustomRenderPipeline.NO_THROUGH_WALLS_FILL);

        CompletableFuture<PathNodes> task1 = task;
        if(pathfinder != null && task1 != null) {
            BlockPos startPos = pathfinder.getStartPos();
            BlockPos endPos = pathfinder.getEndPos();

            RenderUtils.renderTrace(wrLine, endPos, 0, 1, 0, 1);

            RenderUtils.renderESP(wrLine, startPos, 0, 1, 1, 1, false);
            RenderUtils.renderESP(wrLine, endPos, 0, 1, 0, 1, false);

            if(!task1.isDone()) {
                PathRenderer.renderPath(context, pathfinder.getCurrentContext().getForwardPath().stream().map(PathNode::pos).toList(), 0, 0, 1f, xray, false);
                PathRenderer.renderPath(context, pathfinder.getLowestDistanceContext().getForwardPath().stream().map(PathNode::pos).toList(), 1, 0, 0.5f, xray, true);
                PathRenderer.renderPath(context, pathfinder.getLowestCostContext().getForwardPath().stream().map(PathNode::pos).toList(), 0.5f, 0, 1, xray, true);
            } else if(!task1.isCompletedExceptionally()) {
                PathRenderer.renderPath(context, pathBlockPos, 1f, 1, 0, xray, false);
            }
        }

    }

    private boolean isThreadRunning = false;
    private long lastPassTime = System.currentTimeMillis();

    private long prepareParkourJump = System.currentTimeMillis();

    @Override
    public void run() {
        while(true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {}

            while(true) {
                try {
                    isThreadRunning = true;
                    executeThread();
                } catch (Throwable e) {
                    logger.catching(e);
                } finally {
                    isThreadRunning = false;
                }
                if(rePathInfoQueue.isEmpty()) break;

                RePathInfo rePathInfo = rePathInfoQueue.poll();
                pathfinder = rePathInfo.pathfinderInstance();
                task = rePathInfo.preRunTask();
            }
        }
    }

    private void executeThread() {

        if(pathfinder != null && task != null) {
            AimHelper aimHelper = new AimHelper(0.8);
            PathNodes pathNodes;
            try {
                pathNodes = task.get();
            } catch (InterruptedException | ExecutionException e) {
                logger.catching(e);
                return;
            }

            path.addAll(pathNodes.getPath());
            pathBlockPos.addAll(pathNodes.getPath().stream().map(PathNode::pos).toList());

            lastPassedNode = path.getFirst();
            if(ToolList.getInstance().isDevEnvironment()) System.out.println(path);

            if(path.size() > 2 && path.get(2).type() == PathNodeType.FLY) {
                path.removeFirst();
                pathBlockPos.removeFirst();
                lastPassedNode = path.removeFirst();
                pathBlockPos.removeFirst();
            }

            flagPathfinderAlive();
            while (pathfinder != null && task != null && !path.isEmpty()) {

                PathNode node = path.getFirst();
                PathNode aimNode = node;
                for (PathNode pathNode : path) {
                    if (checkPassed(pathfinder.getEndPos(), pathNode)) {
                        lastPassedNode = pathNode;
                        int index = path.indexOf(pathNode);
                        List<PathNode> pathNodes1 = path.subList(0, index + 1);
                        pathNodes1.clear();
                        pathBlockPos.subList(0, index + 1).clear();
                        flagPathfinderAlive();
                        prepareParkourJump = System.currentTimeMillis();
                        break;
                    }
                }

                PathNode preAimNode = aimNode;
                int expandCount = 0;
                while(expandCount < 5 && (preAimNode.type() == PathNodeType.FLY || preAimNode.type() == PathNodeType.MOVE || preAimNode.type() == PathNodeType.UP || preAimNode.type() == PathNodeType.DOWN)) {
                    PathNode temp = getNextNode(preAimNode);
                    if(!canBeSee(temp) || BlockHelper.areaHasCollision(aimNode.pos(), temp.pos().above(2))) {
                        break;
                    }
                    preAimNode = temp;
                    expandCount++;
                }

                if (!path.isEmpty() && path.size() < 30 && rePathInfoQueue.isEmpty()) {
                    PathNode lastNode = path.getLast();
                    if(!lastNode.pos().equals(pathfinder.getEndPos())) {
                        rePath(lastNode.pos(), pathfinder.getEndPos(), false);
                    }
                }
                if(isPathfinderStuck()) {
                    rePath(mc.player.blockPosition(), pathfinder.getEndPos(), true);
                }

                InputSimulator.releaseAllKey();
                boolean useSprint = true;
                boolean enableAiming = true;

                if(node.type() == PathNodeType.FLY) {
                    if (!mc.player.getAbilities().mayfly) {
                        rePath(mc.player.blockPosition(), pathfinder.getEndPos(), true);
                    }
                    if (mc.player.getAbilities().flying) {
                        // InputSimulator.setForward(node.pos().getCenter().horizontal().distanceTo(mc.player.position().horizontal()) > getFlyPassDist());
                        InputSimulator.tryNoViewChangeControlMoveTo(node.pos(), 1);
                        aimNode = preAimNode;

                        if(node.pos().getY() - (BlockHelper.hasCollision(node.pos().below()) ? 0 : 0.5) > mc.player.getY()) {
                            InputSimulator.setJump(true);
                        } else if(node.pos().getY() + 2 < mc.player.getY() + mc.player.getBbHeight()) {
                            InputSimulator.setShift(true);
                        }
                    } else {
                        InputSimulator.setJump(tickCounter % 5 == 0);
                    }
                } else if(node.type().isJumpMove()) {
                    if(node.type() == PathNodeType.JUMP1 || node.type() == PathNodeType.JUMP2) {
                        useSprint = false;
                    }

                    if(System.currentTimeMillis() - prepareParkourJump > 100) {
                        if(System.currentTimeMillis() - prepareParkourJump > 800) {
                            InputSimulator.tryNoViewChangeControlMoveTo(node.pos(), 0.5);
                            boolean farAwayParkourGoal = mc.player.position().horizontal().distanceTo(node.pos().getBottomCenter().horizontal()) > 1;
                            if (!BlockHelper.hasCollision(mc.player.blockPosition().below()) && farAwayParkourGoal) {
                                InputSimulator.setJump(true);
                            }
                            if (!farAwayParkourGoal) {
                                enableAiming = false;
                            }
                        } else {
                            if (InputSimulator.tryNoViewChangeControlMoveTo(getPrevNode(node).pos(), isSameDirection(getPrevNode(node).pos(), node.pos(), getNextBlockPos(node.pos())) ? 0.6 : 0.05).isEmpty()) {
                                prepareParkourJump = 0;
                            } else {
                                InputSimulator.setShift(true);
                            }
                        }
                    }
                } else if(node.type().isMove() || node.type() == PathNodeType.UP) {
                    // InputSimulator.setForward(true);
                    InputSimulator.tryNoViewChangeControlMoveTo(node.pos(), 0.3);
                    aimNode = preAimNode;

                    if(node.pos().getY() - 0.5 > mc.player.getY()) {
                        InputSimulator.setJump(true);
                    } else if(mc.player.getAbilities().flying) {
                        if(node.pos().getY() > mc.player.getY()) {
                            InputSimulator.setJump(true);
                        } else {
                            InputSimulator.setShift(true);
                        }
                    }
                } else if(node.type() == PathNodeType.DOWN) {
                    if(mc.player.getAbilities().flying) {
                        if(node.pos().getY() > mc.player.getY()) {
                            InputSimulator.setJump(true);
                        } else {
                            InputSimulator.setShift(true);
                        }
                    }
                    // InputSimulator.setForward(mc.player.position().horizontal().distanceTo(node.pos().getCenter().horizontal()) > 0.5);
                    InputSimulator.tryNoViewChangeControlMoveTo(node.pos(), 0.3);
                    aimNode = preAimNode;
                }

                InputSimulator.setSprint(useSprint);
                if(enableAiming) {
                    AimHelper.getYawPitchByBlockPos(aimNode.pos().above()).updateToAimHelper(aimHelper);
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }

            }
        }

        stopExecution(false);

    }

    private boolean isSameDirection(BlockPos prev, BlockPos cur, BlockPos next) {
        BlockPos dir1 = next.subtract(cur);
        BlockPos dir2 = cur.subtract(prev);

        int i = 0;
        if(dir1.getX() == 0 && dir2.getX() == 0) i++;
        if(dir1.getY() == 0 && dir2.getY() == 0) i++;
        if(dir1.getZ() == 0 && dir2.getZ() == 0) i++;

        return i >= 2;
    }

    private boolean isSameY(BlockPos pos1, BlockPos pos2, BlockPos pos3) {
        return pos1.getY() == pos2.getY() && pos2.getY() == pos3.getY();
    }

    private void flagPathfinderAlive() {
        lastPassTime = System.currentTimeMillis();
    }

    private boolean isPathfinderStuck() {
        return System.currentTimeMillis() - lastPassTime > 5000;
    }

    public boolean isRunning() {
        return isThreadRunning;
    }

    private static final class RePathInfo {
        private final BlockPos from;
        private final BlockPos to;
        private final boolean stopCurrent;

        private final PathFinderV2 pathfinderInstance;
        private final CompletableFuture<PathNodes> preRunTask;

        private RePathInfo(BlockPos from, BlockPos to, boolean stopCurrent) {
            this.from = from;
            this.to = to;
            this.stopCurrent = stopCurrent;
            this.pathfinderInstance = new PathFinderV2();
            this.preRunTask = pathfinderInstance.findPathSync(from, to);
        }

        public BlockPos from() {
            return from;
        }

        public BlockPos to() {
            return to;
        }

        public boolean stopCurrent() {
            return stopCurrent;
        }

        public CompletableFuture<PathNodes> preRunTask() {
            return preRunTask;
        }

        public PathFinderV2 pathfinderInstance() {
            return pathfinderInstance;
        }

    }

    private void rePath(BlockPos from, BlockPos to, boolean stopCurrent) {
        rePathInfoQueue.offer(new RePathInfo(from, to, stopCurrent));
        if(stopCurrent) {
            stopExecution(false);
        }
    }

    private double getFlyPassDist() {
        return 3.5 * mc.player.getAbilities().getFlyingSpeed() / 0.05;
    }

    private boolean canBeSee(PathNode current) {
        return Stream.of(mc.player.getEyePosition(), mc.player.position(), mc.player.position().add(0, mc.player.getBbHeight(), 0))
                .allMatch(pos -> Stream.of(
                                current.pos().getCenter(),
                                current.pos().above().getCenter(),
                                current.pos().getCenter().add(0, 0.49, 0),
                                current.pos().getBottomCenter().add(0, 0.01, 0)
                        )
                        .allMatch(pos2 -> Optional.ofNullable(ToolList.getInstance().predictPlayerAimBlock(pos, pos2)).map(HitResult::getType).orElse(HitResult.Type.MISS) != HitResult.Type.BLOCK));
    }

    private boolean checkPassed(BlockPos goal, PathNode current) {

        if(current.type().isJumpMove() && !mc.player.onGround()) return false;
        // if(isFollowerAlive() && (follower.isAimingToEntity || (pathblocks.size() == 1 && follower.tryAttack && Math.sqrt(pathblocks.getFirst().distToCenterSqr(follower.currentEntity.getX(), follower.currentEntity.getY(), follower.currentEntity.getZ())) < 2))) return false;

        double horizontalDis = mc.player.position().horizontal().distanceTo(current.pos().getCenter().horizontal());
        if(horizontalDis > 0.85) {
            boolean canSee = canBeSee(current) && canBeSee(getNextNode(current)) && !BlockHelper.areaHasCollision(mc.player.blockPosition(), getNextNode(current).pos().above(2));
            if (!canSee) {
                // logger.info("canSee false");
                return false;
            }
        }

        double distanceToGoal = goal.getCenter().horizontal().distanceTo(current.pos().getCenter().horizontal());

        boolean currentIsGoal = current.pos().equals(goal);

        boolean topHasBlock = BlockPos.betweenClosedStream(current.pos(), mc.player.blockPosition()).anyMatch(BlockHelper::hasCollision);
        boolean lastTopHasBlock = BlockPos.betweenClosedStream(getPrevNode(current).pos(), mc.player.blockPosition()).anyMatch(BlockHelper::hasCollision);

        PathNode nextNode = getNextNode(current);
        boolean nextTopHasBlock = BlockPos.betweenClosedStream(nextNode.pos(), mc.player.blockPosition()).anyMatch(BlockHelper::hasCollision);

        boolean passed = isPassed(current, lastTopHasBlock, currentIsGoal, nextNode, nextTopHasBlock, topHasBlock);


        if(pathfinder.isActuallyAllowBreak()) {
            passed &= (!BlockHelper.hasCollision(goal) && !BlockHelper.hasCollision(goal.above())) || !PathFinder.isBreakable(goal) || !PathFinder.isBreakable(goal.above());
        }

        if(pathfinder.isActuallyAllowPlace()) {
            BlockPos vec3_d1 = goal.offset(0, -1, 0);
            boolean flag1 = pathBlockPos.contains(vec3_d1);
            boolean flag2 = BlockHelper.hasCollision(vec3_d1);
            boolean flag3 = BlockHelper.hasCollision(goal.offset(0, -2, 0));
            boolean flag4 = pathBlockPos.contains(goal.offset(0, 1, 0));
            boolean flag5 = BlockHelper.hasCollision(getNextBlockPos(goal).offset(0, -1, 0));

            passed &= (flag1 || (flag2 || (!flag4 && flag3 && flag5)));
        }

        return passed;
    }

    private boolean isPassed(PathNode current, boolean lastTopHasBlock, boolean currentIsGoal,
                             PathNode nextNode, boolean nextTopHasBlock, boolean topHasBlock) {

        double allowedDistance;

        boolean shouldOnLand = currentIsGoal && BlockHelper.hasCollision(current.pos().below());
        if(shouldOnLand && !mc.player.onGround()) {
            return false;
        }

        if (current.type() == PathNodeType.FLY) {
            if(!mc.player.getAbilities().flying) return false;
            allowedDistance = getFlyPassDist() * (!currentIsGoal ? 3 : 1);
            if(nextNode.type() != PathNodeType.FLY) {
                allowedDistance = 1;
            }
        } else {
            // 非 FLY 类型
            double base;
            if (nextNode.type() == PathNodeType.DOWN ||
                    (current.type() == PathNodeType.DOWN && nextTopHasBlock)) {
                base = 0.3;
            } else {
                base = 0.7;
            }

            double speedFactor = 1 + mc.player.getAttributes()
                    .getBaseValue(Attributes.MOVEMENT_SPEED) * 10;
            allowedDistance = base * speedFactor;
        }

        if (lastTopHasBlock && (nextNode.type() == PathNodeType.FLY || nextNode.type() == PathNodeType.UP || nextNode.type() == PathNodeType.DOWN)) {
            allowedDistance = 0.3;
        }

        boolean hasBlockAround = BlockHelper.areaHasCollision(current.pos().offset(1, 1, 1), current.pos().offset(-1, 0, -1));
        if (hasBlockAround) {
            allowedDistance = Math.min(allowedDistance, 0.6);
        }

        double horizontalDistance = current.pos().getCenter().horizontal()
                .distanceTo(mc.player.position().horizontal());

        if (horizontalDistance > allowedDistance) {
            return false;
        }

        if (topHasBlock || currentIsGoal) {
            double yDiff = Math.abs(mc.player.position().y() - current.pos().getY());
            double maxY;

            if (current.type() == PathNodeType.FLY) {
                maxY = 2.5;
            } else {
                maxY = 1.5;
            }

            if (yDiff > maxY) {
                return false;
            }
        } else {
            double yDiff = mc.player.position().y() - current.pos().getY();
            double minY;

            if (current.type() == PathNodeType.FLY) {
                minY = -2.5;
            } else {
                minY = -1.5;
            }

            if (yDiff < minY) {
                return false;
            }
        }

        return true;
    }

    public BlockPos getNextBlockPos(BlockPos blockPos) {
        try {
            int index = pathBlockPos.indexOf(blockPos);
            if (index != -1 && index < pathBlockPos.size() - 1) {
                return pathBlockPos.get(index + 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return blockPos;
    }

    public PathNode getNextNode(PathNode node) {
        try {
            int index = path.indexOf(node);
            if (index != -1 && index < path.size() - 1) {
                return path.get(index + 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return node;
    }

    public PathNode getPrevNode(PathNode node) {
        try {
            int index = path.indexOf(node);
            if (index > 0) {
                return path.get(index - 1);
            } else if(index == 0) {
                return lastPassedNode;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return node;
    }

    public void startExecution(BlockPos target) {
        stopExecution(false);

        ToolList.addThreadedTask(() -> {
            while(isThreadRunning) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {

                }
            }
            pathfinder = new PathFinderV2();
            task = pathfinder.findPathSync(mc.player.blockPosition(), target);

            interrupt();
        }, null);
    }

    public void stopExecution() {
        stopExecution(true);
    }

    public void stopExecution(boolean stopAll) {
        if(task != null) {
            task.cancel(true);
            task = null;
        }

        rePathInfoQueue.clear();

        pathfinder = null;
        pathBlockPos.clear();
        path.clear();

        InputSimulator.releaseAllKey();

        isPaused = false;
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
