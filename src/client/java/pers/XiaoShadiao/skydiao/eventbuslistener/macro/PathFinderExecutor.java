package pers.XiaoShadiao.skydiao.eventbuslistener.macro;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.pathfinder.EntityFollower;
import pers.XiaoShadiao.skydiao.utils.pathfinder.Miner;
import pers.XiaoShadiao.skydiao.utils.pathfinder.PathFinder;
import pers.XiaoShadiao.skydiao.utils.pathfinder.PathHub;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.PathRenderer;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.*;
import java.util.concurrent.*;

public class PathFinderExecutor extends AbstractListener implements IMacro {

    public float rotationYaw;
    public float rotationPitch;
    public float turnedYaw;
    public float targetYaw;
    public static long BREAK_BLOCK_DELAY = 300;

    public int loop, depth;
    private int tickCounter;

    public boolean isRunning() {
        return isRunning && !sleeping;
    }
    public boolean isSleeping() {
        return sleeping;
    }

    public int action;
    public BlockPos playerLastPos;
    public boolean isDigging;
    public boolean sleeping = true;

    public volatile boolean isRunning = false;
    public long timeout;
    private PathFinder pathFinder;
    private final Queue<Map.Entry<PathFinder, Future<?>>> pathFindQueue = new ConcurrentLinkedQueue<>();

    private final ExecutorService pathFindExecutor = Executors.newSingleThreadExecutor();

    public BlockPos pos;
    public List<BlockPos> pathblocks = new ArrayList<>();

    public boolean reset;
    public boolean goalFlag;
    public float resetPitch;
    public float resetYaw;
    public int failedRemainBlocks;
    public int lastFailedRemainBlocks = 100000000;
    public int sprintingCDFlag;
    private EntityFollower follower;
    private Miner blockMiner;
    private boolean xray;

    private boolean isBreakingBlock;
    private int aimPlaceTime;
    private int aimBreakTime;
    private boolean paused = false;

    public boolean isPaused() {
        PathFinder.ICustomPathfinderConfig config = PathFinder.getRegisteredConfig();
        if(paused) return true;
        return config != null && config.shouldPause();
    }

    private int leftClickCounter = 0;
    private long lastLeftClickTime;

    private long lastBreakBlockTime;
    private long lastStartLeftClickTime;
    private int aimMoreFlag;
    public int backForwardFlag;
    private double randTurnSpeedYaw = 1;
    private double randTurnSpeedPitch = 1;

    private int lastBreakProcess;
    private BlockPos lastBreakPosition;
    private long lastBreakProcessTime;

    private Vec3 entityPos = new Vec3(0,0,0);

    public PathFinder getCurrentPathFinder() {
        return pathFinder;
    }

    @Override
    public String getListenerName() {
        return "PathFinderExecutor";
    }

