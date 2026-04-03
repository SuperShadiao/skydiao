package pers.XiaoShadiao.skydiao.utils.pathfinder;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.VoxelShape;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import java.util.*;

public class PathFinder {

    public interface ICustomPathfinderConfig {
        public boolean isAllowBreak();
        public boolean isAllowPlace();
        public boolean shouldUnregister();
        public int getDepth();
        public long getTimeout();
        public boolean shouldPause();
        public boolean shouldStopWhenRecieveS08();
    }

    public PathProfiler profiler = new PathProfiler();

    private static ICustomPathfinderConfig config;
    public static boolean allowPlace;

    public static boolean allowBreak;
    public static boolean pathFinderDebug;
    public static int pathFinderDebugCount;

    public static ICustomPathfinderConfig getRegisteredConfig() {
        return config;
    }

    public boolean isAllowPlace() {
        return allowPlace;
    }

    public boolean isAllowBreak() {
        return allowBreak;
    }

    public static boolean destroyed;
    public static void setDestroyed(boolean destroyed0) {
        destroyed = destroyed0;
    }

    public static final CompareHub COMPARE_HUB = new CompareHub(false);
    public static final CompareHub COMPARE_HUB_DISTANCE = new CompareHub(true);
    public final BlockPos startPath;
    public final BlockPos endPath;
    private List<BlockPos> path = new ArrayList<>();
    private final PathFinderCollection pathHubs = new PathFinderCollection();
    private final PathFinderCollection workingPathHubList = new PathFinderCollection();
    private final PathFinderCollection tempWorkPathHubList = new PathFinderCollection();

    private final PriorityQueue<PathHub> pathHubLowCost = new PriorityQueue<>(COMPARE_HUB);
    private final PriorityQueue<PathHub> pathHubLowDistance = new PriorityQueue<>(COMPARE_HUB_DISTANCE);

    public Collection<PathHub> getWorkingPathHubList() {
        return tempWorkPathHubList.getOriginalList();
    }

    private static final BlockPos[] directions = new BlockPos[]{
            new BlockPos(1, 0, 0),
            new BlockPos(-1, 0, 0),
            new BlockPos(0, 0, 1),
            new BlockPos(0, 0, -1),
    };
    public boolean computing;
    public boolean finished;

    public boolean flag;

    public boolean foundPath;

    public int waitingToExploreCount = 0;

    public static boolean isConfigAllowBreak() {
        return ConfigManager.pfAllowBreak.getValue();/*Config管理.getConfigKeyValue("pathfinderallowbreak") == 1*/
    }

    public static boolean isConfigAllowPlace() {
        return ConfigManager.pfAllowPlace.getValue();/*Config管理.getConfigKeyValue("pathfinderallowplace") == 1*/
    }

    public static void registerConfig(ICustomPathfinderConfig config) {
        if (PathFinder.config == null || PathFinder.config.shouldUnregister() || config == null) {
            PathFinder.config = config;

            if(config == null) {
                allowBreak = ConfigManager.pfAllowBreak.getValue()/*Config管理.getConfigKeyValue("pathfinderallowbreak") == 1*/ && !ToolList.mc.gameMode.getPlayerMode().isBlockPlacingRestricted();
                allowPlace = ConfigManager.pfAllowPlace.getValue()/*Config管理.getConfigKeyValue("pathfinderallowplace") == 1*/ && !ToolList.mc.gameMode.getPlayerMode().isBlockPlacingRestricted() && hasBlockInInventory();
            } else {
                allowBreak = config.isAllowBreak() && !ToolList.mc.gameMode.getPlayerMode().isBlockPlacingRestricted();
                allowPlace = config.isAllowPlace() && !ToolList.mc.gameMode.getPlayerMode().isBlockPlacingRestricted() && hasBlockInInventory();
            }
        }
    }

