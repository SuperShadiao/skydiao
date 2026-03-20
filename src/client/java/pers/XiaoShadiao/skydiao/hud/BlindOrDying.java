package pers.XiaoShadiao.skydiao.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.ColoredRectangleRenderState;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

import java.util.Objects;

public class BlindOrDying extends XSDHUD {

    private float effectAnimation = 0;

    @Override
    public void runRegister() {
        HudElementRegistry.addLast(Objects.requireNonNull(ResourceLocation.tryBuild("skydiao", "blind_or_dying_effect")), this);
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        effectAnimation += tickCounter.getGameTimeDeltaTicks() / 7.5f;
        if(mc.player == null) return;
        if(ConfigManager.dyingtip.getValue() && mc.player.getHealth() / mc.player.getMaxHealth() < 0.25) {
            draw(context, 0xFFFF0000);
        } else if(ConfigManager.noblind.getValue() && mc.player.hasEffect(MobEffects.BLINDNESS)) {
            draw(context, 0xFF000000);
        }
    }

    private void draw(GuiGraphics context, int rgb) {
        rgb &= (0x00FFFFFF | (Mth.lerpInt(Mth.sin(effectAnimation), 45, 60) << 24));
        // context.fillGradient(0, 0, context.guiWidth(), context.guiHeight() / 4, rgb, 0);
        // context.fillGradient(0, context.guiHeight(),context.guiWidth() / 4, 0, rgb, 0);
        // context.fillGradient(context.guiWidth(), context.guiHeight(), 0, context.guiHeight() * 3 / 4,rgb, 0);
        // context.fillGradient(context.guiWidth(), 0, context.guiWidth() * 3 / 4, context.guiHeight() / 4, rgb, 0);
        Matrix3x2fStack pose = context.pose();
        context.guiRenderState.submitGuiElement(new ColoredRectangleRenderState2(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), 0, 0,context.guiWidth() / 4, context.guiHeight(), rgb, 0, context.scissorStack.peek()));
        context.guiRenderState.submitGuiElement(new ColoredRectangleRenderState2(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), context.guiWidth(), context.guiHeight(), context.guiWidth() * 3 / 4, 0, rgb, 0, context.scissorStack.peek()));
        context.guiRenderState.submitGuiElement(new ColoredRectangleRenderState (RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), context.guiWidth(), context.guiHeight(), 0, context.guiHeight() * 3 / 4, rgb, 0, context.scissorStack.peek()));
        context.guiRenderState.submitGuiElement(new ColoredRectangleRenderState (RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), 0, 0, context.guiWidth(), context.guiHeight() / 4, rgb, 0, context.scissorStack.peek()));
    }

    public record ColoredRectangleRenderState2(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2f pose,
            int x0,
            int y0,
            int x1,
            int y1,
            int col1,
            int col2,
            @Nullable ScreenRectangle scissorArea,
            @Nullable ScreenRectangle bounds
    ) implements GuiElementRenderState {
        public ColoredRectangleRenderState2(
                RenderPipeline renderPipeline,
                TextureSetup textureSetup,
                Matrix3x2f matrix3x2f,
                int i,
                int j,
                int k,
                int l,
                int m,
                int n,
                @Nullable ScreenRectangle screenRectangle
        ) {
            this(renderPipeline, textureSetup, matrix3x2f, i, j, k, l, m, n, screenRectangle, getBounds(i, j, k, l, matrix3x2f, screenRectangle));
        }

        @Override
        public void buildVertices(VertexConsumer vertexConsumer) {
            vertexConsumer.addVertexWith2DPose(this.pose(), this.x0(), this.y0()).setColor(this.col1());
            vertexConsumer.addVertexWith2DPose(this.pose(), this.x0(), this.y1()).setColor(this.col1());
            vertexConsumer.addVertexWith2DPose(this.pose(), this.x1(), this.y1()).setColor(this.col2());
            vertexConsumer.addVertexWith2DPose(this.pose(), this.x1(), this.y0()).setColor(this.col2());
        }

        @Nullable
        private static ScreenRectangle getBounds(int i, int j, int k, int l, Matrix3x2f matrix3x2f, @Nullable ScreenRectangle screenRectangle) {
            ScreenRectangle screenRectangle2 = new ScreenRectangle(i, j, k - i, l - j).transformMaxBounds(matrix3x2f);
            return screenRectangle != null ? screenRectangle.intersection(screenRectangle2) : screenRectangle2;
        }
    }
}
