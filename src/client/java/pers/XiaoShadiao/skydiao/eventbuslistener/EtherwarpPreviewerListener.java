package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.pathfinderv2.BlockHelper;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;
import java.util.Set;

public class EtherwarpPreviewerListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "EtherwarpPreviewerListener";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(mc.player == null) return;

        if(!mc.player.isCrouching() || !isHoldAOTV(mc.player.getMainHandItem())) return;

        RenderUtils.WorldRender wrLine = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_LINE);

        Vec3 eyePos = mc.player.getEyePosition(context.levelState().worldPartialTicks);
        Vec3 predictPos = eyePos.add(mc.player.getForward().multiply(50, 50, 50));

        HitResult hitResult = ToolList.getInstance().predictPlayerAimBlock(eyePos, predictPos);
        if(hitResult instanceof BlockHitResult blockHitResult && blockHitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos target = blockHitResult.getBlockPos();
            Color previewColor;// = new Color(ConfigManager.etherwarpCanWarpPreviewColor.getValue());
            if(!BlockHelper.hasCollision(target.above()) && !BlockHelper.hasCollision(target.above(2))) {
                previewColor = new Color(ConfigManager.etherwarpCanWarpPreviewColor.getValue());
            } else {
                previewColor = new Color(ConfigManager.etherwarpCannotWarpPreviewColor.getValue());
            }
            RenderUtils.renderESP(wrLine, target, previewColor.getRed() / 255f, previewColor.getGreen() / 255f, previewColor.getBlue() / 255f, 1, false, true);
        }

        wrLine.finishDraw();
    }

    private boolean isHoldAOTV(ItemStack itemStack) {
        return Set.of("ASPECT_OF_THE_END", "ASPECT_OF_THE_VOID").contains(ToolList.getInstance().tryGetSkyblockItemId(itemStack));
    }

}
