package pers.XiaoShadiao.skydiao.eventbuslistener.foraging;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TorrhusCanyonHelperListener extends AbstractForagingListener {

    private Thread tikiScannerThread = new Thread();
    private Thread honeyScannerThread = new Thread();

    private final List<BlockPos> tikiPoses = new CopyOnWriteArrayList<>();
    private final List<BlockPos> honeyPoses = new CopyOnWriteArrayList<>();

    @Override
    public String getListenerName() {
        return "TorrhusCanyonHelperListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(!ConfigManager.torrhusCanyonHelper.getValue() || mc.level == null || mc.player == null || !isInTorrhusCanyon()) return;

        RenderUtils.WorldRender wr1 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wr2 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);

        for (BlockPos pos : tikiPoses) {
            if (pos != null) {
                float distance = (float) Math.sqrt(pos.distToCenterSqr(mc.player.position()));
                float alpha = (Mth.clamp(distance, 8, 20) - 8) / 12;
                // RenderUtils.renderTrace(wr1, pos, 1, 0, 0, alpha);
                RenderUtils.renderESP(wr1, pos, 1, 0, 0, alpha, false);
                RenderUtils.renderESP(wr2, pos, 1, 0, 0, alpha, true);
            }
        }
        for (BlockPos pos : honeyPoses) {
            if (pos != null) {
                RenderUtils.renderESP(wr1, pos, 1, 1, 0, 1, false);
            }
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if(entity instanceof Bee bee) {
                if(bee.getScale() > 2) {
                    RenderUtils.renderTrace(wr1, entity, 1, 1, 0, 1);
                    RenderUtils.renderESP(wr1, entity, 1, 1, 0, 1, false);
                    RenderUtils.renderESP(wr2, entity, 1, 1, 0, 1, true);
                }
            } else if(entity instanceof Armadillo) {
                RenderUtils.renderESP(wr1, entity, 1, 0.4f, 0, 1, false);
                RenderUtils.renderESP(wr2, entity, 1, 0.4f, 0, 1, true);
            }
        }

        wr1.finishDraw();
        wr2.finishDraw();
    }

    private void onClientTick(Minecraft mc) {
        if(!ConfigManager.torrhusCanyonHelper.getValue() || mc.player == null || mc.level == null || !isInTorrhusCanyon()) return;

        if(!tikiScannerThread.isAlive()) {
            tikiScannerThread = new Thread(this::scanTiki, "Torrhus Canyon Tiki Scanner");
            tikiScannerThread.start();
        }
        if(!honeyScannerThread.isAlive()) {
            honeyScannerThread = new Thread(this::scanHoney, "Torrhus Canyon Honey Scanner");
            honeyScannerThread.start();
        }

        tikiPoses.removeIf(pos -> !mc.level.getBlockState(pos).is(Blocks.PLAYER_HEAD));
        honeyPoses.removeIf(pos -> !mc.level.getBlockState(pos).is(Blocks.BEE_NEST) || !mc.level.getBlockState(pos).getValueOrElse(BeehiveBlock.HONEY_LEVEL, 0).equals(BeehiveBlock.MAX_HONEY_LEVELS));
    }

    private void scanHoney() {
        while(isInTorrhusCanyon()) {
            scanHoney0();
        }
    }

    private void scanHoney0() {
        try {
            Thread.sleep(10000);
            BlockPos pos = BlockPos.containing(mc.player.position());
            for (int offX = -65; offX < 65; offX++) {
                for (int y = 80; y < 160; y++) {
                    for (int offZ = -65; offZ < 65; offZ++) {
                        BlockPos targetBlockPos = pos.atY(y).offset(offX, 0, offZ);
                        BlockState state = mc.level.getBlockState(targetBlockPos);
                        if(state.is(Blocks.BEE_NEST) &&
                                state.getValueOrElse(BeehiveBlock.HONEY_LEVEL, 0) == BeehiveBlock.MAX_HONEY_LEVELS &&
                                mc.level.getBlockState(targetBlockPos.below()).getBlock() instanceof FenceBlock
                        ) {
                            honeyPoses.add(targetBlockPos);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.catching(e);
        }
    }

    private void scanTiki() {
        while(isInTorrhusCanyon()) {
            scanTiki0();
        }
    }

    private void scanTiki0() {
        try {
            Thread.sleep(10000);
            BlockPos pos = BlockPos.containing(mc.player.position());
            for (int offX = -65; offX < 65; offX++) {
                for (int y = 80; y < 160; y += 3) {
                    for (int offZ = -65; offZ < 65; offZ++) {
                        BlockPos targetBlockPos = pos.atY(y).offset(offX, 0, offZ);
                        if(mc.level.getBlockState(targetBlockPos).is(Blocks.PLAYER_HEAD)) {
                            int tikiPart = 1;
                            for (int offY = 1; offY <= 2; offY++) {
                                if(mc.level.getBlockState(targetBlockPos.offset(0, offY, 0)).is(Blocks.PLAYER_HEAD)) {
                                    tikiPart++;
                                } else {
                                    break;
                                }
                            }
                            for (int offY = -1; offY >= -2; offY--) {
                                if(mc.level.getBlockState(targetBlockPos.offset(0, offY, 0)).is(Blocks.PLAYER_HEAD)) {
                                    tikiPart++;
                                } else {
                                    break;
                                }
                            }
                            if(tikiPart == 3) {
                                BlockPos add = targetBlockPos.immutable();
                                if(!tikiPoses.contains(add)) tikiPoses.add(add);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.catching(e);
        }
    }

}
