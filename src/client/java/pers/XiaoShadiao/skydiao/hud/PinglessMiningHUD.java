package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.OreTypes;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;
import pers.XiaoShadiao.skydiao.utils.tab.TabReader;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PinglessMiningHUD extends XSDHUD {

    private BlockPos lastAimingPos = BlockPos.ZERO;
    private int breakTotalTick = 0;
    private int totalBreakTickNeeded = 0;

    private int miningSpeed = 1000;
    private int cantReadMiningSpeedTick = 0;

    @Override
    public void runRegister() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("skydiao", "pinglessmining"), this);

        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        RenderUtils.WorldRender wrLine = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wrFill = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_FILL);

        if(breakTotalTick >= totalBreakTickNeeded) {
            RenderUtils.renderESP(wrLine, lastAimingPos, 0, 0, 0, 1, false, true);
            RenderUtils.renderESP(wrFill, lastAimingPos, 0, 0, 0, 2, true, true);
        }

        wrLine.finishDraw();
        wrFill.finishDraw();
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force) {

    }

    @Override
    public void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force, HudOffsetAndScale settings) {
        if(breakTotalTick > 0 || force) {
            Matrix3x2fStack pose = context.pose();
            pose.pushMatrix();

            pose.translate(context.guiWidth() / 2f, context.guiHeight() / 2f);
            if(settings != null) {
                pose.scale(settings.scale());
                pose.translate(settings.x(), settings.y());
            }
            int r = 12;
            int t = 2;
            RenderUtils.drawRoundedCircle(context, 0, 0, 0 - 90, 360 - 90, r, t, 0xFF000000);
            RenderUtils.drawRoundedCircle(context, 0, 0, 0 - 90, (int) Mth.clampedLerp(breakTotalTick / (float) totalBreakTickNeeded, 0, 360) - 90, r, t, 0xFF00FF00);
            context.centeredText(mc.font, AbstractListener.tpsListener.getCurrentTPS() < 16 ? "§clf tps..." : breakTotalTick + "/" + totalBreakTickNeeded + "t", 0, 20, -1);

            pose.popMatrix();
        }
    }

    @Override
    public @Nullable String getHudName() {
        return "pinglessmining";
    }

    private void onStartTick(Minecraft mc) {
        if (!ConfigManager.pinglessMining.getValue()) {
            breakTotalTick = 0;
            lastAimingPos = BlockPos.ZERO;
            cantReadMiningSpeedTick = 0;
            return;
        }

        if (mc.level == null || !Set.of("mining_3", "crystal_hollow", "mineshaft").contains(String.valueOf(StatusManager.get().getMode()))) return;

        if (
                (mc.options.keyAttack.isDown() || InputSimulator.isMouseLeftHolding) &&
                        (mc.hitResult instanceof BlockHitResult blockHitResult && mc.hitResult.getType() == HitResult.Type.BLOCK)
        ) {
            OreTypes oreType = OreTypes.byBlock(mc.level.getBlockState(blockHitResult.getBlockPos()).getBlock());
            if (oreType != null) {
                totalBreakTickNeeded = oreType.getTotalBreakTick(miningSpeed);
                if(breakTotalTick < totalBreakTickNeeded) breakTotalTick++;
            }
            if (!lastAimingPos.equals(blockHitResult.getBlockPos())) {
                if (mc.level != null) {
                    lastAimingPos = blockHitResult.getBlockPos();
                    breakTotalTick = 0;
                }
            }
        } else {
            breakTotalTick = 0;
            lastAimingPos = BlockPos.ZERO;
        }

        TabReader.findLineWith("Mining Speed").ifPresentOrElse(line -> {
            Matcher matcher = Pattern.compile("\\d+").matcher(line);
            if (matcher.find()) {
                miningSpeed = Integer.parseInt(matcher.group());
                cantReadMiningSpeedTick = 0;
            }
        }, () -> {
            cantReadMiningSpeedTick++;
            if(cantReadMiningSpeedTick % (60 * 20) == 0) {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §cPingless Mining无法从Tab读取Mining Speed, 请使用§6/tab§c将Stats分类显示在Tab并显示Mining Speed"));
            }
        });
    }
}