    public PathFinder(BlockPos startPath, BlockPos endPath) {
        if(config != null && config.shouldUnregister()) {
            ToolList.getInstance().log.info("Unregistered PF config");
            config = null;
        }

        if(config == null) {
            allowBreak = ConfigManager.pfAllowBreak.getValue()/*Config管理.getConfigKeyValue("pathfinderallowbreak") == 1*/ && !ToolList.mc.gameMode.getPlayerMode().isBlockPlacingRestricted();
            allowPlace = ConfigManager.pfAllowPlace.getValue()/*Config管理.getConfigKeyValue("pathfinderallowplace") == 1*/ && !ToolList.mc.gameMode.getPlayerMode().isBlockPlacingRestricted() && hasBlockInInventory();
        } else {
            allowBreak = config.isAllowBreak() && !ToolList.mc.gameMode.getPlayerMode().isBlockPlacingRestricted();
            allowPlace = config.isAllowPlace() && !ToolList.mc.gameMode.getPlayerMode().isBlockPlacingRestricted() && hasBlockInInventory();
        }
        this.startPath = startPath;
        this.endPath = endPath;
    }

    public List<BlockPos> getPath() {
        return computing && !this.pathHubs.isEmpty() ? ((PathHub)this.pathHubs.get(this.pathHubs.size() - 1)).getPathway() : this.path;
    }

    public PathHub getCurrentPathHub() {
        try {
            return (PathHub)this.pathHubs.get(this.pathHubs.size() - 1);
        } catch (Exception e) {
            return null;
        }
    }