    @Override
    public void registerListeners() {
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::onWorldUnload);
        ClientPlayConnectionEvents.DISCONNECT.register(this::onDisconnect);
        ClientTickEvents.START_CLIENT_TICK.register(this::onTickStart);
        WorldRenderEvents.END_MAIN.register(this::onRender);
    }

    public PathFinderExecutor() {
        super();
        setPriority(Thread.MAX_PRIORITY);
    }

    public boolean getLeftClickFlag() {
        if(leftClickCounter > 0) {
            leftClickCounter--;
            return true;
        }
        return false;
    }

    @Override
    public void run() {
        while (true) {
            try {
                try {
                    sleeping = true;
                    Thread.sleep(Long.MAX_VALUE);
                } catch (Exception e) {
                    sleeping = false;
                    executeThread();
                    isRunning = false;
                }
            } catch (Throwable e) {
                e.printStackTrace();
                isRunning = false;
            }
        }
    }

    private void onWorldUnload(Minecraft mc, ClientLevel level) {
        stopExecuteCauseWorldUnload();
    }

    private void onDisconnect(ClientPacketListener clientPacketListener, Minecraft minecraft) {
        stopExecuteCauseWorldUnload();
    }

    private void stopExecuteCauseWorldUnload() {
        if(isRunning) {
            stopExecution();
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c世界已变更, 寻路自动停止!"));
        }
    }

    @Override
    public boolean isMacroActive() {
        return isRunning();
    }

    @Override
    public boolean onMacroCheck(PositionInfo beforeTP, PositionInfo afterTP) {
        PathFinder.ICustomPathfinderConfig config = PathFinder.getRegisteredConfig();
        if(isRunning) {
            if((config == null && ConfigManager.pfStopWhenTP.getValue()) || (config != null && config.shouldStopWhenRecieveS08())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean onMacroCheck(int beforeSlot, int afterSlot) {
        PathFinder.ICustomPathfinderConfig config = PathFinder.getRegisteredConfig();
        if(isRunning) {
            if((config == null && ConfigManager.pfStopWhenTP.getValue()) || (config != null && config.shouldStopWhenRecieveS08())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getMacroName() {
        return "Path Finder";
    }

    public interface RePather {
        public void rePath(BlockPos goal, List<BlockPos> blacklistedBlocks);
    }

    private void executeThread() {
        PathFinder.setDestroyed(false);
        isRunning = true;
        paused = false;
        xray = ConfigManager.pfXRay.getValue();
        playerLastPos = BlockPos.ZERO;

        RePather reRather = (goal, blacklistedBlocks) -> {
            InputSimulator.unpressAllKey();
            if(pathFindQueue.isEmpty()) {
                pathFinder = rePath(goal, blacklistedBlocks);
            } else {
                Map.Entry<PathFinder, Future<?>> entry = pathFindQueue.poll();
                pathFinder = entry.getKey();
                try {
                    entry.getValue().get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
            if(pathFinder.getPath().size() > 5) lastFailedRemainBlocks = 100000000;
            logger.info("Executing new pathfinding: " + pathFinder.getPath().size() + " blocks");
            pathblocks = pathFinder.getPath();
        };

        List<BlockPos> blacklistedBlocks = new ArrayList<>();
        if(pathFinder != null) synchronized(this) {
            isRunning = true;
            try {
                pathFindExecutor.submit(() -> pathFinder.compute(loop, depth)).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            pathFindQueue.clear();
            pathblocks = pathFinder.getPath();

            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e请使用/skydiaopf stopgoal来停止傻卵机器人的操作"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e** 寻路系统来自FDPClient的AStar寻路 **"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c警告: 请不要在机器人操作时打开任何容器界面 (例如箱子, 背包等, 以免被封号), 也不要乱按鼠标左右键 (避免触发MultiAction被封号)!"));

            BlockPos goal = pathFinder.endPath;
            if(isMinerAlive()) pathblocks.add(pathFinder.endPath);
            long timeout = System.currentTimeMillis();
            lastFailedRemainBlocks = 100000000;
            lastLeftClickTime = System.currentTimeMillis();

            rotationYaw = InputSimulator.getPlayerYaw();
            rotationPitch = InputSimulator.getPlayerPitch();
            isDigging = false;

            task:while (mc.level != null && (isRunning || !PathFinder.hasBlock(BlockPos.containing(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ())) || (aimPlaceTime > -239 && aimPlaceTime < 239))) {
                leftClickCounter = 10;

                a:if(!pathblocks.isEmpty()) {

                    if(InputSimulator.isInventoryOpen()) {
                        InputSimulator.setBackward(false);
                        InputSimulator.setForward(false);
                        InputSimulator.setSprint(false);
                        InputSimulator.releaseLeftClick();
                        InputSimulator.releaseRightClick();
                        InputSimulator.setShift(false);
                        break a;
                    }
                    BlockPos nowTarget = pathblocks.getFirst();

                    int dx = 0, dy = 0;

                    Vec3 current = new Vec3(mc.player.getX() - 0.5, mc.player.getY(), mc.player.getZ() - 0.5);

                    if(checkPassed(nowTarget, current)) {
                        BlockPos removed;
                        BlockPos down, downdown;
                        do {
                            removed = pathblocks.removeFirst();
                            down = removed.offset(0, -1, 0);
                            downdown = down.offset(0, -1, 0);
                            if(pathFinder.isAllowPlace()) break;
                        } while (!pathblocks.isEmpty() && pathblocks.getFirst().equals(down) && mc.level.getBlockState(down).getBlock() == Blocks.AIR && mc.level.getBlockState(downdown).getBlock() == Blocks.AIR);
                        // logger.info("Block " + removed + " passed");
                        timeout = System.currentTimeMillis();
                        continue;
                    }
                    if(!pathFinder.foundPath && pathblocks.size() <= 25 && pathFindQueue.isEmpty()) {
                        BlockPos start = pathblocks.getLast();
                        PathFinder pf = new PathFinder(start, pathFinder.endPath);
                        pathFindQueue.add(new AbstractMap.SimpleEntry<>(pf, pathFindExecutor.submit(() -> {
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e寻路开始预计算新路径..."));
                            pf.compute(loop, depth, blacklistedBlocks);
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §a预计算完成..."));
                        })));
                    }

                    if(!paused) {
                        if((System.currentTimeMillis() - timeout > (isBreakingBlock ? 20000 : 5000) || (pathblocks.isEmpty() && pathFinder.endPath.distSqr(BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ())) > 19)) && System.currentTimeMillis() - lastStartLeftClickTime > 2000)  {

                            InputSimulator.setJump(false);
                            InputSimulator.setForward(false);

                            // KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);

                            if(pathblocks.size() >= 3) {
                                blacklistedBlocks.add(pathblocks.getFirst());
                            }

                            lastFailedRemainBlocks = failedRemainBlocks;
                            failedRemainBlocks = pathblocks.size();
                            logger.info("Timeout, " + failedRemainBlocks + " blocks remain.");
                            logger.info("Last: " + lastFailedRemainBlocks);

                            if(playerLastPos != null && playerLastPos.distSqr(BlockPos.containing(mc.player.getPosition(1))) < 5 * 5)  {
                                ToolList.printChatMessage(Component.literal("§a[小沙雕] §c目标点无法到达! Sry!"));
                                if(!isRunning) {
                                    logger.warn("奇怪, 明明停止了啊...");
                                    break task;
                                }
                                isRunning = false;
                            } else {
                                playerLastPos = BlockPos.containing(mc.player.getPosition(1));
                                reRather.rePath(goal, blacklistedBlocks);
                            }
                            timeout = System.currentTimeMillis();
                        }

                        if(pathFinder.isAllowPlace() && !PathFinder.hasBlockInInventory())  {
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c方块已耗尽!"));

                            reRather.rePath(goal, blacklistedBlocks);
                            timeout = System.currentTimeMillis();
                        }

                        double[] ypb = null;
                        BlockPos breakingBlockPos = null;
                        List<BlockPos> breakBlocks = new ArrayList<>();

                        if(!pathblocks.isEmpty()) {
                            if(isMinerAlive()) breakBlocks.add(pathblocks.getLast());
                            breakBlocks.addAll(pathblocks);
                        }

                        for(BlockPos pos : breakBlocks) {
                            breakingBlockPos = pos;

                            if(PathFinder.hasBlockBelow_Liquid(breakingBlockPos)) {
                                if(Math.abs(breakingBlockPos.getY() - mc.player.getY()) > 0.2 || breakingBlockPos.distToCenterSqr(mc.player.getX(), mc.player.getY(), mc.player.getZ()) > 1.1) continue;
                            }

                            if(!PathFinder.isBreakable(breakingBlockPos)) continue;
                            double[] d = getBreakBlockYawAndPitch(breakingBlockPos);
                            if(d != null) {
                                ypb = d;
                                break;
                            } else {
                                BlockPos up = breakingBlockPos.above();
                                if(!PathFinder.isBreakable(up)) continue;
                                if((d = getBreakBlockYawAndPitch(up)) != null) {
                                    ypb = d;
                                    break;
                                }
                            }
                        }

                        if(!pathFinder.isAllowBreak() && ypb != null) {
                            ypb = null;
                            BlockState blockState = mc.level.getBlockState(breakingBlockPos);
                            Block block = blockState.getBlock();
                            if(block instanceof ChestBlock || block instanceof EnderChestBlock || !ToolList.getInstance().isFullBlock(blockState)) {

                            } else {
                                ToolList.printChatMessage(Component.literal("§a[小沙雕] §cAllowbreak处于关闭状态, 但是路径上出现了障碍!"));
                                reRather.rePath(goal, blacklistedBlocks);
                                timeout = System.currentTimeMillis();
                            }
                        }

                        isBreakingBlock = ypb != null;
                        boolean placeSafeShift = false;
                        double[] ypp = null;
                        BlockPos blockPos = nowTarget.offset(0, -1, 0);
                        if(pathFinder.isAllowPlace() &&
                                !pathblocks.contains(nowTarget.offset(0, -1, 0)) &&
                                //                                !PathFinder.hasBlock(new BlockPos(nowTarget.offset(0, -1, 0))) &&
                                (!PathFinder.hasBlock(blockPos) || mc.level.getBlockState(blockPos).canBeReplaced()) &&
                                (!PathFinder.hasBlock(nowTarget.offset(0, -2, 0)) ||
                                        pathblocks.contains(nowTarget.offset(0, 1, 0)) ||
                                        !PathFinder.hasBlock(getNextBlockPos(nowTarget).offset(0, -1, 0))) &&
                                nowTarget.distSqr(BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ())) < 4
                        ) {
                            ypp = getPlaceBlockYawAndPitch(blockPos);
                            BlockPos pos1 = BlockPos.containing(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ());
                            if(pos1.distSqr(blockPos) <= 2 && !PathFinder.hasBlock(pos1)) placeSafeShift = true;
                        }

                        if(ypp != null && aimPlaceTime < 240) {
                            if(aimPlaceTime < 0) aimPlaceTime = 0;
                            aimPlaceTime++;
                        } else if(ypp == null && aimPlaceTime > -240) {
                            aimPlaceTime -= 2;
                        }
                        if(ypb != null && aimBreakTime < 150) {
                            aimBreakTime = 150;
                        } else if(ypp == null && aimBreakTime > 0) {
                            aimBreakTime--;
                        }
                        if(ypb == null) {
                            if (lastBreakProcess > -100) {
                                lastBreakProcess = -100;
                                lastBreakProcessTime = System.currentTimeMillis();
                            }
                        }

                        if((ypb != null && mc.player.onGround()) && (mc.level.getBlockState(BlockPos.containing(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ())).getBlock() != Blocks.AIR || mc.level.getBlockState(BlockPos.containing(mc.player.getX(), mc.player.getY() - 2, mc.player.getZ())).getBlock() != Blocks.AIR || mc.level.getBlockState(BlockPos.containing(mc.player.getX(), mc.player.getY() - 3, mc.player.getZ())).getBlock() != Blocks.AIR)) {
                            // KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
                            if(isRunning) aimPlaceTime = 0; else aimPlaceTime = -240;
                            action = 1;
                            InputSimulator.setJump(false);
                            InputSimulator.releaseRightClick();
                            InputSimulator.setBackward(false);
                            try {
                                Optional<BlockPos> aimingBlockPos = Optional.ofNullable(mc.hitResult).filter(h -> h instanceof BlockHitResult).map(BlockHitResult.class::cast).filter(h -> h.getType() == HitResult.Type.BLOCK).map(BlockHitResult::getBlockPos);
                                BlockPos currentDamagePosition = aimingBlockPos.orElse(null);

                                int currentDamage = mc.gameMode.getDestroyStage();

                                if (lastBreakProcess != currentDamage || currentDamagePosition == null || !currentDamagePosition.equals(lastBreakPosition)) {
                                    long delay = System.currentTimeMillis() - lastBreakProcessTime;
                                    if(delay > 500 && delay < 5000) {
                                        timeout = System.currentTimeMillis();
                                    }
                                    lastBreakProcess = currentDamage;
                                    lastBreakPosition = currentDamagePosition;
                                    lastBreakProcessTime = System.currentTimeMillis();
                                }
                            } catch (ConcurrentModificationException ignored) {}
                            boolean flag = System.currentTimeMillis() - lastBreakProcessTime > 5000;// (mc.player.isPotionActive(Potion.digSlowdown) || Optional.ofNullable(mc.objectMouseOver).map(MovingObjectPosition::getBlockPos).map(bp -> !PathFinder.isBreakable(bp)).orElse(false)) && System.currentTimeMillis() - lastStartLeftClickTime > 4000;
                            InputSimulator.setShift(flag && tickCounter < 12 && mc.player.onGround());
                            InputSimulator.setForward(flag && tickCounter < 8 && tickCounter > 3 && mc.player.onGround());

                            if(!(mc.hitResult instanceof BlockHitResult blockHitResult) || !blockHitResult.getBlockPos().equals(breakingBlockPos)) {
                                rorateYaw(getYawByPercent(rotationYaw, (float) ypb[0], 0.04f, true));
                                roratePitch(getPitchByPercent(rotationPitch, (float) ypb[1], 0.04f));
                            }

                            boolean leftClickFlag = mc.hitResult instanceof BlockHitResult;

                            if(leftClickFlag) {
                                if(!isDigging) {
                                    if(System.currentTimeMillis() - lastLeftClickTime >= /*250*/BREAK_BLOCK_DELAY) {
                                        InputSimulator.singleLeftClick();
                                    }
                                }
                                InputSimulator.pressLeftClick();
                            } else InputSimulator.releaseLeftClick();

                            float speed = 0;
                            int index = 0;
                            if (mc.hitResult instanceof BlockHitResult blockHitResult)  {
                                for(int i = 0; i < 9; i++) {
                                    if(mc.player.getInventory().getItem(i) != null) {
                                        BlockPos blockPos1 = blockHitResult.getBlockPos();
                                        float speed1 = mc.player.getInventory().getItem(i).getDestroySpeed(mc.level.getBlockState(blockPos1));

                                        if(speed1 > speed) {
                                            speed = speed1;
                                            index = i;
                                        }
                                    }
                                }

                                InputSimulator.switchItem(index);
                                backForwardFlag = 0;
                            }
                            // timeout = System.currentTimeMillis();

                        } else if((ypp != null || aimPlaceTime > 0))  {

                            action = 2;
                            InputSimulator.setLeft(false);
                            InputSimulator.setJump(false);

                            if(ypp != null) {
                                InputSimulator.setShift(true);

                                rorateYaw(getYawByPercent(rotationYaw, (float) ypp[0], 0.07f));
                                roratePitch(getPitchByPercent(rotationPitch, (float) ypp[1], 0.07f));

                                if(aimPlaceTime >= 180) {
                                    InputSimulator.pressRightClick();
                                } else {
                                    InputSimulator.releaseRightClick();
                                }
                                for(int i = 0; i < 9; i++) {
                                    if(mc.player.getInventory().getItem(i) != null) {
                                        if(mc.player.getInventory().getItem(i).getItem() instanceof BlockItem) {
                                            InputSimulator.switchItem(i);
                                            break;
                                        }
                                    }
                                }

                                if(mc.player.onGround()) {
                                    if(InputSimulator.getPlayerPitch() >= 80 && Optional.ofNullable(mc.hitResult).filter(a -> a instanceof BlockHitResult).map(BlockHitResult.class::cast).filter(h -> h.getType() == HitResult.Type.BLOCK).map(BlockHitResult::getDirection).orElse(Direction.UP) != Direction.UP) {
                                        backForwardFlag = 500;
                                    } else {
                                        backForwardFlag = 0;
                                    }
                                }

                                double x = mc.player.getX();
                                double z = mc.player.getZ();
                                double xx = x % 1;
                                double zz = z % 1;
                                boolean isAtBlockMid = xx > 0.3 && xx < 0.7 && zz > 0.3 && zz < 0.7;
                                boolean ff = !(mc.level.getBlockState(nowTarget.below()).getBlock() instanceof LiquidBlock) && (Math.abs(nowTarget.getX() - current.x()) <= 0.2 && Math.abs(nowTarget.getZ() - current.z()) <= 0.2 || (nowTarget.getY() - mc.player.getY() > 0.1));
                                boolean jumpFlag = System.currentTimeMillis() - lastBreakProcessTime > 100 && mc.player.getY() - 0.6 < nowTarget.getY() && ff && aimPlaceTime >= 230;
                                InputSimulator.setJump(jumpFlag);
                                InputSimulator.setBackward(tickCounter < 10 && !ff);
                                InputSimulator.setForward(/*tickCounter < 3 && mc.player.onGround && ff*/jumpFlag && !isAtBlockMid);
                            } else {
                                InputSimulator.releaseRightClick();
                            }

                        } else {
                            action = 0;
                            boolean jumpFlag = System.currentTimeMillis() - lastBreakProcessTime > 100 && mc.player.getY() < nowTarget.getY();
                            boolean forwardFlag = isFollowerAlive() || new Vec3(nowTarget).horizontal().distanceTo(current.horizontal()) > 0.099;

                            BlockState blockState = mc.level.getBlockState(nowTarget);
                            Block block = blockState.getBlock();
                            boolean isBlockLadderOrVine = block instanceof LadderBlock || block instanceof VineBlock;

                            if(!isBlockLadderOrVine) {
                                blockState = mc.level.getBlockState(nowTarget.below());
                                block = blockState.getBlock();
                                isBlockLadderOrVine = block instanceof LadderBlock || block instanceof VineBlock;
                            }
                            if(!isBlockLadderOrVine) {
                                blockState = mc.level.getBlockState(nowTarget.below(2));
                                block = blockState.getBlock();
                                isBlockLadderOrVine = block instanceof LadderBlock || block instanceof VineBlock;
                            }

                            boolean isPlayerOnLadderOrVine = mc.player.onClimbable();

                            isPlayerOnLadderOrVine &= mc.player.getY() < nowTarget.getY();

                            int face = 0;

                            if(block instanceof LadderBlock) {
                                face = blockState.getValue(LadderBlock.FACING).getOpposite().ordinal();
                            } else if(block instanceof VineBlock) {
                                tttt:{
                                    for (Map.Entry<Direction, BooleanProperty> facing : VineBlock.PROPERTY_BY_DIRECTION.entrySet()) {
                                        if(blockState.getValue(facing.getValue())) {
                                            face = facing.getKey().equals(Direction.EAST) ? 5 : facing.getKey().equals(Direction.WEST) ? 4 : facing.getKey().equals(Direction.SOUTH) ? 3 : facing.getKey().equals(Direction.NORTH) ? 2 : -1;
                                            break tttt;
                                        }
                                        face = -1;
                                    }
                                }
                            } else {
                                face = -1;
                            }

                            Vec3 faceTo = new Vec3(nowTarget);

                            if (isBlockLadderOrVine && isPlayerOnLadderOrVine) {
                                switch(face) {
                                    case 2: // north
                                        faceTo = faceTo.add(0, 0, -0.3);
                                        break;
                                    case 3: // south
                                        faceTo = faceTo.add(0, 0, 0.3);
                                        break;
                                    case 4: // west
                                        faceTo = faceTo.add(-0.3, 0, 0);
                                        break;
                                    case 5: // east
                                        faceTo = faceTo.add(0.3, 0, 0);
                                        break;
                                }
                            }

                            boolean followerVoidProtection = false;
                            if (forwardFlag) {

                                float yaw = (float) Math.max(-179.9, Math.min(179.9, (Math.atan2(current.x() - faceTo.x(), -(current.z() - faceTo.z())) * 180 / Math.PI)));
                                if (backForwardFlag > 0) {
                                    yaw = (yaw + 180) % 360;
                                    if (yaw > 180) {
                                        yaw -= 360;
                                    }
                                }
                                yaw = getYawByPercent(rotationYaw, yaw, 0.1f);

                                float pitch = -9999;

                                if (isFollowerAlive()) {
                                    Entity entity = follower.currentEntity;
                                    if (entity != null) {
                                        if (mc.player.getY() > entity.getY() - 4) {

                                            boolean flag = true;
                                            for (int i = 0; i < 15; i++) {
                                                BlockPos bp = BlockPos.containing(entity.getX(), entity.getY() - i, entity.getZ());
                                                BlockPos[] bps = new BlockPos[] {
                                                        bp.offset(1, 0, 0), bp.offset(-1, 0, 0),
                                                        bp.offset(0, 0, 1), bp.offset(0, 0, -1),
                                                        bp.offset(1, 0, 1), bp.offset(-1, 0, -1),
                                                        bp.offset(1, 0, -1), bp.offset(-1, 0, 1),
                                                        bp
                                                };
                                                for(BlockPos b : bps) {
                                                    flag &= !ToolList.getInstance().canThroughBlock(mc.level.getBlockState(b));
                                                }
                                            }

                                            if(flag) followerVoidProtection = true;
                                        }
                                    }
                                }
                                if (isFollowerAlive() && follower.tryAttack) {
                                    Entity entity = follower.currentEntity;
                                    if (entity != null) {
//                                        Vec3 pos = new Vec3(entity.getX(), entity.getY(), entity.getZ());
//                                        if(entity != entityMotion.getKey()) {
//                                            lastEntityPos = pos;
//                                            entityMotion = new AbstractMap.SimpleEntry<>(entity, new Vec3(0, 0, 0));
//                                        }
//                                        if(pos.equals(lastEntityPos)) {
//                                            entityMotion.setValue(new Vec3(entityMotion.getValue().getX() * 0.7, entityMotion.getValue().getY() * 0.7, entityMotion.getValue().getZ() * 0.7));
//                                        } else {
//                                            double predictRange = 80;
//                                            entityMotion.setValue(new Vec3((entity.getX() - lastEntityPos.getX()) * predictRange, (entity.getY() - lastEntityPos.getY()) * (predictRange / 3), (entity.getZ() - lastEntityPos.getZ()) * predictRange));
//                                        }
//                                        // System.out.println(entityMotion.getValue().toString());
//                                        lastEntityPos = pos;
                                        float distanceToEntity = entity.distanceTo(mc.player);
                                        if (distanceToEntity < 9 && ToolList.getInstance().isEntityOnWorld(entity)) {
                                            boolean flag = Optional.ofNullable(mc.hitResult).filter(h -> h instanceof EntityHitResult).map(EntityHitResult.class::cast).map(EntityHitResult::getEntity).orElse(null) != entity;
                                            if ((distanceToEntity < 2.5 && !flag) || distanceToEntity < 1.2 || (entity instanceof ArmorStand && current.horizontal().distanceTo(entity.getPosition(1).horizontal()) < 1.5) && follower.isAimingToEntity) {
                                                forwardFlag = false;
                                            }
                                            if (aimMoreFlag > 0) aimMoreFlag--;
                                            if (flag) aimMoreFlag = 100;

//                                            Vec3 motion = entityMotion.getValue();
//                                            motion = motion != null ? motion : new Vec3(0, 0, 0);

                                            if (aimMoreFlag > 0) {
                                                float newYaw = (float) Math.max(-179.9, Math.min(179.9, (Math.atan2(-((entity.getX()/* + motion.getX()*/) - mc.player.getX()), ((entity.getZ()/* + motion.getZ()*/) - mc.player.getZ())) * 180 / Math.PI)));

                                                final float startYaw = rotationYaw;
                                                final float endYaw = newYaw;

                                                double selfY = mc.player.getY() + mc.player.getEyeHeight();
                                                float targetEyeHeight = entity.getEyeHeight();
                                                double randomDeltaEyeHeight = targetEyeHeight / (0.8 + 0.4 * ToolList.getInstance().random.nextFloat());
                                                double actuallyTargetY = entity.getY() + randomDeltaEyeHeight/* + motion.getY()*/;

                                                if (entity.getY() + targetEyeHeight / 4 > selfY)
                                                    actuallyTargetY = entity.getY() + targetEyeHeight / 4;
                                                else if (actuallyTargetY > selfY) actuallyTargetY = selfY;

                                                double armorstandOffset = entity instanceof ArmorStand ? 0.6 : 0;
                                                entityPos = new Vec3(entity.getX(), actuallyTargetY/* - motion.getY()*/ - randomDeltaEyeHeight + targetEyeHeight - armorstandOffset, entity.getZ());
                                                // entityPredictPos = new Vec3(entity.getX() + motion.getX(), actuallyTargetY - randomDeltaEyeHeight + targetEyeHeight, entity.getZ() + motion.getZ());

                                                float newPitch = (float) Math.max(-89.9, Math.min(89.9, (-Math.atan2(-(selfY - actuallyTargetY + armorstandOffset), entity.getPosition(1).horizontal().distanceTo(mc.player.getPosition(1).horizontal())) * 180 / Math.PI)));
                                                final float startPitch = rotationPitch;
                                                final float endPitch = newPitch;

                                                HitResult predict = ToolList.getInstance().predictPlayerAimBlock(mc.player, endYaw, endPitch, 10);

                                                if (!(predict.getType() == HitResult.Type.BLOCK && predict instanceof BlockHitResult bhr && bhr.getBlockPos().distToCenterSqr(mc.player.getX(), mc.player.getY(), mc.player.getZ()) < mc.player.distanceToSqr(entity))) {
                                                    yaw = getYawByPercent(startYaw, endYaw, (0.024f + (ToolList.getInstance().random.nextFloat() / 100f - 0.005f)) * 2, true);
                                                    pitch = getPitchByPercent(startPitch, endPitch, (0.01f + (ToolList.getInstance().random.nextFloat() / 400f - 0.0025f / 2f)), true);
                                                    // yaw = newYaw;
                                                    if (!jumpFlag && entity.onGround() && entity.getY() - 0.9 > mc.player.getY()) {
                                                        jumpFlag = tickCounter < 5;
                                                    }
                                                    follower.isAimingToEntity = true;
                                                    action = 3;
                                                } else {
                                                    follower.isAimingToEntity = false;
                                                }
                                            } else {
                                                yaw = rotationYaw;
                                                pitch = rotationPitch;
                                                follower.isAimingToEntity = false;
                                            }

                                        } else follower.isAimingToEntity = false;
                                    } else follower.isAimingToEntity = false;
                                }

                                // if(!ffllaagg) if(yaw > 179 || yaw < -179) yaw = 180f;
                                // mc.player.rotationYaw = yaw;
                                if (aimBreakTime <= 0) {
                                    rorateYaw(yaw);
                                    if (backForwardFlag <= 0) {
                                        if (pitch != -9999) {
                                            roratePitch(pitch);
                                        } else if (InputSimulator.getPlayerPitch() > 15) {
                                            roratePitch(InputSimulator.getPlayerPitch() - 0.5f - ToolList.getInstance().random.nextFloat() / 100);
                                        } else if (InputSimulator.getPlayerPitch() < 10) {
                                            roratePitch(InputSimulator.getPlayerPitch() + 0.5f + ToolList.getInstance().random.nextFloat() / 100);
                                        }
                                    }
                                }

                            }
                            forwardFlag &= aimPlaceTime < -230;

                            if (isBlockLadderOrVine && isPlayerOnLadderOrVine) {
                                forwardFlag = true;
                            }

                            if (mc.player.isSprinting()) sprintingCDFlag = 50;
                            if (sprintingCDFlag > 0) sprintingCDFlag--;
                            InputSimulator.setJump(!mc.player.isShiftKeyDown() && (mc.player.isInLava() || mc.player.isInWater() || sprintingCDFlag < 1) && jumpFlag);

                            if (backForwardFlag <= 0) {
                                InputSimulator.setForward(forwardFlag);
                                InputSimulator.setBackward(false);
                            } else {
                                InputSimulator.setBackward(forwardFlag);
                                InputSimulator.setForward(false);
                                backForwardFlag--;
                            }

                            InputSimulator.setSprint(true);

                            InputSimulator.releaseLeftClick();
                            InputSimulator.releaseRightClick();
                            InputSimulator.setShift(mc.player.getAbilities().flying || placeSafeShift || followerVoidProtection);
                        }

                        InputSimulator.setPlayerYaw(rotationYaw/* + (follower != null && follower.isAlive() && follower.isAimingToEntity ? (ToolList.getInstance().random.nextInt(9) - 4) : 0)*/);
                        InputSimulator.setPlayerPitch(rotationPitch/* + (follower != null && follower.isAlive() && follower.isAimingToEntity ? (ToolList.getInstance().random.nextInt(3) - 1) : 0)*/);
                        //                        mc.player.rotationYaw = rotationYaw;
                        //                        mc.player.rotationPitch = rotationPitch;
                    }

                    this.timeout = timeout;
                } else if(pathFinder.endPath.distToCenterSqr(mc.player.getX(), mc.player.getY(), mc.player.getZ()) < 2.5 * 2.5) {
                    break task;
                } else if(playerLastPos != null && playerLastPos.distToCenterSqr(mc.player.getPosition(1)) < 5 * 5) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §c目标点无法到达! Sry!"));
                    if(!isRunning) {
                        logger.warn("奇怪, 明明停止了啊...");
                        break task;
                    }
                    isRunning = false;
                } else {
                    playerLastPos = BlockPos.containing(mc.player.getPosition(1));
                    reRather.rePath(goal, blacklistedBlocks);
                    timeout = System.currentTimeMillis();
                }

                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    // throw new RuntimeException(e);
                }
            }
            if(reset) {
//                mc.player.rotationYaw = resetYaw;
//                mc.player.rotationPitch = resetPitch;
                InputSimulator.setPlayerYaw(resetYaw);
                InputSimulator.setPlayerPitch(resetPitch);
            }
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §a任务执行完成!"));
            pathFinder = null;
            isRunning = false;

            // KeyMapping.releaseAll();
            InputSimulator.releaseAllKey();
        }
    }

    private PathFinder rePath(BlockPos goal, List<BlockPos> blacklistedBlocks) {
        InputSimulator.setBackward(false);
        InputSimulator.setForward(false);
        InputSimulator.setSprint(false);
        InputSimulator.releaseRightClick();
        InputSimulator.releaseLeftClick();
        InputSimulator.setShift(false);

        PathFinder pathFinder1 = new PathFinder(BlockPos.containing(mc.player.getPosition(1)), goal);
        pathFinder = pathFinder1;
        try {
            pathFindExecutor.submit(() -> pathFinder1.compute(loop, depth, blacklistedBlocks)).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return pathFinder1;
    }

    private boolean checkPassed(BlockPos nowTarget, Vec3 current) {

        if(isFollowerAlive() && (follower.isAimingToEntity || (pathblocks.size() == 1 && follower.tryAttack && Math.sqrt(pathblocks.getFirst().distToCenterSqr(follower.currentEntity.getX(), follower.currentEntity.getY(), follower.currentEntity.getZ())) < 2))) return false;

        Vec3 targetVec3 = new Vec3(nowTarget);
        double v = targetVec3.horizontal().distanceTo(current.horizontal());
        boolean passed = (goalFlag && pathblocks.size() <= 1 ? v < 0.5 : (targetVec3.y() - current.y() < -0.6 && mc.player.onGround()) ? v < 0.1 : v < 0.8) && Math.abs(targetVec3.y() - current.y()) <= 1.5 + (mc.player.hasEffect(MobEffects.JUMP_BOOST) ? Math.abs(mc.player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() / 1.25) : 0);

        // logger.info(nowTarget.getY() + " - " + current.getY() + " = " + Math.abs(nowTarget.getY() - current.getY()));

        if(pathFinder.isAllowBreak()) {
            passed &= (!PathFinder.hasBlock(nowTarget) && !PathFinder.hasBlock(nowTarget.above())) || !PathFinder.isBreakable(nowTarget) || !PathFinder.isBreakable(nowTarget.above());
        }

        if(pathFinder.isAllowPlace()) {
            BlockPos vec3_d1 = nowTarget.offset(0, -1, 0);
            boolean flag1 = pathblocks.contains(vec3_d1);
            boolean flag2 = PathFinder.hasBlock(vec3_d1) && !ToolList.getInstance().canThroughBlock(mc.level.getBlockState(vec3_d1));
            boolean flag3 = PathFinder.hasBlock(nowTarget.offset(0, -2, 0));
            boolean flag4 = pathblocks.contains(nowTarget.offset(0, 1, 0));
            boolean flag5 = PathFinder.hasBlock(getNextBlockPos(nowTarget).offset(0, -1, 0));

            passed &= (flag1 || (flag2 || (!flag4 && flag3 && flag5)));
        }

        return passed;
    }

    private void onTickStart(Minecraft mc) {

        randTurnSpeedYaw = 2 * Math.random();
        randTurnSpeedPitch = 3 * Math.random();
        tickCounter++;

    }

    private PathHub lowestCostPathHub = null;
    private PathHub currentPathHub = null;
    private PathHub lowestDistancePathHub = null;
    private int token;
    private Thread pathHubGetter;

    private void onRender(WorldRenderContext context) {
        RenderUtils.WorldRender wrLine = RenderUtils.createWorldRenderInstance(context, xray ? CustomRenderPipeline.THROUGH_WALLS_LINE : CustomRenderPipeline.NO_THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wrFill = RenderUtils.createWorldRenderInstance(context, xray ? CustomRenderPipeline.THROUGH_WALLS_FILL : CustomRenderPipeline.NO_THROUGH_WALLS_FILL);

        try {

            try {
                if(isRunning) {

                    if(pathHubGetter == null || !pathHubGetter.isAlive()) {
                        (pathHubGetter = new Thread(() -> {
                            lowestCostPathHub = currentPathHub = lowestDistancePathHub = null;
                            int thisToken = token = ToolList.getInstance().random.nextInt();
                            while(thisToken == token && isRunning && pathFinder != null && pathFinder.computing) {
                                try {
                                    currentPathHub = pathFinder.getCurrentPathHub();
                                    lowestCostPathHub = pathFinder.getLowestCostPathHub();
                                    lowestDistancePathHub = pathFinder.getLowestDistancePathHub();
                                    try {
                                        Thread.sleep(1000 / 60);
                                    } catch (InterruptedException ignored) {
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        })).start();
                    }

                    // mc.mcProfiler.endStartSection("drawPath");
                    List<BlockPos> list = pathFinder.computing ? pathFinder.getPath() : pathblocks;
                    PathRenderer.renderPath(context, list, pathFinder.computing ? 0f : 1, pathFinder.computing ? 0 : 1, pathFinder.computing ? 1 : 0, xray, false);
                    if(pathFinder.computing) {

                        if(PathFinder.pathFinderDebug) {
                            // mc.mcProfiler.endStartSection("drawDebugWorkingPath");
                            for (PathHub pathHub : pathFinder.getWorkingPathHubList()) {
                                RenderUtils.renderESP(wrLine, pathHub.getLoc(), 1, 0.5f, 0, 1, false);
                                RenderUtils.renderESP(wrFill, pathHub.getLoc(), 1, 0.5f, 0, 1, true);
                            }
                        }

                        PathHub pathHub = lowestCostPathHub;
                        if(pathHub != null) {
                            List<BlockPos> list1 = pathHub.getPathway();
                            PathRenderer.renderPath(context, list1, 0.5f, 0, 1, xray, true);
                        }

                        pathHub = lowestDistancePathHub;
                        if(pathHub != null) {
                            List<BlockPos> list2 = pathHub.getPathway();
                            PathRenderer.renderPath(context, list2, 1, 0, 0.5f, xray, true);
                        }
                    }

                }
                if(isMinerAlive()) {
                    if(blockMiner.nextPos != null) {
                        RenderUtils.renderESP(wrLine, blockMiner.nextPos, 1,0.3f,0, 1, false);
                        RenderUtils.renderESP(wrFill, blockMiner.nextPos, 1,0.3f,0, 1, true);
                    }

                    for (AbstractMap.SimpleEntry<BlockPos, Miner.ScoreGenerator> goal : blockMiner.getGoals()) {
                        RenderUtils.renderESP(wrLine, goal.getKey(), 1,0.5f,0, 1, false);
                    }
                }

                if(isFollowerAlive()) {
                    if(entityPos != null) {
                        RenderUtils.renderESP(wrLine, entityPos, 0, 1, 1, 1, false, 0.2);
                        RenderUtils.renderESP(wrFill, entityPos, 0, 1, 1, 1, true, 0.2);
                    }
                }

                if (pathFinder != null) {
                    RenderUtils.renderTrace(wrLine, pathFinder.endPath, 0, 1, 0, 1);

                    RenderUtils.renderESP(wrLine, pathFinder.startPath, 0, 1, 1, 1, false);
                    RenderUtils.renderESP(wrLine, pathFinder.endPath, 0, 1, 0, 1, false);
                }
            } catch (NullPointerException | IndexOutOfBoundsException e) {

            }
        } catch (ConcurrentModificationException e) {
        }
    }

    public void stopExecution() {
        PathFinder.setDestroyed(true);
        if(pathFinder != null) {
            pathFinder.flag = false;
        }
        if(follower != null) {
            follower.stop0();
            follower = null;
        }
        if(blockMiner != null) {
            blockMiner.stop0();
            blockMiner = null;
        }
        isRunning = false;
    }

    public void finishTaskQuickly() {
        if(pathFinder != null) pathFinder.flag = false;
        isRunning = false;
    }

    public void startExecution(Vec3 goal, boolean shouldRestorePerspective) {
        startExecution(goal, shouldRestorePerspective, 1000, 5, false);
    }

    public void startExecution(Vec3 goal, boolean shouldRestorePerspective, boolean goalFlag) {
        startExecution(goal, shouldRestorePerspective, 1000, 5, goalFlag);
    }

    public void startExecution(Vec3 goal, boolean shouldRestorePerspective, int loop, int depth, boolean goalFlag1) {
        if(pathFinder != null) PathFinder.setDestroyed(true);
        Runnable runnable = () -> {
            setLoopAndDepth(loop, depth);
            pathFinder = new PathFinder(BlockPos.containing(mc.player.getPosition(1)), BlockPos.containing(goal));

            reset = shouldRestorePerspective;
            goalFlag = goalFlag1;
            if(shouldRestorePerspective) {
                resetYaw = InputSimulator.getPlayerYaw();
                resetPitch = InputSimulator.getPlayerPitch();
            }

            interrupt();
        };
        if(isPaused()) resumePF();
        if(isRunning) {
            finishTaskQuickly();

            FutureTask<Void> task = new FutureTask<>(() -> {
                logger.warn("等待实例锁释放...");
                synchronized (PathFinderExecutor.this) {
                }
                logger.warn("已释放!");
            }, null);

            new Thread(task).start();
            new Thread(() -> {
                try {
                    task.get(2, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    e.printStackTrace();
                }
                runnable.run();
            }).start();
        } else {
            runnable.run();
        }
    }

    public BlockPos getGoalBlock() {
        if(pathFinder != null) return pathFinder.endPath; else return BlockPos.ZERO;
    }

    public void startFollowEntity(List<EntityFollower.NameInfo> entityName, boolean tryAttack, boolean blacklistUnreachable) {
        startFollowEntity(entityName, tryAttack, blacklistUnreachable, null);
    }
    public void startFollowEntity(List<EntityFollower.NameInfo> entityName, boolean tryAttack, boolean blacklistUnreachable, EntityFollower.EntityFollowerConfig customConfig) {
        if(isRunning) {
            stopExecution();
        }

        if(follower != null) follower.stop0();
        follower = new EntityFollower(entityName, tryAttack, blacklistUnreachable, customConfig);
        follower.setName("Entity " + entityName + " Follower Thread");
        follower.start();
    }

    public void startFollowEntity(List<EntityFollower.NameInfo> entityName, List<EntityFollower.NameInfo> avoidEntityName, boolean tryAttack, boolean blacklistUnreachable) {
        startFollowEntity(entityName, avoidEntityName, tryAttack, blacklistUnreachable, null);
    }
    public void startFollowEntity(List<EntityFollower.NameInfo> entityName, List<EntityFollower.NameInfo> avoidEntityName, boolean tryAttack, boolean blacklistUnreachable, EntityFollower.EntityFollowerConfig customConfig) {
        if(isRunning) {
            stopExecution();
        }

        if(follower != null) follower.stop0();
        follower = new EntityFollower(entityName, avoidEntityName, tryAttack, blacklistUnreachable, customConfig);
        follower.setName("Entity " + entityName + " Follower Thread");
        follower.start();
    }

    public void startMine(List<Map.Entry<Block, Integer>> list) {
        if(isRunning) {
            stopExecution();
        }

        if(blockMiner != null) blockMiner.stop0();
        blockMiner = new Miner(list);
        blockMiner.setName("Block Miner Thread");
        blockMiner.start();
    }

    private void setLoopAndDepth(int loop, int depth) {
        this.loop = loop;
        this.depth = depth;
    }

    public void pausePF() {
        paused = true;

        InputSimulator.setForward(false);
        InputSimulator.setJump(false);
    }

    public void resumePF() {
        timeout = System.currentTimeMillis();
        paused = false;
    }

    public static double[] getBreakBlockYawAndPitch1(BlockPos blockPos) {
        return MacroManagerListener.pathFinderExecutor.getBreakBlockYawAndPitch(blockPos);
    }
    // 获取方块的yaw和pitch，同时检查方块是否可以被选中和是否被遮挡
    private double[] getBreakBlockYawAndPitch(BlockPos blockPos) {
        // 获取玩家坐标
        double playerX = mc.player.getX();
        double playerY = mc.player.getY() + mc.player.getEyeHeight();
        double playerZ = mc.player.getZ();

        // 计算玩家与方块的距离
        double distance = Math.sqrt(Math.pow(blockPos.getX() + 0.5 - playerX, 2) +
                Math.pow(blockPos.getY() + 0.5 - playerY, 2) +
                Math.pow(blockPos.getZ() + 0.5 - playerZ, 2));

        // 获取方块类型
        BlockState blockState = mc.level.getBlockState(blockPos);
        Block block = blockState.getBlock();

        // 如果距离大于4，返回null
        if (distance > (!ToolList.getInstance().isFullBlock(blockState) ? 2 : 4)) {
            return null;
        }

        // 检查方块是否是水、岩浆或空气
        if (block == Blocks.WATER || block == Blocks.LAVA || block == Blocks.AIR) {
            return null; // 如果方块不可选中，返回null
        }

        // 计算方块中心坐标
        double blockCenterX = blockPos.getX() + 0.5;
        double blockCenterY = blockPos.getY() + 0.5;
        double blockCenterZ = blockPos.getZ() + 0.5;

        Vec3[][] edges2 = new Vec3[][] {
                {
                        new Vec3(blockCenterX, blockCenterY, blockCenterZ),
                        new Vec3(blockCenterX - 0.51, blockCenterY, blockCenterZ),
                        new Vec3(blockCenterX + 0.51, blockCenterY, blockCenterZ),
                        new Vec3(blockCenterX, blockCenterY - 0.51, blockCenterZ),
                        new Vec3(blockCenterX, blockCenterY + 0.51, blockCenterZ),
                        new Vec3(blockCenterX, blockCenterY, blockCenterZ - 0.51),
                        new Vec3(blockCenterX, blockCenterY, blockCenterZ + 0.51),
                        new Vec3(blockCenterX - 0.49, blockCenterY, blockCenterZ),
                        new Vec3(blockCenterX + 0.49, blockCenterY, blockCenterZ),
                        new Vec3(blockCenterX, blockCenterY - 0.49, blockCenterZ),
                        new Vec3(blockCenterX, blockCenterY + 0.49, blockCenterZ),
                        new Vec3(blockCenterX, blockCenterY, blockCenterZ - 0.49),
                        new Vec3(blockCenterX, blockCenterY, blockCenterZ + 0.49)
                },
                {
                        new Vec3(blockCenterX + 0.49, blockCenterY + 0.25, blockCenterZ + 0.25),
                        new Vec3(blockCenterX + 0.49, blockCenterY - 0.25, blockCenterZ + 0.25),
                        new Vec3(blockCenterX + 0.49, blockCenterY + 0.25, blockCenterZ - 0.25),
                        new Vec3(blockCenterX + 0.49, blockCenterY - 0.25, blockCenterZ - 0.25),

                        new Vec3(blockCenterX - 0.49, blockCenterY + 0.25, blockCenterZ + 0.25),
                        new Vec3(blockCenterX - 0.49, blockCenterY - 0.25, blockCenterZ + 0.25),
                        new Vec3(blockCenterX - 0.49, blockCenterY + 0.25, blockCenterZ - 0.25),
                        new Vec3(blockCenterX - 0.49, blockCenterY - 0.25, blockCenterZ - 0.25),

                        new Vec3(blockCenterX + 0.25, blockCenterY + 0.49, blockCenterZ + 0.25),
                        new Vec3(blockCenterX - 0.25, blockCenterY + 0.49, blockCenterZ + 0.25),
                        new Vec3(blockCenterX + 0.25, blockCenterY + 0.49, blockCenterZ - 0.25),
                        new Vec3(blockCenterX - 0.25, blockCenterY + 0.49, blockCenterZ - 0.25),

                        new Vec3(blockCenterX + 0.25, blockCenterY - 0.49, blockCenterZ + 0.25),
                        new Vec3(blockCenterX - 0.25, blockCenterY - 0.49, blockCenterZ + 0.25),
                        new Vec3(blockCenterX + 0.25, blockCenterY - 0.49, blockCenterZ - 0.25),
                        new Vec3(blockCenterX - 0.25, blockCenterY - 0.49, blockCenterZ - 0.25),

                        new Vec3(blockCenterX + 0.25, blockCenterY + 0.25, blockCenterZ + 0.49),
                        new Vec3(blockCenterX - 0.25, blockCenterY + 0.25, blockCenterZ + 0.49),
                        new Vec3(blockCenterX + 0.25, blockCenterY - 0.25, blockCenterZ + 0.49),
                        new Vec3(blockCenterX - 0.25, blockCenterY - 0.25, blockCenterZ + 0.49),

                        new Vec3(blockCenterX + 0.25, blockCenterY + 0.25, blockCenterZ - 0.49),
                        new Vec3(blockCenterX - 0.25, blockCenterY + 0.25, blockCenterZ - 0.49),
                        new Vec3(blockCenterX + 0.25, blockCenterY - 0.25, blockCenterZ - 0.49),
                        new Vec3(blockCenterX - 0.25, blockCenterY - 0.25, blockCenterZ - 0.49)
                }
        };
        for(Vec3[] edges1 : edges2) {
            List<Vec3> edges = Arrays.asList(edges1);
            Vec3 v = new Vec3(playerX, playerY, playerZ);
            edges.sort(Comparator.comparingDouble(vec3 -> vec3.distanceToSqr(v)));

            for(Vec3 edge : edges) {
                HitResult movingObjectPosition = ToolList.getInstance().predictPlayerAimBlock(v, edge);
                if (movingObjectPosition instanceof BlockHitResult hitResult && hitResult.getType() == HitResult.Type.BLOCK && blockPos.equals(hitResult.getBlockPos())) {

                    // 计算目标位置的中心
                    double targetCenterX = edge.x();
                    double targetCenterY = edge.y();
                    double targetCenterZ = edge.z();

                    // 计算目标位置的偏移量
                    double deltaX = targetCenterX - playerX;
                    double deltaY = targetCenterY - playerY;
                    double deltaZ = targetCenterZ - playerZ;

                    // 计算 yaw 和 pitch
                    float yaw = (float) (Math.atan2(deltaZ, deltaX) * (180 / Math.PI)) - 90; // Yaw
                    float pitch = (float) -(Math.atan2(deltaY, Math.sqrt(deltaX * deltaX + deltaZ * deltaZ)) * (180 / Math.PI)); // Pitch
                    // 返回yaw和pitchw
                    return new double[] {yaw, pitch};
                }
            }
        }

        return null;
    }

    private void rorateYaw(float yaw) {
        // if(yaw < -179) yaw = Math.abs(yaw);
        if(ToolList.getInstance().random.nextBoolean()) {
            targetYaw = yaw;
            rotationYaw = fun(rotationYaw, yaw);
        }
    }

    private float fun(float start, float target) {
        // 计算 target 与 start 的差值
        float delta = target - start;

        // 将差值调整到 [-180, 180] 范围
        delta = normalizeAngle(delta);

        // 返回与 target 等价且最接近 start 的值
        float result = start + delta;
        return result;
    }

    /**
     * 将角度归一化到 [-180, 180] 范围。
     */
    private float normalizeAngle(float angle) {
        // 将角度调整到 [-360, 360] 范围
        angle = angle % 360;

        // 进一步调整到 [-180, 180] 范围
        if (angle > 180) {
            angle -= 360;
        } else if (angle < -180) {
            angle += 360;
        }

        return angle;
    }

    private void roratePitch(float pitch) {
        if(ToolList.getInstance().random.nextBoolean()) rotationPitch = pitch;
    }

    private float getYawByPercent(float startYaw, float endYaw, float percent) {
        return getYawByPercent(startYaw, endYaw, percent, false);
    }
    private float getYawByPercent(float startYaw, float endYaw, float percent, boolean tryToBypassSensitive) {

        if(tryToBypassSensitive) percent *= (float) (randTurnSpeedYaw*randTurnSpeedYaw);

        float delta = (endYaw - startYaw) % 360;

        // 将差值归一化到 -180 ~ 180 的范围内
        if (delta > 180) {
            delta -= 360;
        } else if (delta < -180) {
            delta += 360;
        }

        boolean isNegative = delta < 0;
        // 计算中间值
        float absDelta = Math.abs(delta);
        float midYaw = tryToBypassSensitive ? ((float) (startYaw + Math.sqrt(absDelta < 7 ? (absDelta) : absDelta + 20) * (isNegative ? -2 : 2) * percent)) : startYaw + delta * percent;

        // 保证返回值在 -180 ~ 180 范围内
        if (midYaw > 180) {
            midYaw -= 360;
        } else if (midYaw < -180) {
            midYaw += 360;
        }

        return midYaw;
    }

    private float getPitchByPercent(float startPitch, float endPitch, float percent) {
        return getPitchByPercent(startPitch, endPitch, percent, false);
    }
    private float getPitchByPercent(float startPitch, float endPitch, float percent, boolean tryToBypassSensitive) {

        if(tryToBypassSensitive) percent *= (float) (randTurnSpeedPitch * randTurnSpeedPitch);
        float delta = (endPitch - startPitch) % 360;

        // 将差值归一化到 -180 ~180 的范围内
        if (delta >180) {
            delta -=360;
        } else if (delta < -180) {
            delta +=360;
        }

        //计算所需百分比的位置
        float resultPitch = startPitch + delta * percent;

        // 确保返回值在 -90 ~90 范围内
        if (resultPitch >90) {
            resultPitch =90; // 超过90时固定为90
        } else if (resultPitch < -90) {
            resultPitch = -90; //低于-90时固定为-90
        }

        return resultPitch;
    }

    private double[] getPlaceBlockYawAndPitch(BlockPos blockPos) {
// 获取玩家坐标
        double playerX = mc.player.getX();
        double playerY = mc.player.getY() + mc.player.getEyeHeight();
        double playerZ = mc.player.getZ();

        // 计算玩家与方块的距离
//        double distance = Math.sqrt(Math.pow(blockPos.getX() - playerX, 2) +
//                Math.pow(blockPos.getY() - playerY, 2) +
//                Math.pow(blockPos.getZ() - playerZ, 2));
//
//        // 如果距离大于2，返回null
//        if (distance > 4) {
//            return null;
//        }

        // 计算方块中心坐标
        double blockCenterX = blockPos.getX() + 0.5;
        double blockCenterY = blockPos.getY() + 0.5;
        double blockCenterZ = blockPos.getZ() + 0.5;

        Vec3[] edges = new Vec3[] {
                new Vec3(blockCenterX - 0.51, blockCenterY, blockCenterZ),
                new Vec3(blockCenterX + 0.51, blockCenterY, blockCenterZ),
                new Vec3(blockCenterX, blockCenterY - 0.51, blockCenterZ),
                new Vec3(blockCenterX, blockCenterY + 0.51, blockCenterZ),
                new Vec3(blockCenterX, blockCenterY, blockCenterZ - 0.51),
                new Vec3(blockCenterX, blockCenterY, blockCenterZ + 0.51)
//                new Vec3(blockCenterX - 0.49, blockCenterY, blockCenterZ),
//                new Vec3(blockCenterX + 0.49, blockCenterY, blockCenterZ),
//                new Vec3(blockCenterX, blockCenterY - 0.49, blockCenterZ),
//                new Vec3(blockCenterX, blockCenterY + 0.49, blockCenterZ),
//                new Vec3(blockCenterX, blockCenterY, blockCenterZ - 0.49),
//                new Vec3(blockCenterX, blockCenterY, blockCenterZ + 0.49)
        };

        for(Vec3 edge : edges) {

            HitResult movingObjectPosition = ToolList.getInstance().predictPlayerAimBlock(new Vec3(playerX, playerY, playerZ), edge);
            // logger.info("Aiming result: " + (movingObjectPosition == null ? null : movingObjectPosition.typeOfHit + " " + movingObjectPosition.sideHit));
            if (movingObjectPosition instanceof BlockHitResult hitResult && hitResult.getType() == HitResult.Type.BLOCK) {
                // logger.info("goal: " + blockPos + ", aim: " + movingObjectPosition.getBlockPos() + ", offset: " + movingObjectPosition.getBlockPos().offset(movingObjectPosition.sideHit, 1));
                if((blockPos.equals(hitResult.getBlockPos()) && mc.level.getBlockState(blockPos).canBeReplaced()) || blockPos.equals(hitResult.getBlockPos().relative(hitResult.getDirection(), 1))) {

                    // 计算目标位置的中心
                    double targetCenterX = edge.x();
                    double targetCenterY = edge.y();
                    double targetCenterZ = edge.z();

                    // 计算目标位置的偏移量
                    double deltaX = targetCenterX - playerX;
                    double deltaY = targetCenterY - playerY;
                    double deltaZ = targetCenterZ - playerZ;

                    // 计算 yaw 和 pitch
                    float yaw = (float) (Math.atan2(deltaZ, deltaX) * (180 / Math.PI)) - 90; // Yaw
                    float pitch = (float) -(Math.atan2(deltaY, Math.sqrt(deltaX * deltaX + deltaZ * deltaZ)) * (180 / Math.PI)); // Pitch
                    // 返回yaw和pitch
                    return new double[] {yaw, pitch};
                }
            }
        }

        return null;
    }

    public BlockPos getNextBlockPos(BlockPos blockPos) {
        try {
            int index = pathblocks.indexOf(blockPos);
            if (index != -1 && index < pathblocks.size() - 1) {
                return pathblocks.get(index + 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return blockPos;
    }

    public boolean isMinerAlive() {
        return blockMiner != null && blockMiner.isAlive();
    }

    public boolean isFollowerAlive() {
        return follower != null && follower.isAlive();
    }

}
