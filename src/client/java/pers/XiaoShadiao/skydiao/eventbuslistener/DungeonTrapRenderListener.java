package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.List;

public class DungeonTrapRenderListener extends AbstractListener {

    private int tickCount = 0;

    private final List<BlockPos> trapsList = new ArrayList<>();

    @Override
    public String getListenerName() {
        return "DungeonTrapRenderListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
        LevelRenderEvents.END_MAIN.register(this::onRender);
    }

    private void onRender(LevelRenderContext context) {
        if(mc.level == null || mc.player == null) return;

        RenderUtils.WorldRender worldRender = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_LINE);

        float r = 1;
        float g = 0.05f;
        float b = 0.05f;

        for (BlockPos pos : trapsList) {
            BlockState blockState = mc.level.getBlockState(pos);
            if(blockState.getBlock() == Blocks.DISPENSER || blockState.getBlock() == Blocks.DROPPER) {
                Direction direction = blockState.getValue(DropperBlock.FACING);
                int i = 10;
                BlockPos current = pos;
                while (i-- > 0 && mc.level.getBlockState(current = current.relative(direction)).getBlock() == Blocks.AIR) {}
                if (i < 9) {
                    if (pos.getY() != current.getY()) {
                        RenderUtils.renderWorldLine(worldRender, pos, current, r, g, b, 1, r, g, b, 0);
                    } else {
                        Vec3 vec1 = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5 - 0.15, pos.getZ() + 0.5);
                        Vec3 vec2 = new Vec3(current.getX() + 0.5, current.getY() + 0.5 - 0.15, current.getZ() + 0.5);
                        RenderUtils.renderWorldLine(worldRender, vec1, vec2, r, g, b, 1, r, g, b, 0);
                    }
                }
            } else if(blockState.getBlock() == Blocks.TRIPWIRE_HOOK) {
                Direction direction = blockState.getValue(TripWireHookBlock.FACING);
                BlockPos current = pos;
                int i = 10;
                while (i-- > 0 && mc.level.getBlockState(current = current.relative(direction)).getBlock() == Blocks.TRIPWIRE) {}
                if (i < 9) {
                    Vec3 vec1 = new Vec3(pos.getX() + 0.5, pos.getY() - 0.2 + 0.5, pos.getZ() + 0.5);
                    Vec3 vec2 = new Vec3(current.getX() + 0.5, current.getY() - 0.2 + 0.5, current.getZ() + 0.5);
                    RenderUtils.renderWorldLine(worldRender, vec1, vec2, r, g, b, 1, r, g, b, 1);
                }
            }
        }
        worldRender.finishDraw();
    }

    private void onTick(Minecraft mc) {
        // if(true) return;
        if(ConfigManager.dungeonRenderTraps.getValue() && tickCount++ % 10 == 0 && mc.level != null && mc.player != null) {
            trapsList.clear();
            for (BlockPos pos : BlockPos.betweenClosed(
                    (int) mc.player.getX() - 30,
                    (int) mc.player.getY() - 20,
                    (int) mc.player.getZ() - 30,
                    (int) mc.player.getX() + 30,
                    (int) mc.player.getY() + 20,
                    (int) mc.player.getZ() + 30
            )) {
                BlockState blockState = mc.level.getBlockState(pos);
                Block block = blockState.getBlock();

                if(block == Blocks.DISPENSER || block == Blocks.DROPPER || block == Blocks.TRIPWIRE_HOOK) {
                    trapsList.add(pos.immutable());
                }
            }
        }
    }

}
