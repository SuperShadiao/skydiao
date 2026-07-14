package pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.task;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.utils.PosLine;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class BFTaskManiaHandler extends BFTask {

    private static List<BlockPos> safePositions = new ArrayList<>();
    private static PosLine line1 = PosLine.DEFAULT;
    private static PosLine line2 = PosLine.DEFAULT;
    private boolean startFlag = false;
    private int tick = 23;
    private BlockPos cache = null;
    private boolean tcFlag = false;

    private boolean getKnockback;

    public void flagKB() {
        getKnockback = true;
    }

    @Override
    public void onChat(Component chat) {
        String message = ToolList.getInstance().deleteColorCode(chat.getString());
        if(message.toLowerCase().contains("from twinclaws")) {
            tcFlag = true;
        }
    }

    @Override
    public void tickUpdate() {

        if(tick > 0) {
            return;
        }

        BlockPos playerPos = BlockPos.containing(mc.player.getX(), mc.player.getY() + 0.5, mc.player.getZ());
        Entity boss = slayerInstance.getBloodfiendEntityInstance();

        boolean isUnsafe = cache == null || !SafeZoneFinder.isSafeBlock(mc.level, cache);
        boolean isCurrentUnsafe = !SafeZoneFinder.isPlayerInSafeZone(mc.level, playerPos);
        if(!MacroManagerListener.pathFinderExecutor.isRunning() || isUnsafe || tcFlag) {
            if(startFlag || isCurrentUnsafe) {
                startFlag = false;
                tcFlag = false;

                BlockPos nearestBPResult;
                if (cache == null || isUnsafe) {
                    nearestBPResult = SafeZoneFinder.findBestSafePosition(mc.level, playerPos, BlockPos.containing(boss.getX(), boss.getY(), boss.getZ()), 30);
                    if (nearestBPResult == null) nearestBPResult = playerPos.above();
                    cache = nearestBPResult;
                }

                double distance = BlockPos.containing(boss.getX(), 0, boss.getZ()).distToCenterSqr(cache.getX(), 0, cache.getZ());
                MacroManagerListener.pathFinderExecutor.startExecution(new Vec3(cache.above()), false, distance > 3);
            }
        }

        if(getKnockback) {
            getKnockback = false;
            startFlag = true;
        }

    }

    @Override
    public void onTaskResume() {

    }

    @Override
    public void onTaskStart(BFTask lastTask) {
        if(lastTask instanceof BFTaskImpelHandler) {
            /*if(((BFTaskImpelHandler) lastTask).getType() == BFTaskImpelHandler.ImpelType.JUMP) */return;
        }
        startFlag = true;
    }

    @Override
    public boolean isTaskFinished() {
        return tick <= 0 && checkBossHasntSkill("mania");// Optional.ofNullable(slayerInstance.getBloodfiendInstance().skyblockMobsInfoSlayerTimeAndSkills).map(e -> !e.getName().contains("mania")).orElse(false);
    }

    @Override
    public String getTaskName() {
        return "Doing Mania";
    }

    @Override
    public int priority() {
        return tick <= 0 && (!SafeZoneFinder.isPlayerInSafeZone(mc.level, BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ())) || (MacroManagerListener.pathFinderExecutor.isRunning() && MacroManagerListener.pathFinderExecutor.pathblocks.size() < 4)) ? priorityManiaRunning : priorityMania;
    }

    @Override
    public void sleepAlsoTickUpdate() {
        if(tick > 0) {
            tick--;
        }
    }

    @Override
    public void render(RenderUtils.WorldRender wrLine, RenderUtils.WorldRender wrFill) {
        LivingEntity bossEntity = slayerInstance.getBloodfiendEntityInstance();
        if(slayerInstance.getBloodIchorEntity() != null && bossEntity != null) {

//            Tessellator tessellator = Tessellator.getInstance();
//            WorldRenderer worldRenderer = tessellator.getWorldRenderer();
//
//            // 设置 OpenGL 状态
//            if(true) GL11.glDisable(2929);
//            GL11.glEnable(GL11.GL_BLEND);
//            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
//            GL11.glDisable(GL11.GL_TEXTURE_2D);
//            GL11.glEnable(2848); // LINE_SMOOTH
//            GL11.glDepthMask(false);
//            // GL11.glDisable(GL11.GL_DEPTH_TEST);
//            GL11.glLineWidth(3.0F); // 设置线宽
//
//            GL11.glColor4f(1, 0, 0, 1.0F); // 设置颜色
//            // 开始绘制线段
//            worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
//
//            worldRenderer.pos(line1.x1 - ToolList.mc.getRenderManager().viewerPosX, bossEntity.posY + bossEntity.getEyeHeight() - ToolList.mc.getRenderManager().viewerPosY, line1.z1 - ToolList.mc.getRenderManager().viewerPosZ).color(1, 0, 0, 1.0F).endVertex();
//            worldRenderer.pos(line1.x2 - ToolList.mc.getRenderManager().viewerPosX, bossEntity.posY + bossEntity.getEyeHeight() - ToolList.mc.getRenderManager().viewerPosY, line1.z2 - ToolList.mc.getRenderManager().viewerPosZ).color(1, 0, 0, 1.0F).endVertex();
//
//            // 结束绘制
//            tessellator.draw();
//            GL11.glColor4f(0, 1, 0, 1.0F); // 设置颜色
//            // 开始绘制线段
//            worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
//
//            worldRenderer.pos(line2.x1 - ToolList.mc.getRenderManager().viewerPosX, bossEntity.posY + bossEntity.getEyeHeight() - ToolList.mc.getRenderManager().viewerPosY, line2.z1 - ToolList.mc.getRenderManager().viewerPosZ).color(0, 1, 0, 1.0F).endVertex();
//            worldRenderer.pos(line2.x2 - ToolList.mc.getRenderManager().viewerPosX, bossEntity.posY + bossEntity.getEyeHeight() - ToolList.mc.getRenderManager().viewerPosY, line2.z2 - ToolList.mc.getRenderManager().viewerPosZ).color(0, 1, 0, 1.0F).endVertex();
//
//            // 结束绘制
//            tessellator.draw();
//
//            // 恢复 OpenGL 状态
//            GL11.glDisable(2848); // LINE_SMOOTH
//            GL11.glDepthMask(true);
//            if(true) GL11.glEnable(2929);
//            GL11.glEnable(GL11.GL_TEXTURE_2D);
//            GL11.glEnable(GL11.GL_DEPTH_TEST);
//            GL11.glDisable(GL11.GL_BLEND);

            RenderUtils.renderWorldLine(wrLine, line1.x1, bossEntity.getY() + bossEntity.getEyeHeight(), line1.z1, line1.x2, bossEntity.getY() + bossEntity.getEyeHeight(), line1.z2, 1, 0, 0, 1.0F);
            RenderUtils.renderWorldLine(wrLine, line2.x1, bossEntity.getY() + bossEntity.getEyeHeight(), line2.z1, line2.x2, bossEntity.getY() + bossEntity.getEyeHeight(), line2.z2, 0, 1, 0, 1.0F);

            // RenderUtil.renderWayPoint("DstToIchor: " + line1.distanceTo(mc.thePlayer.posX, mc.thePlayer.posZ), bossEntity.getPosition().down(2), 1);
        }

        if(slayerInstance.getBloodfiendEntityInstance() != null) {
            for (BlockPos pos : safePositions) {
                RenderUtils.renderESP(wrFill, pos, 0, 1, 0, 0.8f, true);
                RenderUtils.renderESP(wrLine, pos, 0, 1, 0, 0.8f, false);
            }
        }
    }

    public static class SafeZoneFinder {
        /**
         * 寻找最佳安全位置（6）
         * @param world 世界对象
         * @param playerPos 玩家位置（9）
         * @param bossPos Boss位置（7）
         * @param searchRadius 搜索半径（默认30）
         * @return 最佳安全位置（6），若未找到返回null
         */
        public static BlockPos findBestSafePosition(Level world, BlockPos playerPos, BlockPos bossPos, int searchRadius) {
            // 1. 获取所有安全区方块（Y轴±1范围）
            Iterable<BlockPos> safeZoneBlocks = BlockPos.betweenClosed(
                    bossPos.offset(-searchRadius, -4, -searchRadius),
                    bossPos.offset(searchRadius, 1, searchRadius)
            );

            // 2. 过滤条件
            List<BlockPos> safePositions = BFTaskManiaHandler.safePositions = StreamSupport.stream(safeZoneBlocks.spliterator(), false)
                    .map(BlockPos::immutable)
                    .filter(pos -> isSafeBlock(world, pos))
                    .filter(pos -> isWalkable(world, pos.above()) && isWalkable(world, pos.above(2)))
                    .filter(SafeZoneFinder::hasClearPath)
                    .filter(pos -> !isDangerousEdge(world, pos, bossPos))
                    .filter(pos -> {
                        double xzDist = Math.sqrt(pos.distToCenterSqr(bossPos.getX() + 0.5, pos.getY(), bossPos.getZ() + 0.5));
                        return xzDist >= 3; // 修改为3.01格最小距离
                    })
                    .collect(Collectors.toList());

            if (safePositions.isEmpty()) return null;

            ArmorStand entity = slayerInstance.getBloodIchorEntity();
            LivingEntity entityBoss = slayerInstance.getBloodfiendEntityInstance();
            if (entity != null) {

                PosLine line = line1 = new PosLine(entity.getX(), entity.getZ(), entityBoss.getX(), entityBoss.getZ());
                PosLine line3 = line2 = line.getPerpendicularLine(entityBoss.getX(), entityBoss.getZ()).expandLength(20);

                BlockPos blockPos = safePositions.stream()
                        .filter(pos -> Math.sqrt(pos.distSqr(playerPos)) < 7.5)
                        .filter(pos -> line.distanceTo(pos.getX() + 0.5, pos.getZ() + 0.5) < 1.5)
                        .filter(pos -> line3.isSameSide(pos.getX() + 0.5, pos.getZ() + 0.5, mc.player.getX(), mc.player.getZ()))
                        .min(Comparator.comparingDouble(pos -> {
                            double distToBoss = Math.sqrt(pos.distSqr(bossPos));
                            double distToPlayer = Math.sqrt(pos.distSqr(playerPos));
                            return distToBoss * 1.2 + distToPlayer; // 权重微调
                        }))
                        .orElse(null);
                if(blockPos != null) {
                    // System.out.println(blockPos);
                    return blockPos;
                }

                blockPos = safePositions.stream()
                        .filter(pos -> Math.sqrt(pos.distSqr(playerPos)) < 7.5)
                        .filter(pos -> line3.distanceTo(pos.getX() + 0.5, pos.getZ() + 0.5) > 3.2)
                        .min(Comparator.comparingDouble(pos -> {
                            double distToBoss = Math.sqrt(pos.distSqr(bossPos));
                            double distToPlayer = Math.sqrt(pos.distSqr(playerPos));
                            return distToBoss * 1.2 + distToPlayer; // 权重微调
                        }))
                        .orElse(null);
                if(blockPos != null) {
                    // System.out.println(blockPos);
                    return blockPos;
                }
            }

            // 3. 优先级排序（Boss距离权重1.2倍）
            return safePositions.stream()
                    .min(Comparator.comparingDouble(pos -> {
                        double distToBoss = Math.sqrt(pos.distSqr(bossPos));
                        double distToPlayer = Math.sqrt(pos.distSqr(playerPos));
                        return distToBoss * 1.2 + distToPlayer; // 权重微调
                    }))
                    .orElse(null);
        }

        private static boolean isDangerousEdge(Level world, BlockPos pos, BlockPos bossPos) {
            // 1. 计算从安全区指向Boss的方向向量（XZ平面）
            double dx = bossPos.getX() - pos.getX();
            double dz = bossPos.getZ() - pos.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);

            // 归一化方向向量（后退方向为反向）
            int backX = pos.getX() - (int)(dx / length + (dx < 0 ? -0.75 : 0.75)); // 后退1格的X
            int backZ = pos.getZ() - (int)(dz / length + (dz < 0 ? -0.75 : 0.75)); // 后退1格的Z

            // 2. 检查后退格是否仍是安全区
            BlockPos backPos = new BlockPos(backX, pos.getY(), backZ);
            return !isSafeBlock(world, backPos) && !isSafeBlock(world, backPos.above()) && !isSafeBlock(world, backPos.below());
        }

        /** 检查是否是深绿色硬化粘土（meta=13） */
        private static boolean isSafeBlock(Level world, BlockPos pos) {
            return world.getBlockState(pos).getBlock() == Blocks.GREEN_TERRACOTTA;
        }

        /** 检查是否可行走（上方是空气或可穿透方块） */
        private static boolean isWalkable(Level world, BlockPos pos) {
            return world.getBlockState(pos).isAir();
        }

        public static boolean isPlayerInSafeZone(Level world, BlockPos playerPos) {
            BlockPos feetPos = playerPos.below();
            return isSafeBlock(world, feetPos)/* || isSafeBlock(world, feetPos.down()) || isSafeBlock(world, feetPos.down().down())*/;
        }

        private static boolean hasClearPath(BlockPos toPos) {

            // EntityLivingBase bossEntity = slayerInstance.getBloodfiendEntityInstance();
            // if(bossEntity != null && new PosLine(mc.thePlayer.posX, mc.thePlayer.posZ, toPos.getX() + 0.5, toPos.getZ() + 0.5).distanceTo(bossEntity.posX, bossEntity.posZ) < 0.7) return false;

            // 简单的直线检测，可以改为更复杂的路径检测
            Vec3 startVec = new Vec3(ToolList.mc.player.getX(), ToolList.mc.player.getY() + ToolList.mc.player.getEyeHeight(), ToolList.mc.player.getZ());
            Vec3 endVec1 = new Vec3(toPos.getX() + 0.5, toPos.getY() + 0.5, toPos.getZ() + 0.5);
            Vec3 endVec2 = new Vec3(toPos.getX() + 0.5, ToolList.mc.player.getY() + ToolList.mc.player.getEyeHeight(), toPos.getZ() + 0.5);

            // 进行光线追踪检测
            HitResult hitResult1 = ToolList.getInstance().predictPlayerAimBlock(startVec, endVec1);
            HitResult hitResult2 = ToolList.getInstance().predictPlayerAimBlock(startVec, endVec2);

            return Optional.ofNullable(hitResult1).map(h -> h.getType() == HitResult.Type.MISS).orElse(true) ||
                    Optional.ofNullable(hitResult2).map(h -> h.getType() == HitResult.Type.MISS).orElse(true) || 
                    (hitResult1 instanceof BlockHitResult blockHitResult && blockHitResult.getBlockPos().equals(toPos));
        }

    }

}
