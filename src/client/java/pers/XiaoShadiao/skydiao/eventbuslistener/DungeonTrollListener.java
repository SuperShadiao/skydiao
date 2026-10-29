package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.renderutils.Gif;

import java.awt.*;

public class DungeonTrollListener extends AbstractListener {

    private static final Identifier gifId = Identifier.fromNamespaceAndPath("skydiao", "textures/hiddensomething/sus.gif");

    private static final int gifWidth = 468;
    private static final int gifHeight = 60;

    private float animationOffset;

    @Override
    public String getListenerName() {
        return "DungeonTrollListener";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::onCollectSubmits);

    }

    private void onCollectSubmits(LevelRenderContext context) {
        if(!"dungeon_hub".equals(StatusManager.get().getMode())) return;

        animationOffset += mc.getDeltaTracker().getGameTimeDeltaTicks() / 13;

        PoseStack poseStack = context.poseStack();

        Vec3 pos = new Vec3(-56, 137, 6);

        poseStack.pushPose();
        poseStack.translate(-context.levelState().cameraRenderState.pos.x + pos.x() + 0.5, -context.levelState().cameraRenderState.pos.y + pos.y() + 0.5, -context.levelState().cameraRenderState.pos.z + pos.z() + 0.5);

        poseStack.rotateDegrees(Axis.YP, -90);
        poseStack.rotateDegrees(Axis.XP, 0);

        poseStack.translate(-0.5, -0.5, -0.5);
        poseStack.translate(1, 1, 0);
        poseStack.rotateDegrees(Axis.ZP, 180.0f);

        float renderWidth = (float) gifWidth / gifHeight;

        animationOffset %= renderWidth;

        for (int i = 0; i < 3; i++) {
            int offset = i;
            context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.text(Gif.create(gifId).updateAndGetFrame().resourceId()), ((pose, buffer) -> {
                float z = 1f - 1 / 512f;

                buffer.addVertex(pose, renderWidth * offset - animationOffset, 1, z).setColor(Color.WHITE.getRGB()).setUv(0.0f, 1.0f).setLight(LightCoordsUtil.FULL_BRIGHT);
                buffer.addVertex(pose, renderWidth * (offset + 1) - animationOffset, 1, z).setColor(Color.WHITE.getRGB()).setUv(1.0f, 1.0f).setLight(LightCoordsUtil.FULL_BRIGHT);
                buffer.addVertex(pose, renderWidth * (offset + 1) - animationOffset, 0.0f, z).setColor(Color.WHITE.getRGB()).setUv(1.0f, 0.0f).setLight(LightCoordsUtil.FULL_BRIGHT);
                buffer.addVertex(pose, renderWidth * offset - animationOffset, 0.0f, z).setColor(Color.WHITE.getRGB()).setUv(0.0f, 0.0f).setLight(LightCoordsUtil.FULL_BRIGHT);
            }));
        }

        poseStack.popPose();
    }

}
