package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

import java.util.Objects;

public class BlindOrDying extends XSDHUD {

    private float effectAnimation = 0;

    @Override
    public void runRegister() {
        HudElementRegistry.addFirst(Objects.requireNonNull(Identifier.tryBuild("skydiao", "blind_or_dying_effect")), this);
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force) {

    }

    @Override
    public void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        effectAnimation += tickCounter.getGameTimeDeltaTicks() / 7.5f;
        if(mc.player == null) return;
        if(ConfigManager.dyingtip.getValue() && mc.player.getHealth() / mc.player.getMaxHealth() < 0.25) {
            draw(context, 0xFFFF0000);
        } else if(ConfigManager.noblind.getValue() && mc.player.hasEffect(MobEffects.BLINDNESS)) {
            draw(context, 0xFF000000);
        }
    }

    @Override
    public String getHudName() {
        return null;
    }

    private void draw(GuiGraphicsExtractor context, int rgb) {
        rgb &= (0x00FFFFFF | (Mth.lerpInt(Mth.sin(effectAnimation), 45, 60) << 24));
        // context.fillGradient(0, 0, context.guiWidth(), context.guiHeight() / 4, rgb, 0);
        // context.fillGradient(0, context.guiHeight(),context.guiWidth() / 4, 0, rgb, 0);
        // context.fillGradient(context.guiWidth(), context.guiHeight(), 0, context.guiHeight() * 3 / 4,rgb, 0);
        // context.fillGradient(context.guiWidth(), 0, context.guiWidth() * 3 / 4, context.guiHeight() / 4, rgb, 0);
        Matrix3x2fStack pose = context.pose();
        context.guiRenderState.addGuiElement(new ColoredRectangleRenderState2(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), 0, 0,context.guiWidth() / 4, context.guiHeight(), rgb, 0, context.scissorStack.peek()));
        context.guiRenderState.addGuiElement(new ColoredRectangleRenderState2(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), context.guiWidth(), context.guiHeight(), context.guiWidth() * 3 / 4, 0, rgb, 0, context.scissorStack.peek()));
        context.guiRenderState.addGuiElement(new ColoredRectangleRenderState (RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), context.guiWidth(), context.guiHeight(), 0, context.guiHeight() * 3 / 4, rgb, 0, context.scissorStack.peek()));
        context.guiRenderState.addGuiElement(new ColoredRectangleRenderState (RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), 0, 0, context.guiWidth(), context.guiHeight() / 4, rgb, 0, context.scissorStack.peek()));
    }
}
