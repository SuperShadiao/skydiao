package pers.XiaoShadiao.skydiao.utils.renderutils;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class PathRenderer {

    public static void renderPath(LevelRenderContext context, List<?> positions, float r, float g, float b, boolean xray, boolean drawEnd) {
        if (positions == null || positions.size() < 2) {
            return; // 如果路径点少于2个，直接返回
        }

        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, xray ? CustomRenderPipeline.THROUGH_WALLS_LINE : CustomRenderPipeline.NO_THROUGH_WALLS_LINE);

        Vec3 prev = getListIndex(positions, 0);
        Vec3 current = Vec3.ZERO;
        for (int i = 1; i < positions.size(); i++) {
            current = getListIndex(positions, i);

            // 如果方向发生变化（转弯），添加当前点
            if (i == 1 || isDirectionChange(getListIndex(positions, i - 2), current, getListIndex(positions, i - 1))) {
                RenderUtils.renderWorldLine(wr, prev.add(0.5, 0.5, 0.5), current.add(0.5, 0.5, 0.5), r, g, b, 1.0F);
                prev = current;
            }
        }
        RenderUtils.renderWorldLine(wr, prev.add(0.5, 0.5, 0.5), current.add(0.5, 0.5, 0.5), r, g, b, 1.0F);

        if(drawEnd) {
            RenderUtils.renderESP(wr, current, r,g,b, 1, false);
        }
    }

    private static Vec3 getListIndex(List<?> list, int index) {
        Object o = list.get(index);
        if(o instanceof Vec3) return (Vec3) o;
        if(o instanceof Vec3i) return new Vec3(((Vec3i) o).getX(), ((Vec3i) o).getY(), ((Vec3i) o).getZ());
        throw new RuntimeException(new ClassCastException((o == null ? "null" : o.getClass().getName()) + " -> Vec3"));
    }

    // 判断三点是否发生方向变化
    private static boolean isDirectionChange(Vec3 a, Vec3 b, Vec3 c) {
        double dx1 = b.x - a.x;
        double dy1 = b.y - a.y;
        double dz1 = b.z - a.z;

        double dx2 = c.x - b.x;
        double dy2 = c.y - b.y;
        double dz2 = c.z - b.z;

        // 如果方向向量不一致，说明发生了方向变化
        return dx1 != dx2 || dy1 != dy2 || dz1 != dz2;
    }

    public static void renderPath(LevelRenderContext context, List<?> positions, int r, int g, int b, boolean xray, boolean drawEnd) {
        // 将 int 类型的 RGB 转换为 float 类型
        float fr = r / 255.0F;
        float fg = g / 255.0F;
        float fb = b / 255.0F;

        // 调用 float 类型的 renderPath 方法
       renderPath(context, positions, fr, fg, fb, xray, drawEnd);
    }

    public static void renderPath(LevelRenderContext context, double fromX, double fromY, double fromZ, double toX, double toY, double toZ, int r, int g, int b, boolean xray) {
        float fr = r / 255.0F;
        float fg = g / 255.0F;
        float fb = b / 255.0F;

        renderPath(context, List.of(new Vec3(fromX, fromY, fromZ), new Vec3(toX, toY, toZ)), fr, fg, fb, xray, false);
    }
}