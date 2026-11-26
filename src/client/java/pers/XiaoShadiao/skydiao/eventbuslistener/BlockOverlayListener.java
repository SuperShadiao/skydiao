package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;

public class BlockOverlayListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "BlockOverlayListener";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(mc.player == null || mc.level == null || !ConfigManager.blockOverlay.getValue()) return;

        RenderUtils.WorldRender wrLine = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wrFill = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_FILL);

        if(!(mc.hitResult instanceof BlockHitResult blockHitResult) || blockHitResult.getType() != HitResult.Type.BLOCK) return;

        if(mc.gameMode != null && !ConfigManager.blockOverlayAllowInAdvantureMode.getValue() && mc.gameMode.getPlayerMode().isBlockPlacingRestricted()) return;

        BlockPos pos = blockHitResult.getBlockPos();
        boolean rainbowMode = ConfigManager.blockOverlayRainbowMode.getValue();
        float alpha = (float) (double) ConfigManager.blockOverlayAlpha.getValue();
        Color color;
        if(!rainbowMode) {
            color = new Color(ConfigManager.blockOverlayColor.getValue());
        } else {
            double speed = ConfigManager.blockOverlayRainbowModeSpeed.getValue();
            float hue = (float) ((System.currentTimeMillis() / 6000.0 * speed) % 1.0);
            color = new Color(Color.HSBtoRGB(hue, 1f, 1f));
        }
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        RenderUtils.renderESP(wrLine, pos, r, g, b, 1, false, true);
        RenderUtils.renderESP(wrFill, pos, r, g, b, alpha * 2, true, true);

        wrLine.finishDraw();
        wrFill.finishDraw();
    }

}
