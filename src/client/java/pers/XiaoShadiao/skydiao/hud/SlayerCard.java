package pers.XiaoShadiao.skydiao.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.SlayerInfoListener;
import pers.XiaoShadiao.skydiao.utils.anim.BezierUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public final class SlayerCard implements IslandCard {

    private static final int PAD = 10;
    private static final int CONTENT_MIN_WIDTH = 124;
    private static final int BAR_EXTRA = 12;
    private static final int BAR_HEIGHT = 6;
    private static final int ABILITY_ROW = 16;
    private static final int ABILITY_BAR_HEIGHT = 4;

    private static final int TEXT = 0xFFF2F2F5;
    private static final int BAR_TRACK = 0xFF33414A;
    private static final int HEALTH_RED = 0xFFB71C1C;
    private static final int SHIELD_PURPLE = 0xFFAD84C7;

    private record AbilityRow(String name, String time, int color, float progress) {
    }

    private record Snapshot(String title, String healthLine, float shield, float health, float phase,
                            List<AbilityRow> abilities) {
    }

    private static final Map<String, Integer> ABILITY_TOTAL = new HashMap<>();
    private static final Map<String, Integer> ABILITY_COLOR = new HashMap<>();
    private static final Map<String, Long> ABILITY_START = new HashMap<>();

    static {
        ABILITY_TOTAL.put("SPECTRAL", 20);
        ABILITY_TOTAL.put("TWINCLAWS", 36);
        ABILITY_TOTAL.put("CLOTTING", 28);
        ABILITY_TOTAL.put("STONEWRATH", 60);
        ABILITY_TOTAL.put("MANIA", 520);
        ABILITY_TOTAL.put("IMPEL", 10);
        ABILITY_TOTAL.put("KILLERSPRING", 300);
        ABILITY_TOTAL.put("RESURRECT", 76);
        ABILITY_TOTAL.put("ICHOR", 40);

        ABILITY_COLOR.put("SPECTRAL", 0xFF6F7C6F);
        ABILITY_COLOR.put("TWINCLAWS", 0xFFEDAD1E);
        ABILITY_COLOR.put("CLOTTING", 0xFF970921);
        ABILITY_COLOR.put("STONEWRATH", 0xFF12A6A0);
        ABILITY_COLOR.put("MANIA", 0xFF970688);
        ABILITY_COLOR.put("IMPEL", 0xFF8CFEEE);
        ABILITY_COLOR.put("KILLERSPRING", 0xFFEA59E6);
        ABILITY_COLOR.put("RESURRECT", 0xFFFFFFFF);
        ABILITY_COLOR.put("ICHOR", 0xFF0D988A);
    }

    private static final BezierUtil HEALTH_ANIMATION = new BezierUtil(4F, 0F);
    private static final BezierUtil PHASE_ANIMATION = new BezierUtil(4F, 0F);
    private static final BezierUtil COLOR_ANIMATION = new BezierUtil(4F, 0F);

    @Override
    public int priority() {
        return 200;
    }

    @Override
    public boolean visible() {
        if (!ConfigManager.slayerInfo.getValue()) return false;
        SlayerInfoListener.Boss boss = SlayerInfoListener.current();
        return boss != null && boss.nameEntity != null;
    }

    @Override
    public int width(Minecraft client) {
        Snapshot snapshot = snapshot();
        if (snapshot == null) return 0;
        int content = Math.max(CONTENT_MIN_WIDTH, client.font.width(snapshot.title));
        content = Math.max(content, client.font.width(snapshot.healthLine));
        for (AbilityRow row : snapshot.abilities) {
            content = Math.max(content, client.font.width(row.name + " " + row.time + "s"));
        }
        return content + BAR_EXTRA + PAD * 2;
    }

    @Override
    public int height() {
        Snapshot snapshot = snapshot();
        if (snapshot == null) return 0;
        int count = snapshot.abilities.size();
        return PAD * 2 + 34 + (count == 0 ? 0 : count * ABILITY_ROW - 1);
    }

    @Override
    public void render(GuiGraphicsExtractor context, int x, int y, int alpha) {
        Snapshot snapshot = snapshot();
        if (snapshot == null) return;
        Minecraft client = Minecraft.getInstance();

        int content = Math.max(CONTENT_MIN_WIDTH, client.font.width(snapshot.title));
        content = Math.max(content, client.font.width(snapshot.healthLine));
        for (AbilityRow row : snapshot.abilities) {
            content = Math.max(content, client.font.width(row.name + " " + row.time + "s"));
        }
        int barWidth = content + BAR_EXTRA;

        HEALTH_ANIMATION.update(clamp(snapshot.health));
        PHASE_ANIMATION.update(clamp(snapshot.phase));
        COLOR_ANIMATION.update(snapshot.shield);
        HEALTH_ANIMATION.freeze();
        PHASE_ANIMATION.freeze();
        COLOR_ANIMATION.freeze();

        int contentX = x + PAD;
        int contentY = y + PAD;
        int barColor = lerpColor(HEALTH_RED, SHIELD_PURPLE, COLOR_ANIMATION.get());

        context.text(client.font, Component.literal(snapshot.title), contentX, contentY, withAlpha(alpha, TEXT));
        context.text(client.font, Component.literal(snapshot.healthLine), contentX, contentY + 12,
                withAlpha(alpha, barColor));

        int barY = contentY + 25;
        HudDraw.bar(context, contentX, barY, barWidth, BAR_HEIGHT, withAlpha(alpha, BAR_TRACK));
        HudDraw.bar(context, contentX, barY, Math.round(barWidth * HEALTH_ANIMATION.get()), BAR_HEIGHT,
                withAlpha(alpha, barColor));
        if (PHASE_ANIMATION.get() > 0F && snapshot.shield < 1F) {
            HudDraw.bar(context, contentX, barY, Math.round(barWidth * PHASE_ANIMATION.get()), BAR_HEIGHT,
                    withAlpha(Math.round(alpha * 0.55F), barColor));
        }

        int abilityY = contentY + 34;
        for (AbilityRow row : snapshot.abilities) {
            context.text(client.font, Component.literal(row.name + " " + row.time + "s"), contentX, abilityY,
                    withAlpha(alpha, row.color));
            HudDraw.bar(context, contentX, abilityY + 11, barWidth, ABILITY_BAR_HEIGHT, withAlpha(alpha, BAR_TRACK));
            HudDraw.bar(context, contentX, abilityY + 11, Math.round(barWidth * row.progress), ABILITY_BAR_HEIGHT,
                    withAlpha(alpha, row.color));
            abilityY += ABILITY_ROW;
        }
    }

    private static Snapshot snapshot() {
        SlayerInfoListener.Boss boss = SlayerInfoListener.current();
        if (boss == null || boss.nameEntity == null) return null;

        String title = boss.type.displayName + " " + boss.tier;
        String healthLine = "Health: ?";
        float health = 0F;
        float phase = 0F;
        float shield = 0F;
        List<AbilityRow> abilities = new ArrayList<>();

        String name = SlayerInfoListener.stripName(boss.nameEntity).replace(",", "");
        Matcher matcher = SlayerInfoListener.HP_PATTERN.matcher(name);
        if (matcher.matches()) {
            if (matcher.group("shield") != null) {
                shield = 1F;
                double hits = Double.parseDouble(matcher.group("shield"));
                healthLine = "Hits: " + matcher.group("shield");
                health = (float) Math.min(1.0, hits / 100.0);
            } else {
                String extra = matcher.group("extra") == null ? "" : matcher.group("extra");
                double hp = Double.parseDouble(matcher.group("hp"));
                if ("M".equals(extra)) hp *= 1_000_000;
                else if ("k".equals(extra)) hp *= 1_000;
                int maxHealth = Math.max(1, SlayerInfoListener.maxHealth(boss.type, boss.tier));
                healthLine = "Health: " + matcher.group("hp") + extra;
                health = (float) (hp / maxHealth);
                phase = (float) (SlayerInfoListener.nextPhase(boss.type, boss.tier, hp) / (double) maxHealth);
            }
        }

        if (boss.type == SlayerInfoListener.SlayerType.BLAZE && boss.timeEntity != null) {
            Matcher phaseMatcher = SlayerInfoListener.BS_PATTERN.matcher(SlayerInfoListener.stripName(boss.timeEntity));
            if (phaseMatcher.matches()) title = title + " · " + phaseMatcher.group("phase");
        }
        if (boss.type == SlayerInfoListener.SlayerType.VAMPIRE && boss.timeEntity != null) {
            abilities = abilities(boss.timeEntity);
        }
        return new Snapshot(title, healthLine, shield, health, phase, abilities);
    }

    private static List<AbilityRow> abilities(net.minecraft.world.entity.decoration.ArmorStand timeEntity) {
        List<AbilityRow> rows = new ArrayList<>();
        String raw = SlayerInfoListener.stripName(timeEntity).replace("KILLER SPRING", "KILLERSPRING");
        Matcher root = SlayerInfoListener.VS_PATTERN.matcher(raw);
        if (!root.matches()) return rows;
        String ability = root.group("ability") == null ? "" : root.group("ability").trim();
        Matcher matcher = SlayerInfoListener.ABILITY_PATTERN.matcher(ability);
        long now = System.currentTimeMillis();
        List<String> seen = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group("name");
            String left = matcher.group("time");
            Integer total = ABILITY_TOTAL.get(name);
            if (total == null) continue;
            seen.add(name);
            int ticks = (int) (Double.parseDouble(left) * 20);
            ABILITY_START.putIfAbsent(name, now - (total - ticks) * 50L);
            long elapsed = now - ABILITY_START.get(name);
            float fraction = (float) ((total - elapsed / 50.0) / total);
            rows.add(new AbilityRow(name, left, ABILITY_COLOR.getOrDefault(name, 0xFFFFFFFF), clamp(fraction)));
        }
        ABILITY_START.keySet().removeIf(name -> !seen.contains(name));
        return rows;
    }

    private static float clamp(float value) {
        return Math.max(0F, Math.min(1F, value));
    }

    private static int lerpColor(int from, int to, float t) {
        float clamped = clamp(t);
        int r = Math.round(((from >> 16) & 0xFF) * (1F - clamped) + ((to >> 16) & 0xFF) * clamped);
        int g = Math.round(((from >> 8) & 0xFF) * (1F - clamped) + ((to >> 8) & 0xFF) * clamped);
        int b = Math.round((from & 0xFF) * (1F - clamped) + (to & 0xFF) * clamped);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int alpha, int rgb) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }
}
