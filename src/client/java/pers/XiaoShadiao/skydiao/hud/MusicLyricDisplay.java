package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.musicplayer.PlayerThread;
import pers.XiaoShadiao.skydiao.utils.musicplayer.StringLyric;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MusicLyricDisplay extends XSDHUD {

    public static final StringLyric empty = new StringLyric("粒子");

    public static final Color c1 = new Color(255,255,255,100);
    public static final Color c2 = new Color(0,255,0,255);

    public Map<Integer, String> map = new HashMap<>();

    public StringLyric text1 = StringLyric.emptyInstance, text2 = StringLyric.emptyInstance, text3 = StringLyric.emptyInstance, text4 = StringLyric.emptyInstance, text5 = StringLyric.emptyInstance;

    public float animation;
    public int flag, flag2;

    @Override
    public void runRegister() {
        HudElementRegistry.addFirst(Objects.requireNonNull(Identifier.tryBuild("skydiao", "music_lyric_display")), this);
    }

    @Override
    public void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter) {

    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force) {
        if(!PlayerThread.isPlaying() && !force) return;

        StringLyric s = getLyric2(-2);
        if(!s.equals(text1)) {
            animation = 20;
            text1 = s;
        }
        s = getLyric2(-1);
        if(!s.equals(text2)) {
            animation = 20;
            text2 = s;
        }
        s = getLyric2(0);
        if(!s.equals(text3)) {
            animation = 20;
            text3 = s;
        }
        s = getLyric2(1);
        if(!s.equals(text4)) {
            animation = 20;
            text4 = s;
        }
        s = getLyric2(2);
        if(!s.equals(text5)) {
            animation = 20;
            text5 = s;
        }

        if(animation > 0) animation -= tickCounter.getGameTimeDeltaTicks() / 8;

        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        float scale;
        float size = /*(double) Config管理.getMusicLyricSize() / 100d*/1;
        scale = size;
        pose.scale(scale, scale);

        Color c2 = new Color(ConfigManager.lyricColor1.getValue()),
                c1 = new Color(ConfigManager.lyricColor2.getValue());

        context.centeredText(mc.font, text1.lyric(), (int) (context.guiWidth() / 2f / scale), (int) az((context.guiHeight() * 0.3f / scale) + (- mc.font.lineHeight - 4), (context.guiHeight() * 0.3f / scale) + (- mc.font.lineHeight - 4) * 2), new Color(c1.getRed(),c1.getGreen(),c1.getBlue(), (int) az(100, 5)).getRGB());
        context.centeredText(mc.font, text4.lyric(), (int) (context.guiWidth() / 2f / scale), (int) az((context.guiHeight() * 0.3f / scale) + (+ mc.font.lineHeight + 4) * 2, (context.guiHeight() * 0.3f / scale) + (+ mc.font.lineHeight + 4)), new Color(c1.getRed(),c1.getGreen(),c1.getBlue(), (int) az(5, 100)).getRGB());

        pose.popMatrix();
        pose.pushMatrix();

        scale = az(1.1f * size, 1 * size);
        pose.scale(scale, scale);

        context.centeredText(mc.font, text2.lyric(), (int) (context.guiWidth() / 2f / scale), (int) az((context.guiHeight() * 0.3f / scale), (context.guiHeight() * 0.3f / scale) + (- mc.font.lineHeight - 4)), new Color(c1.getRed(),c1.getGreen(),c1.getBlue(), (int) az(255, 100)).getRGB());

        pose.popMatrix();
        pose.pushMatrix();
        scale = az(size, 1.1f * size);
        pose.scale(scale, scale);
        context.centeredText(mc.font, text3.lyric(), (int) (context.guiWidth() / 2f / scale), (int) az((context.guiHeight() * 0.3f / scale) + (+ mc.font.lineHeight + 4), (context.guiHeight() * 0.3f / scale)), new Color(c2.getRed(),c2.getGreen(),c2.getBlue(), (int) az(100, 255)).getRGB());

        pose.popMatrix();
    }

    @Override
    public String getHudName() {
        return "music_lyric";
    }

    private StringLyric getLyric2(int i) {
        if(PlayerThread.current == null/* && mc.currentScreen instanceof 鬼_文字设置*/) {
            return empty;
        } else if (PlayerThread.current != null) {
            return PlayerThread.current.getLyric(i);
        }
        return empty;
    }

    private float az(float start, float end) {
//        if(animation == 20) return start;
//        if(animation <= 0 || animation > 20) return end;
//        return start + (end - start) * ((20d - animation) / 20d);
        return Mth.clampedLerp(20 - animation, start, end);
    }

}
