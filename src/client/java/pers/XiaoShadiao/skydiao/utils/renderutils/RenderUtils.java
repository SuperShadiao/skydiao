package pers.XiaoShadiao.skydiao.utils.renderutils;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;

public class RenderUtils {

    public static void renderScrollingString(
            GuiGraphics guiGraphics,
            Font font,
            Component text,
            int centerX,      // 基准 X，用于居中绘制时的中心点
            int left,         // 绘制区域左边界
            int top,          // 绘制区域上边界
            int right,        // 绘制区域右边界
            int bottom,       // 绘制区域下边界
            int color         // 文字颜色 (ARGB)
    ) {
        int textWidth = font.width(text);
        int textY = (top + bottom - 9) / 2 + 1; // 在上下边界之间居中
        int availableWidth = right - left;

        if (textWidth > availableWidth) {
            int overflow = textWidth - availableWidth;
            double time = Util.getMillis() / 500.0;
            double period = Math.max(overflow * 0.5, 3.0);
            double oscillation = Math.sin((Math.PI / 2) * Math.cos((Math.PI * 2) * time / period)) / 2.0 + 0.5;
            double offset = Mth.lerp(oscillation, 0.0, overflow);

            guiGraphics.enableScissor(left, top, right, bottom);
            guiGraphics.drawString(font, text, left - (int) offset, textY, color);
            guiGraphics.disableScissor();
        } else {
            int drawX = Mth.clamp(centerX, left + textWidth / 2, right - textWidth / 2);
            guiGraphics.drawCenteredString(font, text, drawX, textY, color);
        }
    }

    public static WorldRender createWorldRenderInstance(WorldRenderContext context, RenderPipeline pipeline) {
        return new WorldRender(context, pipeline);
    }

    public static class WorldRender {

        private final WorldRenderContext context;
        private final RenderPipeline pipeline;
        private final CustomRenderPipeline crpl;

        public WorldRender(WorldRenderContext context, RenderPipeline pipeline) {
            this.context = context;
            this.pipeline = pipeline;
            this.crpl = CustomRenderPipeline.getInstance(pipeline);
        }

        public void finishDraw() {
            crpl.draw(ToolList.mc, pipeline);
        }

    }

    public static final List<RenderPipeline> _3d = List.of(
            CustomRenderPipeline.NO_THROUGH_WALLS_FILL,
            CustomRenderPipeline.THROUGH_WALLS_FILL
    );
    public static final List<RenderPipeline> _3d_line = List.of(
            CustomRenderPipeline.NO_THROUGH_WALLS_LINE,
            CustomRenderPipeline.THROUGH_WALLS_LINE
    );
    public static void checkAccess(List<RenderPipeline> list, RenderPipeline pipeline, String name) {
        if (!list.contains(pipeline)) throw new UnsupportedOperationException(name + "不支持使用" + pipeline.getLocation() + "作为Pipeline");
    }

    public static void renderESP(WorldRender worldRender, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a, boolean fillBox) {
        checkAccess(fillBox ? _3d : _3d_line, worldRender.pipeline, "renderESP()");
        worldRender.crpl.renderESP(worldRender.context, worldRender.pipeline, (float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2, r, g, b, a, fillBox);
    }

    public static void renderESP(WorldRender worldRender, BlockPos pos, float r, float g, float b, float a, boolean fillBox) {
        renderESP(worldRender, pos.getX() - 0.5, pos.getY(), pos.getZ() - 0.5, pos.getX() + 1, pos.getY() + 0.5, pos.getZ() + 0.5, r, g, b, a, fillBox);
    }

    public static void renderESP(WorldRender worldRender, Vec3 pos, float r, float g, float b, float a, boolean fillBox) {
        renderESP(worldRender, pos.x - 0.5, pos.y, pos.z - 0.5, pos.x + 0.5, pos.y + 1, pos.z + 0.5, r, g, b, a, fillBox);
    }

    public static void renderESP(WorldRender worldRender, Entity entity, float r, float g, float b, float a, boolean fillBox) {
        float partialTicks = ToolList.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double x = Mth.lerp(partialTicks, entity.xo, entity.getX());
        double y = Mth.lerp(partialTicks, entity.yo, entity.getY());
        double z = Mth.lerp(partialTicks, entity.zo, entity.getZ());

        // 获取实体的边界框大小
        AABB originalBB = entity.getBoundingBox();
        double width = originalBB.getXsize();
        double height = originalBB.getYsize();

        // 创建插值后的边界框
        double x1 = x - width / 2.0;
        double y1 = y;
        double z1 = z - width / 2.0;
        double x2 = x + width / 2.0;
        double y2 = y + height;
        double z2 = z + width / 2.0;

        renderESP(worldRender, x1, y1, z1, x2, y2, z2, r, g, b, a, fillBox);
    }

    public static void renderTrace(WorldRender worldRender, double x, double y, double z, float r, float g, float b, float a) {
        checkAccess(_3d_line, worldRender.pipeline, "renderTrace()");

        Vec3 vec3 = mc().player.getPosition(ToolList.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)).add(mc().player.getForward().add(0, mc().getCameraEntity().getEyeHeight(), 0));

        // Vec3 forward = mc().getCameraEntity().getForward().add(0, mc().getCameraEntity().getEyeHeight(), 0);
        worldRender.crpl.renderTrace(worldRender.context, worldRender.pipeline, (float) (vec3.x), (float) (vec3.y), (float) (vec3.z), (float) (x - vec3.x), (float) (y - vec3.y), (float) (z - vec3.z), r, g, b, a);
    }

    public static void renderTrace(WorldRender worldRender, BlockPos pos, float r, float g, float b, float a) {
        renderTrace(worldRender, pos.getX() + 1, pos.getY() + 0.5, pos.getZ() + 0.5, r, g, b, a);
    }

    public static void renderTrace(WorldRender worldRender, Vec3 pos, float r, float g, float b, float a) {
        renderTrace(worldRender, pos.x + 0.5, pos.y, pos.z + 0.5, r, g, b, a);
    }

    public static void renderTrace(WorldRender worldRender, Entity entity, float r, float g, float b, float a) {
        float partialTicks = ToolList.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double x = Mth.lerp(partialTicks, entity.xo, entity.getX());
        double y = Mth.lerp(partialTicks, entity.yo, entity.getY());
        double z = Mth.lerp(partialTicks, entity.zo, entity.getZ());

        renderTrace(worldRender, x, y, z, r, g, b, a);
    }

    public static Minecraft mc() {
        return ToolList.mc;
    }


}
