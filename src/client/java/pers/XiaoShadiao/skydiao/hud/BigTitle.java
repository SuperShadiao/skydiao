package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix3x2fStack;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.Objects;

public class BigTitle extends XSDHUD {

    private String title;
    private long displayTime;

    @Override
    public void runRegister() {
        HudElementRegistry.addLast(Objects.requireNonNull(Identifier.tryBuild("skydiao", "big_title")), this);
        ScreenEvents.AFTER_INIT.register(this::afterScreenInit);
    }

    private void afterScreenInit(Minecraft mc, Screen screen, int scaledWidth, int scaledHeight) {
        ScreenEvents.afterRender(screen).register(this::render);
    }

    private void render(Screen screen, GuiGraphics guiGraphics, int i, int i1, float v) {
        render(guiGraphics, mc.getDeltaTracker());
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if(title != null && System.currentTimeMillis() < displayTime && mc.player != null && mc.level != null) {
            Matrix3x2fStack pose = context.pose();
            pose.pushMatrix();
            pose.scale(2.0f);
            pose.translate(-context.guiWidth() / 4f, -context.guiHeight() / 4f);
            context.drawCenteredString(mc.font, title, context.guiWidth() / 2, context.guiHeight() / 2, 0xFFFFFFFF);
            pose.popMatrix();
        }
    }

    public void updateTitleMsg(String title, long displayTime) {
        updateTitleMsg(title, displayTime, SoundEvents.NOTE_BLOCK_PLING.value());
    }

    public void updateTitleMsg(String title, long displayTime, SoundEvent soundEvent) {
        this.title = title;
        this.displayTime = System.currentTimeMillis() + displayTime;
        ToolList.getInstance().playSound(soundEvent);
    }

}
