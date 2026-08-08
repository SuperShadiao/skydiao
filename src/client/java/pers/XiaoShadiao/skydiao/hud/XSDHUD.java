package pers.XiaoShadiao.skydiao.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.AutoBloodfiendListener;
import pers.XiaoShadiao.skydiao.utils.Register;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class XSDHUD implements HudElement {

    public static final Minecraft mc = Minecraft.getInstance();

    public static final CustomBossbar customBossbar = new CustomBossbar();
    public static final StarRailNotification starRailNotification = new StarRailNotification();
    public static final BlindOrDying blindOrDying = new BlindOrDying();
    public static final BigTitle bigTitle = new BigTitle();
    public static final HUDCrashFixer hudCrashFixer = new HUDCrashFixer();
    public static final AutoBloodfiendListener.TaskRender abfTaskRender = MacroManagerListener.autoBloodfiendListener.new TaskRender();
    public static final GenshinImpactHeatCold genshinImpactHeatCold = new GenshinImpactHeatCold();
    public static final MusicLyricDisplay musicLyricDisplay = new MusicLyricDisplay();
    public static final DungeonReviveItemCD dungeonReviveItemCD = new DungeonReviveItemCD();

    public static final List<XSDHUD> huds = Util.make(new ArrayList<>(), arr -> Register.execRegister(XSDHUD.class, XSDHUD.class, arr::add));

    public static void init() {
        Register.execRegister(XSDHUD.class, XSDHUD.class, XSDHUD::runRegister);
        updateHudOffsetAndScaleFromConfig();
    }

    public abstract void runRegister();

    public abstract void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force);

    public abstract void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter);

    public @Nullable abstract String getHudName();

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        extractRenderState(graphics, deltaTracker, false);
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, boolean force) {
        String name = getHudName();
        HudOffsetAndScale settings = null;
        if(name != null) settings = hudOffsetAndScaleMap.get(name);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        if(settings != null) {
            pose.scale(settings.scale);
            pose.translate(settings.x, settings.y);
        }
        render(graphics, deltaTracker, force);
        pose.popMatrix();
        renderEffect(graphics, deltaTracker);
    }

    public record HudOffsetAndScale(float x, float y, float scale) {}

    public static Map<String, HudOffsetAndScale> hudOffsetAndScaleMap = new HashMap<>();

    public static void updateHudOffsetAndScaleFromConfig() {
        String offsetAndScale = ConfigManager.hudOffsetAndScale.getValue();
        JsonObject jo = JsonParser.parseString(offsetAndScale).getAsJsonObject();
        jo.entrySet().forEach(entry -> {
            HudOffsetAndScale hos = new HudOffsetAndScale(entry.getValue().getAsJsonObject().get("x").getAsFloat(), entry.getValue().getAsJsonObject().get("y").getAsFloat(), entry.getValue().getAsJsonObject().get("scale").getAsFloat());
            hudOffsetAndScaleMap.put(entry.getKey(), hos);
        });
    }

    public static void saveHudOffsetAndScalesToConfig() {
        JsonObject jo = new JsonObject();
        hudOffsetAndScaleMap.forEach((k, v) -> {
            JsonObject jo2 = new JsonObject();
            jo2.addProperty("x", v.x);
            jo2.addProperty("y", v.y);
            jo2.addProperty("scale", v.scale);
            jo.add(k, jo2);
        });
        ConfigManager.hudOffsetAndScale.setValue(jo.toString());
        ConfigManager.saveConfig();
    }

    public static void saveHudOffsetAndScale(XSDHUD hud, float x, float y, float scale) {
        if(hud.getHudName() != null) {
            hudOffsetAndScaleMap.put(hud.getHudName(), new HudOffsetAndScale(x, y, scale));
            saveHudOffsetAndScalesToConfig();
        }
    }

    public static void saveHudAppendOffset(XSDHUD hud, float deltaX, float deltaY) {
        if(hud.getHudName() != null) {
            HudOffsetAndScale hos = hudOffsetAndScaleMap.computeIfAbsent(hud.getHudName(), n -> new HudOffsetAndScale(0, 0, 1.0f));
            HudOffsetAndScale newHos = new HudOffsetAndScale(hos.x + deltaX, hos.y + deltaY, hos.scale);
            hudOffsetAndScaleMap.put(hud.getHudName(), newHos);
            saveHudOffsetAndScalesToConfig();
        }
    }

    public static void saveHudAppendScale(XSDHUD hud, float deltaScale) {
        if(hud.getHudName() != null) {
            HudOffsetAndScale hos = hudOffsetAndScaleMap.computeIfAbsent(hud.getHudName(), n -> new HudOffsetAndScale(0, 0, 1.0f));
            HudOffsetAndScale newHos = new HudOffsetAndScale(hos.x, hos.y, hos.scale + deltaScale);
            hudOffsetAndScaleMap.put(hud.getHudName(), newHos);
            saveHudOffsetAndScalesToConfig();
        }
    }

    public static void resetHudSetting(XSDHUD currentSelected) {
        if(currentSelected.getHudName() != null) {
            saveHudOffsetAndScale(currentSelected, 0, 0, 1.0f);
        }
        saveHudOffsetAndScalesToConfig();
    }

}
