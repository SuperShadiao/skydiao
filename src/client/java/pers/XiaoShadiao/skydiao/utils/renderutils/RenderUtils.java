package pers.XiaoShadiao.skydiao.utils.renderutils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3f;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.FreecamAndFreelook;
import pers.XiaoShadiao.skydiao.mixin.client.MixinAbstractContainerScreenPosGetter;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.awt.*;
import java.util.List;
import java.util.Objects;

public class RenderUtils {

    public static void renderCircle(WorldRender worldRender, Vec3 center, float radius, int segments){
        checkAccess(_3d, worldRender.pipeline, "renderCircle()");
        worldRender.crpl.renderCircle(worldRender.context, worldRender.pipeline, center, radius, segments);
    }

    public static void renderScrollingString(
            GuiGraphicsExtractor guiGraphics,
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
            guiGraphics.text(font, text, left - (int) offset, textY, color);
            guiGraphics.disableScissor();
        } else {
            int drawX = Mth.clamp(centerX, left + textWidth / 2, right - textWidth / 2);
            guiGraphics.centeredText(font, text, drawX, textY, color);
        }
    }

    public static WorldRender createWorldRenderInstance(LevelRenderContext context, RenderPipeline pipeline) {
        return new WorldRender(context, pipeline);
    }

    // 绘制圆形
    public static void drawCircle(GuiGraphicsExtractor context, float x, float y, int start, int end, float r, int rgb) {
        Matrix3x2fStack pose = context.pose();
        for(int i = start; i <= end; i++) {
            pose.pushMatrix();
            pose.translate(x, y);
            pose.rotate((float) (i * Math.PI / 180));
            // pose.translate(0, r);

            context.fill(0, 0, (int) r, 1, rgb);

            pose.popMatrix();
        }
    }

    // 绘制带厚度的圆形
    public static void drawRoundedCircle(GuiGraphicsExtractor context, float x, float y, int start, int end, float r, int thick, int rgb) {
        Matrix3x2fStack pose = context.pose();
        for(int i = start; i <= end; i++) {
            pose.pushMatrix();
            pose.translate(x, y);
            pose.rotate((float) (i * Math.PI / 180));
            // pose.translate(0, r);

            context.fill((int) r + thick, 0, (int) r, 1, rgb);

            pose.popMatrix();
        }
    }

    // 绘制渐变透明圆形
    public static void drawRoundedCircleFadeToNoAlpha(GuiGraphicsExtractor context, float x, float y, int start, int end, float r, int thick, int rgb, boolean fadeOutward) {
        Matrix3x2fStack pose = context.pose();
        int delta = end - start;
        for(int i = start; i <= end; i++) {
            pose.pushMatrix();
            pose.translate(x, y);
            pose.rotate((float) (i * Math.PI / 180));
            // pose.translate(0, r);
            int alpha = (rgb >> 24) & 255;
            if(fadeOutward) {
                alpha = (int) (alpha * Math.pow(1 - (float) (i - start) / delta, 2));
            } else {
                alpha = (int) (alpha * Math.pow((float) (i - start) / delta, 2));
            }
            context.fill((int) r + thick, 0, (int) r, 1, rgb & (alpha << 24 | 0xFFFFFF));

            pose.popMatrix();
        }
    }

    // 使用纹理绘制圆形
    public static void drawCircleWithTexture(GuiGraphicsExtractor context, Identifier texture, float x, float y, float r) {

        for (int i = 0; i <= 180; i++) {
            float angle = (float) (i * Math.PI / 180);

            float py = Mth.sin(angle) * r;

            float xOffset = -Mth.cos(angle) * r;
            context.enableScissor((int) (x + xOffset), (int) (y - py), (int) (x + xOffset + 1), (int) (y + py));
            context.blit(RenderPipelines.GUI_TEXTURED, texture, (int) (x - r), (int) (y - r), 0, 0, (int) (r * 2), (int) (r * 2), (int) (r * 2), (int) (r * 2));
            context.disableScissor();
        }

    }

    public static void drawOutlineRect(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, int rgb) {
        context.fill(x1, y1 - 1, x2, y1, rgb);
        context.fill(x1, y1, x1 - 1, y2, rgb);
        context.fill(x1, y2 + 1, x2, y2, rgb);
        context.fill(x2, y1, x2 + 1, y2, rgb);
    }

    public static void renderSlot(GuiGraphicsExtractor guiGraphics, AbstractContainerScreen<?> abstractContainerScreen, Slot slot, int rgb) {
        MixinAbstractContainerScreenPosGetter getter = (MixinAbstractContainerScreenPosGetter) abstractContainerScreen;
        guiGraphics.fill(getter.getLeftPos() + slot.x, getter.getTopPos() + slot.y, getter.getLeftPos() + slot.x + 16, getter.getTopPos() + slot.y + 16, rgb);
    }

    // 转发到 BlurredRoundedSurface：绘制圆角矩形并做周围模糊/辉光
    public static void drawBlurredRoundedSurface(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius) {
        BlurredRoundedSurface.draw(graphics, x, y, w, h, radius);
    }

    // 转发到 BlurredRoundedSurface：绘制圆角矩形并做周围模糊/辉光，r/g/b/a（0~1）为玻璃底色与不透明度
    public static void drawBlurredRoundedSurface(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius,
                                                 float r, float g, float b, float a) {
        BlurredRoundedSurface.draw(graphics, x, y, w, h, radius, r, g, b, a);
    }

    // 转发到 BlurredRoundedSurface：自定义辉光，useLiquidGlassblur 控制半透明底色是否预模糊，
    // overwriteBeforeColors 控制是否覆盖此前已绘制的颜色（false 则与之混合），
    // glowRadius 为辉光距离，glowR/g/b/a（0~1）为辉光颜色/强度
    public static void drawWithCustomGlow(GuiGraphicsExtractor graphics, float x, float y, float w, float h,
                                          boolean useLiquidGlassblur, boolean overwriteBeforeColors, float radius,
                                          float glowRadius, float r, float g, float b, float a,
                                          float glowR, float glowG, float glowB, float glowA) {
        BlurredRoundedSurface.drawWithCustomGlow(graphics, x, y, w, h, useLiquidGlassblur, overwriteBeforeColors, radius, glowRadius, r, g, b, a, glowR, glowG, glowB, glowA);
    }

    // 转发到 BlurredRoundedSurface：辉光颜色未指定时跟随内部颜色
    public static void drawWithCustomGlow(GuiGraphicsExtractor graphics, float x, float y, float w, float h,
                                          boolean useLiquidGlassblur, boolean overwriteBeforeColors, float radius,
                                          float glowRadius, float r, float g, float b, float a) {
        BlurredRoundedSurface.drawWithCustomGlow(graphics, x, y, w, h, useLiquidGlassblur, overwriteBeforeColors, radius, glowRadius, r, g, b, a);
    }

    // 转发到 BlurredRoundedSurface：在 GuiRenderer.render 头部合成待渲染的模糊面板
    public static void renderPendingBlurredRoundedSurface(GuiRenderState state) {
        BlurredRoundedSurface.renderPending(state);
    }

    // 转发到 BlurredRoundedSurface：释放 GPU 资源
    public static void closeBlurredRoundedSurface() {
        BlurredRoundedSurface.close();
    }

    public static void renderNameTag(LevelRenderContext context, Vec3 pos, Component name) {
        FreecamAndFreelook.CameraEntity cameraEntity1 = AbstractListener.freecamAndFreelook.getCameraEntity();
        Entity cameraEntity0 = cameraEntity1 == null ? Objects.requireNonNull(mc().getCameraEntity(), "Camera Entity") : cameraEntity1;
        LivingEntity cameraEntity = Objects.requireNonNull(cameraEntity0.asLivingEntity() == null ? ToolList.mc.player : cameraEntity0.asLivingEntity());
        context.submitNodeCollector().submitNameTag(context.poseStack(), pos.subtract(cameraEntity.getEyePosition(ToolList.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true))).subtract(0, 0.35, 0), 0, name, true, 15, context.levelState().cameraRenderState);
    }

    public static void renderClientLogoOnEntity(LevelRenderContext context, AbstractClientPlayer player) {
        PoseStack poseStack = context.poseStack();

        double distanceToCameraSq = player.distanceToSqr(
                context.levelState().cameraRenderState.pos.x,
                context.levelState().cameraRenderState.pos.y,
                context.levelState().cameraRenderState.pos.z);

        // 与原版 NameTag 保持一致的显示前置条件，不满足时直接不渲染
        if (!shouldShowClientLogo(player, distanceToCameraSq)) {
            return;
        }

        // 与原生 NameTag 使用同一锚点：实体 NAME_TAG 附着点（头顶）再向上 0.5 格；
        // 位置同样按插值后的渲染坐标计算，避免移动时与 NameTag 错位
        float partialTick = mc().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 attachment = player.getAttachments().get(EntityAttachment.NAME_TAG, 0, player.getYRot(partialTick));
        double anchorX = Mth.lerp(partialTick, player.xo, player.getX()) - context.levelState().cameraRenderState.pos.x + attachment.x;
        double anchorY = Mth.lerp(partialTick, player.yo, player.getY()) - context.levelState().cameraRenderState.pos.y + attachment.y + 0.5;
        double anchorZ = Mth.lerp(partialTick, player.zo, player.getZ()) - context.levelState().cameraRenderState.pos.z + attachment.z;

        // 复刻 EntityRenderer.submitNameDisplay：存在计分板 belowName 文本时，先渲染计分行，
        // 再把 NameTag 行整体上移一行高，因此图标需要跟随 NameTag 一起上移
        if (distanceToCameraSq < 100.0 && player.belowNameDisplay() != null) {
            anchorY += 9.0 * 1.15 * 0.025;
        }

        // 复刻 AvatarRenderer.getRenderOffset：潜行时整个实体（NameTag 锚点包含在内）向下偏移 2/16 格
        if (player.isCrouching()) {
            anchorY -= player.getScale() * 2.0 / 16.0;
        }

        // 复刻原生 NameTag 的文字坐标系（见 NameTagFeatureRenderer）：
        // 平移锚点 -> 相机朝向 billboard -> 缩放 0.025（含 Y 翻转，用于把“文字向下”的 Y 轴映射到世界坐标）
        // 该坐标系中：文字水平居中占 x ∈ [-width/2, width/2]，一行文字纵向占 y ∈ [0, 9]，单位为“文字像素”
        float nameWidth = mc().font.width(player.getDisplayName());
        float logoSize = 9.0f;   // 与 NameTag 行高等高；调大/调小此值即可改变 logo 尺寸
        float gap = 2.0f;

        float logoRight = -nameWidth / 2.0f - gap;   // 紧贴文字左边界
        float logoLeft = logoRight - logoSize;
        float centerY = 4.5f;                        // 文字行的竖直中心
        float logoTop = centerY - logoSize / 2.0f;
        float logoBottom = centerY + logoSize / 2.0f;

        poseStack.pushPose();
        poseStack.translate(anchorX, anchorY, anchorZ);
        // 与 NameTag 使用相同朝向，保证 logo 与文字处于同一平面
        poseStack.rotate(context.levelState().cameraRenderState.orientation);
        poseStack.scale(0.025f, -0.025f, 0.025f);

        // 复刻原生 NameTag 的遮挡规则（EntityRenderer.submitNameDisplay 传入 !state.isDiscrete）：
        // 未潜行时叠加两份——忽略深度的半透明副本 + 受深度测试的不透明副本；
        // 潜行时只画一份受深度测试的半透明副本。
        // 结果：被方块遮挡 -> 半透明；潜行 -> 半透明；同时满足 -> 完全不可见。
        Identifier texture = Identifier.fromNamespaceAndPath("skydiao", "xsdv2.png");
        boolean seeThrough = !player.isDiscrete();
        if (seeThrough) {
            submitClientLogo(context, poseStack, RenderTypes.textSeeThrough(texture), 0x80FFFFFF, logoLeft, logoRight, logoTop, logoBottom);
        }
        submitClientLogo(context, poseStack, RenderTypes.text(texture), seeThrough ? 0xFFFFFFFF : 0x80FFFFFF, logoLeft, logoRight, logoTop, logoBottom);

        poseStack.popPose();
    }

    /**
     * 复刻原版 NameTag 的显示前置条件：
     * EntityRenderer.extractRenderState（显示距离）与 LivingEntityRenderer / AvatarRenderer#shouldShowName
     * （隐藏 GUI、第一人称本体、骑乘、隐身、队伍 NameTag 可见性）。
     */
    private static boolean shouldShowClientLogo(AbstractClientPlayer player, double distanceToCameraSq) {
        Minecraft minecraft = mc();
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null) {
            return false;
        }

        // 原版：显示距离上限 64 格；相机实体（第一人称下的自己）不显示；骑乘中不显示；F1 隐藏 GUI 后不显示
        if (distanceToCameraSq >= 4096.0
                || player == minecraft.getCameraEntity()
                || player.isVehicle()
                || minecraft.gui.hud.isHidden()) {
            return false;
        }

        // 原版：潜行（discrete）时显示距离缩短为 32 格
        if (player.isDiscrete() && distanceToCameraSq >= 1024.0) {
            return false;
        }

        // 原版：隐身不渲染 NameTag；同队伍且开启“可看见友好隐身”时例外
        boolean isVisibleToPlayer = !player.isInvisibleTo(localPlayer);
        if (player == localPlayer) {
            return isVisibleToPlayer;
        }

        // 原版：由服务器下发的计分板队伍决定 NameTag 可见性
        PlayerTeam team = player.getTeam();
        if (team == null) {
            return isVisibleToPlayer;
        }
        PlayerTeam myTeam = localPlayer.getTeam();
        return switch (team.getNameTagVisibility()) {
            case ALWAYS -> isVisibleToPlayer;
            case NEVER -> false;
            case HIDE_FOR_OTHER_TEAMS ->
                    myTeam == null ? isVisibleToPlayer : team.isAlliedTo(myTeam) && (team.canSeeFriendlyInvisibles() || isVisibleToPlayer);
            case HIDE_FOR_OWN_TEAM ->
                    myTeam == null ? isVisibleToPlayer : !team.isAlliedTo(myTeam) && isVisibleToPlayer;
        };
    }

    private static void submitClientLogo(
            LevelRenderContext context,
            PoseStack poseStack,
            RenderType renderType,
            int argb,
            float left,
            float right,
            float top,
            float bottom
    ) {
        context.submitNodeCollector().submitCustomGeometry(
                poseStack,
                renderType,
                (pose, buffer) -> {
                    // 顶点顺序与 UV 完全照搬 Font 的字形四边形，保证正面朝向相机、不被背面剔除
                    buffer.addVertex(pose, left, top, 0).setColor(argb).setUv(0.0f, 0.0f).setLight(LightCoordsUtil.FULL_BRIGHT);
                    buffer.addVertex(pose, left, bottom, 0).setColor(argb).setUv(0.0f, 1.0f).setLight(LightCoordsUtil.FULL_BRIGHT);
                    buffer.addVertex(pose, right, bottom, 0).setColor(argb).setUv(1.0f, 1.0f).setLight(LightCoordsUtil.FULL_BRIGHT);
                    buffer.addVertex(pose, right, top, 0).setColor(argb).setUv(1.0f, 0.0f).setLight(LightCoordsUtil.FULL_BRIGHT);
                }
        );
    }

    public enum SideDirection {
        N(180), S(0), W(270),  E(90);
        private final int radius;
        SideDirection(int radius) {
            this.radius = radius;
        }
        public int getRadius() {
            return radius;
        }
    }
    public enum TopMode {
        TOP(-90), BOTTOM(90), SIDE(0);
        private final float angle;
        TopMode(float angle) {
            this.angle = angle;
        }
        public float getAngle() {
            return angle;
        }
    }

    public static void renderTextureOnBlockSide(LevelRenderContext context, Vec3 pos, Identifier texture, SideDirection direction, TopMode topMode) {
        PoseStack poseStack = context.poseStack();

        poseStack.pushPose();
        poseStack.translate(-context.levelState().cameraRenderState.pos.x + pos.x() + 0.5, -context.levelState().cameraRenderState.pos.y + pos.y() + 0.5, -context.levelState().cameraRenderState.pos.z + pos.z() + 0.5);

        poseStack.rotateDegrees(Axis.YP, direction.radius);
        poseStack.rotateDegrees(Axis.XP, topMode.angle);

        poseStack.translate(-0.5, -0.5, -0.5);
        poseStack.translate(1, 1, 0);
        poseStack.rotateDegrees(Axis.ZP, 180.0f);

        context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.text(texture), ((pose, buffer) -> {
            float z = 1f - 1 / 512f;
            buffer.addVertex(pose, 0.0f, 1, z).setColor(Color.WHITE.getRGB()).setUv(0.0f, 1.0f).setLight(LightCoordsUtil.FULL_BRIGHT);
            buffer.addVertex(pose, 1, 1, z).setColor(Color.WHITE.getRGB()).setUv(1.0f, 1.0f).setLight(LightCoordsUtil.FULL_BRIGHT);
            buffer.addVertex(pose, 1, 0.0f, z).setColor(Color.WHITE.getRGB()).setUv(1.0f, 0.0f).setLight(LightCoordsUtil.FULL_BRIGHT);
            buffer.addVertex(pose, 0.0f, 0.0f, z).setColor(Color.WHITE.getRGB()).setUv(0.0f, 0.0f).setLight(LightCoordsUtil.FULL_BRIGHT);
        }));

        poseStack.popPose();
    }

    public static class WorldRender {

        private final LevelRenderContext context;
        private final RenderPipeline pipeline;
        private final CustomRenderPipeline crpl;

        public WorldRender(LevelRenderContext context, RenderPipeline pipeline) {
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

    public static void renderESP(WorldRender worldRender, BlockPos pos, float r, float g, float b, float a, boolean fillBox, boolean followBlockOutline) {
        BlockState blockState = ToolList.mc.level == null ? Blocks.AIR.defaultBlockState() : ToolList.mc.level.getBlockState(pos);
        if(blockState.isAir() || !followBlockOutline) {
            renderESP(worldRender, pos.getX() - 0.01, pos.getY() - 0.01, pos.getZ() - 0.01 , pos.getX() + 1.01, pos.getY() + 1.01, pos.getZ() + 1.01, r, g, b, a, fillBox);
        } else {
            blockState.getShape(ToolList.mc.level, pos).forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
                renderESP(worldRender, pos.getX() + x1 - 0.01, pos.getY() + y1 - 0.01, pos.getZ() + z1 - 0.01, pos.getX() + x2 + 0.01, pos.getY() + y2 + 0.01, pos.getZ() + z2 + 0.01, r, g, b, a, fillBox);
            });
        }
    }

    public static void renderESP(WorldRender worldRender, BlockPos pos, float r, float g, float b, float a, boolean fillBox) {
        renderESP(worldRender, pos.getX() - 0.01, pos.getY() - 0.01, pos.getZ() - 0.01 , pos.getX() + 1.01, pos.getY() + 1.01, pos.getZ() + 1.01, r, g, b, a, fillBox);
    }

    public static void renderESP(WorldRender worldRender, Vec3 pos, float r, float g, float b, float a, boolean fillBox) {
        renderESP(worldRender, pos.x - 0.51, pos.y - 0.01, pos.z - 0.51, pos.x + 0.51, pos.y + 1.01, pos.z + 0.51, r, g, b, a, fillBox);
    }

    public static void renderESP(WorldRender worldRender, Vec3 pos, float r, float g, float b, float a, boolean fillBox, double size) {
        size /= 2;
        renderESP(worldRender, pos.x - size, pos.y - size, pos.z - size, pos.x + size, pos.y + size, pos.z + size, r, g, b, a, fillBox);
    }

    public static void renderESP(WorldRender worldRender, Entity entity, float r, float g, float b, float a, boolean fillBox) {
        if(entity instanceof EnderDragon ed) {
            for (EnderDragonPart subEntity : ed.getSubEntities()) {
                renderESP(worldRender, subEntity, r, g, b, a, fillBox);
            }
            return;
        }

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

    public static void renderWorldLine(WorldRender worldRender, double x1, double y1, double z1, double x2, double y2, double z2, float r1, float g1, float b1, float a1, float r2, float g2, float b2, float a2) {
        checkAccess(_3d_line, worldRender.pipeline, "renderWorldLine()");

        worldRender.crpl.renderWorldLine(worldRender.context, worldRender.pipeline, (float) x1, (float) y1, (float) z1, (float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1), r1, g1, b1, a1, r2, g2, b2, a2);
    }

    public static void renderWorldLine(WorldRender worldRender, double x1, double y1, double z1, double x2, double y2, double z2, float r1, float g1, float b1, float a1) {
        renderWorldLine(worldRender, x1, y1, z1, x2, y2, z2, r1, g1, b1, a1, r1, g1, b1, a1);
    }

    public static void renderWorldLine(WorldRender worldRender, BlockPos pos1, BlockPos pos2, float r1, float g1, float b1, float a1, float r2, float g2, float b2, float a2) {
        renderWorldLine(worldRender, pos1.getX() + 0.5, pos1.getY() + 0.5, pos1.getZ() + 0.5, pos2.getX() + 0.5, pos2.getY() + 0.5, pos2.getZ() + 0.5, r1, g1, b1, a1, r2, g2, b2, a2);
    }

    public static void renderWorldLine(WorldRender worldRender, BlockPos pos1, BlockPos pos2, float r1, float g1, float b1, float a1) {
        renderWorldLine(worldRender, pos1, pos2, r1, g1, b1, a1, r1, g1, b1, a1);
    }

    public static void renderWorldLine(WorldRender worldRender, Vec3 pos1, Vec3 pos2, float r1, float g1, float b1, float a1, float r2, float g2, float b2, float a2) {
        renderWorldLine(worldRender, pos1.x(), pos1.y(), pos1.z(), pos2.x(), pos2.y(), pos2.z(), r1, g1, b1, a1, r2, g2, b2, a2);
    }

    public static void renderWorldLine(WorldRender worldRender, Vec3 pos1, Vec3 pos2, float r1, float g1, float b1, float a1) {
        renderWorldLine(worldRender, pos1, pos2, r1, g1, b1, a1, r1, g1, b1, a1);
    }

    static void renderVector(PoseStack poseStack, VertexConsumer vertexConsumer, Vector3f vector3f, Vec3 vec3, int color1, int color2) {
        PoseStack.Pose pose = poseStack.last();
        int lineWidth = 3;
        vertexConsumer.addVertex(pose, vector3f)
                .setColor(color1)
                .setNormal(pose, (float)vec3.x * 10f, (float)vec3.y * 10f, (float)vec3.z * 10f).setLineWidth(lineWidth);
        vertexConsumer.addVertex(pose, (float)(vector3f.x() + vec3.x), (float)(vector3f.y() + vec3.y), (float)(vector3f.z() + vec3.z))
                .setColor(color2)
                .setNormal(pose, (float)vec3.x * 10f, (float)vec3.y * 10f, (float)vec3.z * 10f).setLineWidth(lineWidth);
    }

    public static void renderTrace(WorldRender worldRender, double x, double y, double z, float r, float g, float b, float a) {
        checkAccess(_3d_line, worldRender.pipeline, "renderTrace()");

        FreecamAndFreelook.CameraEntity cameraEntity1 = AbstractListener.freecamAndFreelook.getCameraEntity();
        Entity cameraEntity = cameraEntity1 == null ? Objects.requireNonNull(mc().getCameraEntity(), "Camera Entity") : cameraEntity1;
        Vec3 vec3 = cameraEntity.getPosition(ToolList.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)).add(cameraEntity.getForward().add(0, cameraEntity.getEyeHeight(), 0));

        worldRender.crpl.renderTrace(worldRender.context, worldRender.pipeline, (float) (vec3.x), (float) (vec3.y), (float) (vec3.z), (float) (x - vec3.x), (float) (y - vec3.y), (float) (z - vec3.z), r, g, b, a);
    }

    public static void renderTrace(WorldRender worldRender, BlockPos pos, float r, float g, float b, float a) {
        renderTrace(worldRender, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, r, g, b, a);
    }

    public static void renderTrace(WorldRender worldRender, Vec3 pos, float r, float g, float b, float a) {
        renderTrace(worldRender, pos.x(), pos.y() + 0.5, pos.z(), r, g, b, a);
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
