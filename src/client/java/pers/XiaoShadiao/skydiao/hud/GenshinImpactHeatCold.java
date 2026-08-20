package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.customsounds.CustomSounds;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.awt.*;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenshinImpactHeatCold extends XSDHUD {

    public ColorType debug1 = null;
    public Integer debugHeatOrColdValue = null;

    private boolean forceMode;
    private BooleanSupplier delayedForceModeUpdate;

    // ===================================================================

    private int heatOrColdValue;
    private float L,L2;

    private ColorType currentColor = ColorType.heat;
    private int timewarn;
    private int timewarnrender;
    private float animation,animation2;

    private SimpleSoundInstance keepingWarningSound;

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force) {
        delayedForceModeUpdate = () -> force;
        // TODO 自动生成的方法存根
        if(!shouldRender()) return;
        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.translate(-1, 0);
        if(L2 > L) {
            L2 -= tickCounter.getGameTimeDeltaTicks() * 3.2f * getBarSpeed();
            L2 = Math.max(0, L2);
        }
		if(L2 < L) {
            L2 += tickCounter.getGameTimeDeltaTicks() * 3.2f * getBarSpeed();
            L2 = Math.min(100, L2);
        }

        if(animation < 100) {
            if(animation == 0) ToolList.getInstance().playSound(CustomSounds.YSENTER);
            animation += tickCounter.getGameTimeDeltaTicks() * 4.5f;
        }
		if(animation < 0) animation = 0;
		if(animation > 100) animation = 100;
        // float L = heat / 100f;
        int guiPosX = context.guiWidth() / 2;
        int guiPosY = context.guiHeight() / 2 + 10;

        if(animation <= 20) {
            context.fill(guiPosX - 1, guiPosY - 1, guiPosX + 2, guiPosY + 2, new Color(1,1,1,(animation / 20f)).getRGB());
        } else if(animation > 20 && animation < 80) {
            context.fill((int) (guiPosX - 1 - 43f * ((animation - 20f)*(animation - 20f) / 3600f)), guiPosY - 1, (int) (guiPosX + 2 - 43f * ((animation - 20f)*(animation - 20f) / 3600f)), guiPosY + 2, new Color(1,1,1,1f).getRGB());
            context.fill((int) (guiPosX - 1 + 43f * ((animation - 20f)*(animation - 20f) / 3600f)), guiPosY - 1, (int) (guiPosX + 2 + 43f * ((animation - 20f)*(animation - 20f) / 3600f)), guiPosY + 2, new Color(1,1,1,1f).getRGB());
        } else if(animation >= 80 && animation < 100) {
            context.fill(guiPosX - 1 - 43, guiPosY - 1, guiPosX + 2 - 43, guiPosY + 2, new Color(1,1,1, (100 - animation) / 20).getRGB());
            context.fill(guiPosX - 1 + 43, guiPosY - 1, guiPosX + 2 + 43, guiPosY + 2, new Color(1,1,1, (100 - animation) / 20).getRGB());
        }
        // if(animation < 100) animation += 1;
        
        if(animation > 70) {
            for(int I = -4;I <= 164;I++) {
                if(I < -3 || I > 162)
                    context.fill(guiPosX - 40 + I / 2, guiPosY - 50 + 50, guiPosX - 39 + I / 2, guiPosY - 49 + 50, getColorBarGround(currentColor, (int) (255f * (-(70f - animation) / 30f))).getRGB());
                else context.fill(guiPosX - 40 + I / 2, guiPosY - 51 + 50, guiPosX - 39 + I / 2, guiPosY - 48 + 50, getColorBarGround(currentColor, (int) (255f * (-(70f - animation) / 30f))).getRGB());

            }

            // LogManager.getLogger().info(80f / 255f * Math.sin(Math.PI * Math.min(timewarnrender,0) / 30d));
            if(heatOrColdValue >= currentColor.warningStage1) {
                context.fill(guiPosX - 42, guiPosY - 47 + 50, guiPosX - 40 + 83, guiPosY - 52 + 50, new Color(1f,0,0,(float) (75f / 255f * Math.sin(Math.PI * Math.max(timewarnrender,0) / 30d))).getRGB());

                if(heatOrColdValue >= currentColor.warningStage2) {
                    // context.fill(guiPosX - 42, guiPosY - 47 + 50, guiPosX - 40 + 83, guiPosY - 52 + 50, new Color(1f,0,0,(float) (40f / 255f * Math.sin(Math.PI * Math.max(timewarnrender,0) / 30d))).getRGB());
                    context.fill(guiPosX - 42, guiPosY - 47 + 50, guiPosX - 40 + 83, guiPosY - 52 + 50, new Color(1f,0,0,(float) (80f / 255f * (animation2 / 10f))).getRGB());
                }
            }

            for(int I = -4;I <= 164;I++) {

                if((I + 4) / 168f <= L2)

                    if(I < -3 || I > 162) 
                        context.fill(guiPosX - 40 + I / 2, guiPosY - 50 + 50, guiPosX - 39 + I / 2, guiPosY - 49 + 50, getColorBarValue(currentColor, (int) (255f * (-(70f - animation) / 30f))).getRGB());
                    else context.fill(guiPosX - 40 + I / 2, guiPosY - 51 + 50, guiPosX - 39 + I / 2, guiPosY - 48 + 50, getColorBarValue(currentColor, (int) (255f * (-(70f - animation) / 30f))).getRGB());

            }
        }

        pose.popMatrix();
    }

    @Override
    public void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if(heatOrColdValue >= currentColor.warningStage1) {
            draw(context, getColor(currentColor,(int) (75 * (Math.min(timewarn, 20) / 20f))).getRGB());
        }
        if(animation2 > 0) {
            draw2(context, getColor(currentColor,(int) (75f * (animation2 / 10f))).getRGB());
        }
    }

    @Override
    public String getHudName() {
        return "heatcold";
    }

    public void update0(Minecraft mc) {
        if(!ToolList.getInstance().isDevEnvironment() && (!ConfigManager.genshinImpactHeatColdRender.getValue() || !StatusManager.get().isInSkyblock())) return;
        update(mc);
        update(mc);
    }

    public void update(Minecraft mc) {

        if(delayedForceModeUpdate != null) {
            forceMode = delayedForceModeUpdate.getAsBoolean();
            delayedForceModeUpdate = null;
        }

        if(heatOrColdValue >= currentColor.warningStage1) timewarn++; else timewarn = 0;
        if(timewarn % 100 == 1) {
            ToolList.getInstance().playSound(CustomSounds.YSWARNING);
        }
        
        if(heatOrColdValue >= currentColor.warningStage1) timewarnrender++; else timewarnrender = 0;
        if(timewarnrender >= 30) timewarnrender = -10;
        
        if(heatOrColdValue >= currentColor.warningStage2 && animation2 < 10) {
            if(animation2 <= 0 && currentColor.soundStart != null) ToolList.getInstance().playSound(currentColor.soundStart);
            animation2 += 0.1F;
        } else if(heatOrColdValue < currentColor.warningStage2 && animation2 > 0) {
            animation2 -= 0.1F;
        }

        if(heatOrColdValue >= currentColor.warningStage2 && (keepingWarningSound == null || !mc.getSoundManager().isActive(keepingWarningSound))) {
            if(currentColor.sound != null) {
                keepingWarningSound = ToolList.getInstance().playSound(currentColor.sound);
            }
        } else if(heatOrColdValue < currentColor.warningStage2 && keepingWarningSound != null && mc.getSoundManager().isActive(keepingWarningSound)) mc.getSoundManager().stop(keepingWarningSound);
        
        boolean f = false;
        if(mc.level == null) {
            heatOrColdValue = 0;
        } else {
            a:try {
                for(Component s : ToolList.getInstance().fetchScoreboardLines()) {
                    // LogManager.getLogger().info(s);
                    String line = ToolList.getInstance().deleteColorCode(s.getString());
                    if(line.contains("Heat:")) {
                        Matcher m = Pattern.compile(".*?(\\d+).*").matcher(line);
                        if(m.find()) {
                            heatOrColdValue = Integer.parseInt(m.group(1));
                            currentColor = ColorType.heat;
                            f = true;
                            break a;
                        }
                    };
                    if(line.contains("Cold:")) {
                        Matcher m = Pattern.compile(".*?(\\d+).*").matcher(line);
                        if(m.find()) {
                            heatOrColdValue = Integer.parseInt(m.group(1));
                            currentColor = ColorType.cold;
                            f = true;
                            break a;
                        }
                    }
                }
                if(forceMode) {
                    heatOrColdValue = 50;
                    currentColor = ColorType.heat;
                    f = true;
                }
            } catch(Exception e) {
                
            }
            
        }
        if(!f) heatOrColdValue = 0;

        if(ToolList.getInstance().isDevEnvironment()) {
            if(debug1 != null) currentColor = debug1;
            if(debugHeatOrColdValue != null) heatOrColdValue = debugHeatOrColdValue;
        }

        L = (float) heatOrColdValue / 100f;
        
        if(!shouldRender()) animation = 0;
    }

    public boolean shouldRender() {
        // TODO 自动生成的方法存根
        return forceMode || (ConfigManager.genshinImpactHeatColdRender.getValue() && (StatusManager.get().isInSkyblock() || ToolList.getInstance().isDevEnvironment()) && L2 + L != 0);
    }

    private Color getColor(ColorType ct, int alpha) {
        return new Color(ct.color.getRed(), ct.color.getGreen(), ct.color.getBlue(), alpha);
    }
    private Color getColorBarGround(ColorType ct, int alpha) {
        return new Color(ct.colorBarGround.getRed(), ct.colorBarGround.getGreen(), ct.colorBarGround.getBlue(), alpha);
    }
    private Color getColorBarValue(ColorType ct, int alpha) {
        return new Color(ct.colorBarValue.getRed(), ct.colorBarValue.getGreen(), ct.colorBarValue.getBlue(), alpha);
    }
    private float getBarSpeed() {
        return heatOrColdValue == 0 ? 0.01f : Math.sqrt((L - L2) * (L - L2)) > 0.05f ? 0.0055f : 0.001f;
    }

    @Override
    public void runRegister() {
        HudElementRegistry.addFirst(Objects.requireNonNull(Identifier.tryBuild("skydiao", "genshin_impact_heat_cold")), this);
        ClientTickEvents.START_CLIENT_TICK.register(this::update0);
    }

    public enum ColorType {
        heat(new Color(0x9F,0x3A,0x35),
                new Color(0x9F,0x3A,0x35),
                new Color(0xFF,0x7C,0x35),
                80,
                100,
                null,
                null
        ),
        cold(new Color(144, 255, 248),
                new Color(102, 149, 183),
                new Color(144, 255, 248),
                80,
                90,
                CustomSounds.YSCS,
                CustomSounds.YSC
        );

        private final Color color;
        private final Color colorBarGround;
        private final Color colorBarValue;
        private final int warningStage1;
        private final int warningStage2;
        private final SoundEvent soundStart;
        private final SoundEvent sound;

        ColorType(Color color, Color colorBar, Color colorBarValue, int warningStage1, int warningStage2, SoundEvent soundStart, SoundEvent sound) {
            this.color = color;
            this.colorBarGround = colorBar;
            this.colorBarValue = colorBarValue;
            this.warningStage1 = warningStage1;
            this.warningStage2 = warningStage2;
            this.soundStart = soundStart;
            this.sound = sound;
        }
    }

    private void draw(GuiGraphicsExtractor context, int rgb) {
        Matrix3x2fStack pose = context.pose();
        context.guiRenderState.addGuiElement(new ColoredRectangleRenderState2(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), 0, 0,context.guiWidth() / 4, context.guiHeight(), rgb, 0, context.scissorStack.peek()));
        context.guiRenderState.addGuiElement(new ColoredRectangleRenderState2(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), context.guiWidth(), context.guiHeight(), context.guiWidth() * 3 / 4, 0, rgb, 0, context.scissorStack.peek()));
        context.guiRenderState.addGuiElement(new ColoredRectangleRenderState (RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), context.guiWidth(), context.guiHeight(), 0, context.guiHeight() * 3 / 4, rgb, 0, context.scissorStack.peek()));
        context.guiRenderState.addGuiElement(new ColoredRectangleRenderState (RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), 0, 0, context.guiWidth(), context.guiHeight() / 4, rgb, 0, context.scissorStack.peek()));
    }

    private void draw2(GuiGraphicsExtractor context, int rgb) {
        Matrix3x2fStack pose = context.pose();
        context.guiRenderState.addGuiElement(new ColoredRectangleRenderState2(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), 0, 0,context.guiWidth() * 2 / 4, context.guiHeight(), rgb, 0, context.scissorStack.peek()));
        context.guiRenderState.addGuiElement(new ColoredRectangleRenderState2(RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), context.guiWidth(), context.guiHeight(), context.guiWidth() * 2 / 4, 0, rgb, 0, context.scissorStack.peek()));
        context.guiRenderState.addGuiElement(new ColoredRectangleRenderState (RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), context.guiWidth(), context.guiHeight(), 0, context.guiHeight() * 2 / 4, rgb, 0, context.scissorStack.peek()));
        context.guiRenderState.addGuiElement(new ColoredRectangleRenderState (RenderPipelines.GUI, TextureSetup.noTexture(), new Matrix3x2f(pose), 0, 0, context.guiWidth(), context.guiHeight() * 2 / 4, rgb, 0, context.scissorStack.peek()));
    }
}
