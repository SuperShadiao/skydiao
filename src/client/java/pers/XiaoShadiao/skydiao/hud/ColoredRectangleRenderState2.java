package pers.XiaoShadiao.skydiao.hud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

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