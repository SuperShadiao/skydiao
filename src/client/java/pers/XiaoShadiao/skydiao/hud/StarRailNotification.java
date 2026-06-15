package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import pers.XiaoShadiao.skydiao.customsounds.CustomSounds;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.awt.*;
import java.util.Objects;

public class StarRailNotification extends XSDHUD {

    @Override
    public void runRegister() {
        HudElementRegistry.addLast(Objects.requireNonNull(Identifier.tryBuild("skydiao", "starrailtip")), this);
    }

    public String message = "";

    private Type type;
    private double animationFadeIn, animationFadeOut = 40;

    private long updateTime;

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {

        boolean flag = System.currentTimeMillis() - updateTime > 3000 + message.length() * 100L;

        float particalTick = tickCounter.getGameTimeDeltaTicks() * 3;

        if(animationFadeIn > 0) animationFadeIn -= 20 * particalTick;
        if(animationFadeIn < 0) animationFadeIn = 0 * particalTick;

        if(animationFadeOut < 40 && flag) animationFadeOut += 5 * particalTick;

        if(animationFadeOut >= 40) return;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        float alpha = flag ? (float) (1 - animationFadeOut / 40f) : (float) ((20f - animationFadeIn / 20) / 20.0);
        switch(type) {
            case warning:
                float strLength = mc.font.width(message);
                context.blit(RenderPipelines.GUI_TEXTURED, type.resource, width / 2 - 16, (int) (height * 0.22 + (1-Math.cos(animationFadeIn / 800 * Math.PI)) * 20), 0, 0, 32, 32, 32, 32, ((int) (alpha * 255) << 24) | 0xFFFFFF);
                int color = new Color(1, 72 / 255f, 72 / 225f, alpha).getRGB();
                context.fill((int) (width / 2f - 20 - (25 * (Math.cos(Math.min(animationFadeIn / 600, 0.5) * Math.PI))) - strLength / 2), (int) (height * 0.22f + 55 + (1-Math.cos(animationFadeIn / 800 * Math.PI) * 20)), (int) (width / 2f + 20 + (25 * (Math.cos(Math.min(animationFadeIn / 600, 0.5) * Math.PI))) + strLength / 2), (int) (height * 0.22f + 75 + (1-Math.cos(animationFadeIn / 800 * Math.PI) * 20)), color);
                context.centeredText(mc.font, message, width / 2, (int) (height * 0.22f + 61 + (1-Math.cos(animationFadeIn / 800 * Math.PI) * 20)), new Color(1, 1f, 1f, alpha).getRGB());
                break;
            case success:
                strLength = mc.font.width(message);
                context.blit(RenderPipelines.GUI_TEXTURED, type.resource, width / 2 - 16, (int) (height * 0.22 + (1-Math.cos(animationFadeIn / 800 * Math.PI)) * 20), 0, 0, 32, 32, 32, 32, ((int) (alpha * 255) << 24) | 0xFFFFFF);
                color = 0x4EBFFE | new Color(0, 0, 0, alpha).getRGB();
                context.fill((int) (width / 2f - 20 - (25 * (Math.cos(Math.min(animationFadeIn / 600, 0.5) * Math.PI))) - strLength / 2), (int) (height * 0.22f + 55 + (1-Math.cos(animationFadeIn / 800 * Math.PI) * 20)), (int) (width / 2f + 20 + (25 * (Math.cos(Math.min(animationFadeIn / 600, 0.5) * Math.PI))) + strLength / 2), (int) (height * 0.22f + 75 + (1-Math.cos(animationFadeIn / 800 * Math.PI) * 20)), color);
                context.centeredText(mc.font, message, width / 2, (int) (height * 0.22f + 61 + (1-Math.cos(animationFadeIn / 800 * Math.PI) * 20)), new Color(1, 1f, 1f, alpha).getRGB());
                break;
        }

    }

    public void updateMessage(String s, Type type0)  {
        message = s;
        animationFadeIn = 400;
        animationFadeOut = 0;
        updateTime = System.currentTimeMillis();
        type = type0;
        ToolList.getInstance().playSound(CustomSounds.STAR_RAIL_NOTIFICATION);
    }

    public enum Type {
        warning(Identifier.tryBuild("skydiao", "textures/starrail/warning.png")),
        success(Identifier.tryBuild("skydiao", "textures/starrail/success.png")),
        tip(Identifier.tryBuild("skydiao", "textures/starrail/warning.png"));

        public final Identifier resource;

        Type(Identifier resource) {
            this.resource = Objects.requireNonNull(resource);
        }
    }
}