    public List<BlockPos> getLowestDistancePath() {
        try {
            if(computing && !this.pathHubs.isEmpty()) {
                PathHub peek = pathHubLowDistance.peek();
                return peek == null ? Collections.emptyList() : peek.getPathway();
            } else {
                // System.out.println("Empty pre path!");
                return this.path;
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<BlockPos> getLowestCostPath() {
        try {
            if(computing && !this.pathHubs.isEmpty()) {
                PathHub peek = pathHubLowCost.peek();
                return peek == null ? Collections.emptyList() : peek.getPathway();
            } else {
                // System.out.println("Empty pre path!");
                return this.path;
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public PathHub getLowestDistancePathHub() {
        try {
            if(computing && !this.pathHubs.isEmpty()) {
                return pathHubLowDistance.peek();
            } else {
                System.out.println("Empty pre path!");
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public PathHub getLowestCostPathHub() {
        try {
            if(computing && this.pathHubs.size() > 1) {
                return pathHubLowCost.peek();
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static long getConfigTimeout() {
        return ConfigManager.pfTimeout.getValue();
    }

    public static int getConfigDepth() {
        return 1000;
    }

    public void compute() {
        this.compute(1000, 4);
    }

    public void compute(int loops, int depth) {
        this.compute(loops, depth, Collections.emptyList());
    }

    public synchronized void compute(@Deprecated int loops, int depth, List<BlockPos> blacklistedPos) {
        destroyed = false;
        flag = true;
        computing = true;
        if(finished) return;
        boolean pathHasFound = false;
        this.path.clear();
        this.workingPathHubList.clear();
        ArrayList<BlockPos> initPath = new ArrayList<>();
        initPath.add(this.startPath);

        tempWorkPathHubList.clear();
        PathFinderCollection tempWorkList = tempWorkPathHubList; // new ArrayList<>(this.workingPathHubList);
        // tempWorkPathHubList = tempWorkList;
        tempWorkList.add(new PathHub(this.startPath, null, initPath, this.startPath.distSqr(this.endPath), 0.0, 0.0, this.endPath));

        long timeoutTime = ConfigManager.pfTimeout.getValue();

        if(config != null) {
            timeoutTime = config.getTimeout();
            loops = config.getDepth();
        }

        loops = Math.max(loops, 100);
        timeoutTime = Math.max(timeoutTime, 10);
        long startTime = System.currentTimeMillis();
        label53:
        for(int i = 0; ; ++i) {
//            if(!(i < loops)) {
//                ToolList.getInstance().log.warn("Loop ran out!");
//                break;
//            }
            if(!(!destroyed && flag)) {
                ToolList.getInstance().log.warn("Cancelled!");
                break;
            }
            if(!(System.currentTimeMillis() - startTime <= timeoutTime)) {
                ToolList.getInstance().log.warn("Time out!");
                break;
            }
            PathFinderCollection sortList = tempWorkList;
//            if(!allowPlace && tempWorkList.size() > 100 && i % 75 != 1) {
//                sortList = tempWorkList.subList(0, 50);
//            }
            boolean sortSubList = !allowPlace && tempWorkList.size() > 100 && i % 75 != 1;
            boolean flag = i > 500 && i % 75 == 1;
            profiler.start("SortPH");
            if(sortSubList) {
                sortList.sortSubList(0, 50, flag ? COMPARE_HUB_DISTANCE : COMPARE_HUB);
            } else {
                sortList.sort(flag ? COMPARE_HUB_DISTANCE : COMPARE_HUB);
            }
            profiler.end("SortPH");
//            if(allowPlace && tempWorkList.size() > 510 && flag) {
//                tempWorkList.subList(0, 500).sort(COMPARE_HUB);
//            }

            profiler.start("Sizing&Ite");
            waitingToExploreCount = tempWorkList.size();
            int j = 0;
            if (tempWorkList.isEmpty()) {
                break;
            }
            // Iterator<PathHub> it = tempWorkList.iterator();
            profiler.end("Sizing&Ite");
            while(!tempWorkList.isEmpty()) {
                profiler.start("SingleWhile");
                if (++j > depth) {
                    break;
                }
                PathHub pathHub = tempWorkList.get(0);
                tempWorkList.remove(pathHub);

                profiler.start("AddHub");
                this.pathHubs.add(pathHub);
                pathHubLowCost.add(pathHub);
                pathHubLowDistance.add(pathHub);
                profiler.end("AddHub");

                int viewDistance = Math.max(ToolList.mc.options.renderDistance().get() * 16 - 32, 32);
                BlockPos blockPos = pathHub.getLoc();
                if(blockPos.getY() >= -64 && blockPos.getY() < 320 && (
                        // !ToolList.mc.theWorld.isBlockLoaded(blockPos) ||
                        Math.abs(blockPos.getX() - startPath.getX()) > viewDistance ||
                                Math.abs(blockPos.getZ() - startPath.getZ()) > viewDistance
                )) {

                    ToolList.getInstance().log.warn("Out of the border!");
                    break label53;
                }
                if(blacklistedPos.contains(blockPos)) continue;
                if(pathHub.getLoc().getY() <= 0) continue;

//                for(Vec3 direction : directions) {
//                    Vec3 loc = pathHub.getLoc().add(direction).floor();
//                    PathBlockInfo valid = isValid(loc, false, true, false, pathHub.getPathway(), true, false, false);
//                    if((valid.isValid || new BlockPos(loc.mc()).equals(new BlockPos(endVec3Path.mc()))) && this.putHub(pathHub, loc, getCostByPBI(valid))) {
//                        pathHasFound = true;
//                        break label53;
//                    }
//                }
                profiler.start("Directions");
                for (BlockPos loc : Arrays.stream(directions)
                        .map(direction -> pathHub.getLoc().offset(direction))
                        .sorted(Comparator.comparingDouble(pos -> pos.distSqr(endPath)))
                        .toArray(BlockPos[]::new)) {
                    PathBlockInfo valid = isValid(loc, false, true, false, pathHub.getPathway(), true, false, false);
                    if((valid.isValid() || loc.equals(endPath)) && this.putHub(pathHub, loc, getCostByPBI(valid))) {
                        pathHasFound = true;
                        break label53;
                    }
                }
                profiler.end("Directions");

                profiler.start("Up");
                BlockPos loc2;
                PathBlockInfo valid = isValid(loc2 = pathHub.getLoc().offset(0, 1, 0), false, true, true, pathHub.getPathway(), false, false, false);
                profiler.end("Up");

                if(valid.isValid() && this.putHub(pathHub, loc2, getCostByPBI(valid))) {
                    pathHasFound = true;
                    break label53;
                } else {
                    profiler.start("Down");
                    valid = isValid(loc2 = pathHub.getLoc().offset(0, -1, 0), false, false, false, pathHub.getPathway(), false, true, false);
                    if(valid.isValid() && this.putHub(pathHub, loc2, getCostByPBI(valid))) {
                        pathHasFound = true;
                        break label53;
                    }
                    profiler.end("Down");
                }
                profiler.start("DownDirection");
                BlockPos temp = pathHub.getLoc().offset(0, -1, 0);

                if(!(ToolList.mc.level.getBlockState(temp).getBlock() == Blocks.AIR)) {

                    BlockPos[] vec3s = new BlockPos[] {
                            pathHub.getLoc().offset(1, -1, 0),
                            pathHub.getLoc().offset(0, -1, 1),
                            pathHub.getLoc().offset(-1, -1, -1)
                    };

                    for(BlockPos loc3 : vec3s) {
                        if ((ToolList.mc.level.getBlockState(loc3).getBlock() == Blocks.AIR)) {
                            valid = isValid(loc2 = loc3, false, false, false, pathHub.getPathway(), true, true, true);
                            if (valid.isValid() && this.putHub(pathHub, loc2, getCostByPBI(valid))
                            ) {
                                pathHasFound = true;
                                break label53;
                            }
                        }
                    }

//                    if (isValid(loc2 = pathHub.getLoc().addVector(1.0, -1.0, 0.0).floor(), false, false) && this.putHub(pathHub, loc2, 0.0)
//                            || isValid(loc2 = pathHub.getLoc().addVector(0.0, -1.0, 1.0).floor(), false, false) && this.putHub(pathHub, loc2, 0.0)
//                            || isValid(loc2 = pathHub.getLoc().addVector(-1.0, -1.0, 0.0).floor(), false, false) && this.putHub(pathHub, loc2, 0.0)
//                            || isValid(loc2 = pathHub.getLoc().addVector(0.0, -1.0, -1.0).floor(), false, false) && this.putHub(pathHub, loc2, 0.0)
//                    ) {
//                        break label53;
//                    }
                }
                profiler.end("DownDirection");
                profiler.end("SingleWhile");
            }

            profiler.start("MoveList");
            tempWorkList.addAll(workingPathHubList);
            workingPathHubList.clear();
            profiler.end("MoveList");
        }

        foundPath = pathHasFound;

        if (!pathHasFound)  {
//            this.pathHubs.sort(COMPARE_HUB_DISTANCE);
//            this.path = ((PathHub)this.pathHubs.get(0)).getPathway();
            PathHub poll = pathHubLowDistance.poll();
            this.path = poll == null ? Collections.emptyList() : poll.getPathway();
        }

        for(int i = path.size() - 1; i > 3; i--) {
            BlockPos vec1 = path.get(i);
            BlockPos vec2 = path.get(i - 1);
            BlockPos vec3 = path.get(i - 2);
            if(
                    (Math.abs(vec1.getX() - vec3.getX()) == 1 || Math.abs(vec1.getZ() - vec3.getZ()) == 1) &&
                            (vec1.getY() - vec2.getY()) == 0 &&
                            (vec2.getY() - vec3.getY()) == 1 &&
                            (!allowBreak || (!hasBlock(vec2) && !hasBlock(vec2.above()))) &&
                            (!allowPlace || (hasBlock(vec2.below())))
            ) {
                path.remove(i - 1);
            }
        }

        for(int i = path.size() - 1; i > 3; i--) {
            BlockPos vec1 = path.get(i);
            BlockPos vec2 = path.get(i - 1);
            BlockPos vec3 = path.get(i - 2);
            if(
                    Math.abs(vec1.getX() - vec3.getX()) == 1 &&
                            Math.abs(vec1.getZ() - vec3.getZ()) == 1 &&
                            /*Math.abs*/(vec1.getY() - vec3.getY()) >= -2 && (vec1.getY() - vec3.getY()) <= 0 &&
                            vec2.getY() <= vec3.getY() && vec2.getY() <= vec1.getY() &&
                            (
                                    ToolList.getInstance().isFullBlock(ToolList.mc.level.getBlockState(vec1.below())) == ToolList.getInstance().isFullBlock(ToolList.mc.level.getBlockState(vec3.below())) ||
                                    !ToolList.getInstance().isFullBlock(ToolList.mc.level.getBlockState(vec1.below())) && ToolList.getInstance().isFullBlock(ToolList.mc.level.getBlockState(vec3.below()))
                            ) &&
                            (!allowBreak || (!hasBlock(vec2) && !hasBlock(vec2.above()))) &&
                            (!allowPlace || (hasBlock(vec2.below())))
            ) {
                path.remove(i - 1);
            }
        }

        // this.path.add(endVec3Path);
        finished = true;
        computing = false;
    }

    public static boolean hasBlockBelow(BlockPos pos) {
        for (int y = pos.getY() - 1; y >= pos.getY() - 15; y--) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());

            BlockState blockState = ToolList.mc.level.getBlockState(checkPos);
            // 检查当前位置是否是水或岩浆
            Block block = blockState.getBlock();
            // 检查当前位置是否是一个实心方块
            if (block != Blocks.AIR && ToolList.getInstance().isFullBlock(blockState))   {
                return true; // 找到方块，返回true
            }
        }
        return false; // 在15格范围内没有找到方块，返回false
    }

    public static boolean hasBlockBelow_Liquid(BlockPos pos) {
        for (int y = pos.getY() - 1; y >= pos.getY() - 15; y--) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());

            BlockState blockState = ToolList.mc.level.getBlockState(checkPos);
            // 检查当前位置是否是水或岩浆
            Block block = blockState.getBlock();

            if (block != Blocks.AIR && ToolList.getInstance().isFullBlock(blockState))   {
                return false; // 找到方块，返回false
            }

            if (block == Blocks.WATER || block == Blocks.LAVA) {
                return true; // 找到水或岩浆，返回true
            }

        }
        return false; // 在15格范围内没有找到方块，返回false
    }

    public PathBlockInfo isValid(BlockPos loc, boolean checkGround, boolean checkAir, boolean isStepUp, List<BlockPos> pathBlocks, boolean isMoveAround, boolean isMoveDown, boolean isMoveAroundAndDown) {
        return isValid((int)loc.getX(), (int)loc.getY(), (int)loc.getZ(), checkGround, checkAir, isStepUp, pathBlocks, isMoveAround, isMoveDown, isMoveAroundAndDown);
    }

    public PathBlockInfo isValid(int x, int y, int z, boolean checkGround, boolean checkAir, boolean isStepUp, List<BlockPos> pathBlocks, boolean isMoveAround, boolean isMoveDown, boolean isMoveAroundAndDown)  {

        if(pathFinderDebug) {
            while(pathFinderDebugCount <= 0) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                }
            }

            pathFinderDebugCount--;
        }

        BlockPos block1 = new BlockPos(x, y, z);
        BlockPos block2 = new BlockPos(x, y + 1, z);
        BlockPos block3 = new BlockPos(x, y - 1, z);

        boolean flag1 = !isNotPassable(block1, false);
        boolean flag2 = !isNotPassable(block2, false);
        boolean flag3 = isNotPassable(block3, checkAir);

        return new PathBlockInfo(
                ((flag1 && flag2 && (flag3 || !checkGround)) || (isMoveAroundAndDown && block1.equals(endPath))) && canWalkOn(block3, checkAir, isStepUp, pathBlocks, false, isMoveAround, isMoveDown, isMoveAroundAndDown) && (!isMoveDown || !hasBlockBelow_Liquid(block1)),
                // !allowPlace ? 0 : (!hasBlock(block3) ? ((isStepUp ? 1 : !hasBlock(block3.down()) ? 2 : 2)) : 0),
                !allowPlace ? 0 : (!hasBlock(block3) ? ((isStepUp && hasBlock(block3.below()) ? 0 : isMoveDown && !hasBlockBelow(block1) ? 15 : 1.2)) : 0),
                allowBreak && (hasBlock(block1) || hasBlock(block2))
        );
    }

    private static double getCostByPBI(PathBlockInfo valid) {
        return valid.needBreak() ? 1.6 : valid.placeCost();
    }

    public static boolean isNotPassable(BlockPos block, boolean checkAir) {
        if (!allowBreak || (ConfigManager.pathfinderallowbreakwhengetslowmining.getValue() && ToolList.mc.player.hasEffect(MobEffects.MINING_FATIGUE)) || checkAir) {
            // BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
            BlockState blockState = ToolList.mc.level.getBlockState(block);
            Block b = blockState.getBlock();

            return ToolList.getInstance().isFullBlock(blockState)
                    || b instanceof SlabBlock
                    || b instanceof StairBlock
                    || b instanceof CactusBlock
                    || b instanceof ChestBlock
                    || b instanceof EnderChestBlock
                    || b instanceof SkullBlock
                    || b instanceof CrossCollisionBlock
                    || b instanceof HorizontalDirectionalBlock
                    || b instanceof FenceGateBlock
                    || b instanceof WallBlock
                    || b instanceof StainedGlassBlock
                    || b instanceof PistonBaseBlock
                    || b instanceof PistonHeadBlock
                    || b instanceof TrapDoorBlock
                    || b instanceof EndPortalBlock
                    || b instanceof EndPortalFrameBlock
                    || b instanceof BedBlock
                    || b instanceof WebBlock
                    || b instanceof BarrierBlock
                    || b instanceof WaterlilyBlock
                    || b instanceof IceBlock
                    || b instanceof SlimeBlock
                    || b instanceof CauldronBlock
                    || (
                    (b instanceof SnowLayerBlock ||
                            ToolList.mc.level.getBlockState(block.north()).getBlock() instanceof SnowLayerBlock ||
                            ToolList.mc.level.getBlockState(block.west()).getBlock() instanceof SnowLayerBlock ||
                            ToolList.mc.level.getBlockState(block.south()).getBlock() instanceof SnowLayerBlock ||
                            ToolList.mc.level.getBlockState(block.east()).getBlock() instanceof SnowLayerBlock) &&
                            ToolList.mc.level.getBlockState(block.above(2)).getBlock() != Blocks.AIR
            )
                    ;
        } else {
            return !canWalkWithBreak(block);
        }
    }

    private static boolean canWalkOn(BlockPos block, boolean checkAir, boolean isStepUp, List<BlockPos> pathBlocks, boolean ignoreBreakBlockCheck, boolean isMoveAround, boolean isMoveDown, boolean isMoveAroundAndDown)  {

        BlockPos pos1 = block.below();
        BlockState blockState1 = ToolList.mc.level.getBlockState(pos1);
        Block block1 = blockState1.getBlock();
        BlockPos pos2 = block;
        BlockState blockState2 = ToolList.mc.level.getBlockState(pos2);
        Block block2 = blockState2.getBlock();
        BlockPos lastBP = pathBlocks.getLast();
        Block lastBPDownBlock = ToolList.mc.level.getBlockState(lastBP.below()).getBlock();
        if(isMoveAround && pathBlocks.size() >= 2 && pathBlocks.get(pathBlocks.size() - 2).getY() == (lastBP.getY() + 1) && lastBPDownBlock.equals(Blocks.AIR))   {
            return false;
        }

        BlockState lastBPDownDownBlockState = ToolList.mc.level.getBlockState(lastBP.below(2));
        Block lastBPDownDownBlock = lastBPDownDownBlockState.getBlock();
        BlockState BSS = ToolList.mc.level.getBlockState(pos2);

        if((isMoveAround && lastBPDownBlock == Blocks.AIR && pathBlocks.contains(lastBP.offset(0, -1, 0)) && (
                lastBPDownDownBlock instanceof CarpetBlock ||
                        lastBPDownDownBlock instanceof CropBlock ||
                        (
                                (
                                        lastBPDownDownBlock instanceof SlabBlock &&
                                                (
                                                        lastBPDownDownBlockState.getValue(SlabBlock.TYPE) != SlabType.DOUBLE &&
                                                                lastBPDownDownBlockState.getValue(SlabBlock.TYPE) == SlabType.BOTTOM)
                                ) &&
                                        (!(block2 instanceof SlabBlock) || blockState2.getValue(SlabBlock.TYPE) == SlabType.DOUBLE || BSS.getValue(SlabBlock.TYPE) == SlabType.TOP)) ||
                        lastBPDownDownBlock instanceof SignBlock ||
                        lastBPDownDownBlock instanceof LiquidBlock ||
                        lastBPDownDownBlock instanceof VineBlock ||
                        lastBPDownDownBlock instanceof BaseRailBlock
        ))
                ||
                (isStepUp && allowPlace &&

                        (
                                block1 instanceof CarpetBlock ||
                                        block1 instanceof CropBlock ||
                                        (block1 instanceof SlabBlock && blockState1.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) ||
                                        block1 instanceof SignBlock ||
                                        block1 instanceof LiquidBlock ||
                                        block1 instanceof VineBlock ||
                                        block1 instanceof BaseRailBlock
                        )
                        && !block1.equals(Blocks.AIR))
        ) return false;

        if(allowBreak && !allowPlace && !ignoreBreakBlockCheck) {
            if(pathBlocks.contains(pos1)) {
                return false;
            }
        }
        if(isStepUp) block2 = block1; else block1 = block2;

        if(allowPlace) {

            BlockPos upperBlock = block.above(2);
            return (!isMoveAroundAndDown && (!isMoveAround || !pathBlocks.contains(upperBlock) || !(hasBlock(pos2))));

//            if((isMoveAroundAndDown || (isMoveAround && pathBlocks.contains(new Vec3(upperBlock)))) && ToolList.mc.theWorld.getBlockState(pos2).getBlock() instanceof BlockAir) {
//                return false;
//            }
//            return true;
        }

        return !(
                block2 instanceof FenceBlock
                        || block2 instanceof FenceGateBlock
        )
                && !(
                block2 instanceof WallBlock
        )

                &&

                !(checkAir && (
                        (
                                (block2 instanceof AirBlock/* || block2 instanceof BlockLiquid*/ || block2 instanceof BushBlock || !isSlabCanWalk(pos2) || block2 instanceof SnowLayerBlock) &&
                                        (block1 instanceof AirBlock/* || block1 instanceof BlockLiquid*/ || block1 instanceof BushBlock || !isSlabCanWalk(pos1) || block1 instanceof SnowLayerBlock) ||
                                        (block1 instanceof WebBlock || block2 instanceof WebBlock || block1 instanceof FenceBlock || block2 instanceof FenceBlock || block1 instanceof FenceGateBlock || block2 instanceof FenceGateBlock || block1 instanceof WallBlock || block2 instanceof WallBlock || block1 instanceof TrapDoorBlock || block2 instanceof TrapDoorBlock || block1 instanceof BaseRailBlock || block2 instanceof BaseRailBlock)
                        )
                ));
    }

    public static boolean isSlabCanWalk(BlockPos pos) {
        BlockState blockState = ToolList.mc.level.getBlockState(pos);
        Block block = blockState.getBlock();
        return !(block instanceof SlabBlock) || blockState.getValue(SlabBlock.TYPE) == SlabType.TOP || blockState.getValue(SlabBlock.TYPE) == SlabType.TOP;
    }

    public PathHub doesHubExistAt(BlockPos loc) {
        PathHub pathHub;
        if((pathHub = this.pathHubs.getByPosition(loc)) != null) return pathHub;
        if((pathHub = this.workingPathHubList.getByPosition(loc)) != null) return pathHub;
        if((pathHub = this.tempWorkPathHubList.getByPosition(loc)) != null) return pathHub;
        return null;
    }

    public boolean putHub(PathHub parent, BlockPos loc, double cost) {

        PathHub existingPathHub = this.doesHubExistAt(loc);
        // cost += 1;
        double totalCost = cost;
        if (parent != null) {
            double penalty = !allowPlace && parent.getLoc().distSqr(this.endPath) < loc.distSqr(this.endPath) ? 2 : 0;
            totalCost += parent.getMaxCost() + penalty;
        }

        if (existingPathHub == null) {
            if (loc.getX() == this.endPath.getX() && loc.getY() == this.endPath.getY() && loc.getZ() == this.endPath.getZ()
                    || loc.distSqr(this.endPath) <= 0.2) {
                this.path.clear();
                this.path = ((PathHub)Objects.requireNonNull(parent)).getPathway();
                this.path.add(loc);
                return true;
            }

            ArrayList<BlockPos> path = new ArrayList<>(((PathHub)Objects.requireNonNull(parent)).getPathway());
            path.add(loc);
            this.workingPathHubList.add(new PathHub(loc, parent, path, loc.distSqr(this.endPath), cost, totalCost, this.endPath));
        } else if (existingPathHub.getCurrentCost() > cost || existingPathHub.getMaxCost() > totalCost) {
            ArrayList<BlockPos> path = new ArrayList<>(((PathHub)Objects.requireNonNull(parent)).getPathway());
            path.add(loc);
            existingPathHub.setLoc(loc);
            existingPathHub.setParentPathHub(parent);
            existingPathHub.setPathway(path);
            existingPathHub.setSqDist(loc.distSqr(this.endPath));
            existingPathHub.setCurrentCost(cost);
            existingPathHub.setMaxCost(totalCost);
        }

        return false;
    }

    public record CompareHub(boolean distanceOnly) implements Comparator<PathHub> {

        public int compare(PathHub o1, PathHub o2) {
                double difference = (o1.getDist() + (distanceOnly ? 0 : o1.getMaxCost())) -
                        (o2.getDist() + (distanceOnly ? 0 : o2.getMaxCost()));
                return Double.compare(difference, 0);
            }
        }

    public static boolean hasBlockInInventory() {
        LocalPlayer player = ToolList.mc.player;
        for (int i = 0; i < 9; i++) { // 检查物品栏的前 9 个槽
            ItemStack itemStack = player.getInventory().getItem(i);
            if (itemStack.getItem() instanceof BlockItem) {
                return true;
            }
        }
        return false; // 未找到方块，返回 false
    }

    public static boolean isBreakable(BlockPos pos) {
        Block block = ToolList.mc.level.getBlockState(pos).getBlock();

        for (BlockPos offset : new BlockPos[] {
                pos.above(),    // 上方
                // pos.down(),  // 下方
                pos.north(), // 前方
                pos.south(), // 后方
                pos.east(),  // 右方
                pos.west()   // 左方
        }) {
            Block neighborBlock = ToolList.mc.level.getBlockState(offset).getBlock();
            // 如果周围方块是水或岩浆，返回false
            if (neighborBlock == Blocks.WATER || neighborBlock == Blocks.LAVA) {
                return false; // 周围有水或岩浆，不可破坏
            }
        }
        return block != Blocks.BEDROCK &&
                block != Blocks.OBSIDIAN &&
                block != Blocks.END_PORTAL &&
                block != Blocks.END_PORTAL_FRAME &&
                block != Blocks.COMMAND_BLOCK &&
                block != Blocks.CHAIN_COMMAND_BLOCK &&
                block != Blocks.REPEATING_COMMAND_BLOCK &&
                block != Blocks.BARREL &&
                block != Blocks.ENDER_CHEST;
    }

    public static boolean canWalkWithBreak(BlockPos pos) {
        return isBreakable(pos);
    }

    public static boolean hasBlock(BlockPos pos) {
        Block block = ToolList.mc.level.getBlockState(pos).getBlock();

        // 检查方块是否存在且可被选中
        if (block != Blocks.AIR) {
            // 水和岩浆不可被选中
            if (block == Blocks.WATER || block == Blocks.LAVA) {
                return false; // 水和岩浆不可被选中
            }
            return true; // 其他方块可被选中
        }

        return false; // 方块不存在或不可被选中
    }

    public static class PathProfiler {

        private final Map<String, Profiler> profilingMap = new HashMap<>();

        public void start(String name) {
            Profiler profiler = profilingMap.computeIfAbsent(name, k -> new Profiler(name));
            profiler.start();
        }
        public void end(String name) {
            Profiler profiler = profilingMap.get(name);
            if (profiler != null) {
                profiler.end();
            }
        }
        public Collection<Profiler> getResults() {
            return profilingMap.values();
        }

        public static class Profiler {
            private long startTimeTemp;
            private long startTime;
            private long endTime;

            private final String name;

            public Profiler(String name) {
                this.name = name;
            }

            public void start() {
                startTimeTemp = System.nanoTime();
            }
            public void end() {
                startTime = startTimeTemp;
                endTime = System.nanoTime();
            }
            public long getTime() {
                return endTime - startTime;
            }

            public String getName() {
                return name;
            }
        }

    }
}


