package pers.XiaoShadiao.skydiao.utils.statusbar;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 Hypixel 的 action bar 中解析血量、蓝量和防御，供自绘状态条使用。
 */
public final class StatusBarTracker {

    private static final Pattern STATUS_PATTERN = Pattern.compile("(?<status>.+?)(?: {2,}|$)");

    private static final Pattern HEALTH_PATTERN = Pattern.compile(
            "(?<health>[\\d,]+(?:\\.\\d+)?[kKmMbBtT]?)/(?<max>[\\d,]+(?:\\.\\d+)?[kKmMbBtT]?)[\\u2764\\uE010]");

    private static final Pattern MANA_PATTERN = Pattern.compile(
            "(?<mana>[\\d,]+)/(?<max>[\\d,]+)[\\u270E\\uE003](?: ?(?:Mana|(?<overflow>[\\d,]+)[\\u02AC\\uE017]))?");

    private static final Pattern DEFENSE_PATTERN = Pattern.compile(
            "(?<defense>[\\d,]+)[\\u2748\\uE008](?: Defense)?");

    private static final Pattern VITALITY_PATTERN = Pattern.compile(
            "(?<vitality>[\\d,]+)/(?<max>[\\d,]+)(?:[\\uE028]| Vitality)");

    private static final Minecraft MC = Minecraft.getInstance();

    private static Resource health = new Resource(100, 100, 0);
    private static final EstimatedResource mana = new EstimatedResource(new Resource(100, 100, 0));
    private static Resource vitality = new Resource(100, 100, 0);
    private static Resource speed = new Resource(0, 400, 0);
    private static Resource air = new Resource(300, 300, 0);
    private static int defense;

    /** 即将消耗的血量/蓝量/Vitality，由外部消耗预测逻辑更新 */
    private static int healthCost;
    private static int manaCost;
    private static int vitalityCost;

    private static boolean healthSeen;
    private static boolean manaSeen;
    private static boolean defenseSeen;
    private static boolean vitalitySeen;

    private static long ticks;

    private static Component lastMessage = Component.empty();
    private static Component lastReturn = Component.empty();
    private static long lastMessageTime;

    private static boolean initialized;

    private StatusBarTracker() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ClientReceiveMessageEvents.ALLOW_GAME.register(StatusBarTracker::allowGameMessage);
        ClientReceiveMessageEvents.MODIFY_GAME.register(StatusBarTracker::modifyGameMessage);
        ClientTickEvents.END_CLIENT_TICK.register(StatusBarTracker::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static Resource getHealth() {
        return health;
    }

    public static Resource getMana() {
        return mana.resource();
    }

    public static boolean isManaEstimated() {
        return mana.isEstimated();
    }

    public static int getDefense() {
        return defense;
    }

    public static Resource getVitality() {
        return vitality;
    }

    public static Resource getSpeed() {
        return speed;
    }

    public static Resource getAir() {
        return air;
    }

    public static boolean hasHealthData() {
        return healthSeen;
    }

    public static boolean hasManaData() {
        return manaSeen;
    }

    public static boolean hasDefenseData() {
        return defenseSeen;
    }

    public static boolean hasVitalityData() {
        return vitalitySeen;
    }

    public static int getHealthCost() {
        return healthCost;
    }

    public static void setHealthCost(int cost) {
        healthCost = Math.max(cost, 0);
    }

    public static int getManaCost() {
        return manaCost;
    }

    public static void setManaCost(int cost) {
        manaCost = Math.max(cost, 0);
    }

    public static int getVitalityCost() {
        return vitalityCost;
    }

    public static void setVitalityCost(int cost) {
        vitalityCost = Math.max(cost, 0);
    }

    private static boolean shouldHandle() {
        return ConfigManager.statusBars.getValue() && MC.player != null && ToolList.isInSkyblock();
    }

    private static boolean allowGameMessage(Component text, boolean overlay) {
        if (!overlay || !shouldHandle()) {
            return true;
        }
        return !process(text).getString().isEmpty();
    }

    private static Component modifyGameMessage(Component text, boolean overlay) {
        if (!overlay || !shouldHandle()) {
            return text;
        }
        return process(text);
    }

    /**
     * ALLOW_GAME 与 MODIFY_GAME 会分别调用一次，这里缓存 7 tick 内相同 action bar 的结果，避免重复更新估算值。
     */
    private static Component process(Component text) {
        long now = System.currentTimeMillis();
        if (lastMessage.equals(text) && lastMessageTime + 337 > now) {
            return lastReturn;
        }
        lastMessage = text;
        lastMessageTime = now;

        String raw = text.getString();
        String modified = update(raw);
        return lastReturn = modified.equals(raw) ? text : Component.literal(modified);
    }

    private static String update(String actionBar) {
        Matcher statuses = STATUS_PATTERN.matcher(actionBar);
        StringBuilder output = new StringBuilder();

        while (statuses.find()) {
            String segment = statuses.group("status");
            String plain = ChatFormatting.stripFormatting(segment);

            Matcher matcher;
            if ((matcher = HEALTH_PATTERN.matcher(plain)).find()) {
                updateHealth(matcher);
                statuses.appendReplacement(output, "");
            } else if ((matcher = DEFENSE_PATTERN.matcher(plain)).find()) {
                defense = (int) parseNumber(matcher.group("defense"));
                defenseSeen = true;
                statuses.appendReplacement(output, "");
            } else if ((matcher = VITALITY_PATTERN.matcher(plain)).find()) {
                updateVitality(matcher);
                statuses.appendReplacement(output, "");
            } else if ((matcher = MANA_PATTERN.matcher(plain)).find()) {
                updateMana(matcher);
                statuses.appendReplacement(output, "");
            } else {
                statuses.appendReplacement(output, "$0");
            }
        }

        return statuses.appendTail(output).toString().trim();
    }

    private static void updateHealth(Matcher matcher) {
        int max = (int) parseNumber(matcher.group("max"));
        int parsed = (int) parseNumber(matcher.group("health"));

        int value = parsed;
        if (MC.player != null && MC.player.getMaxHealth() > 0) {
            // action bar 只在数值变化时更新，用客户端血量比例补间
            value = Math.round(MC.player.getHealth() * max / MC.player.getMaxHealth());
        }

        int overflow = Math.max(0, parsed - value);
        health = new Resource(Math.min(value, max), max, overflow);
        healthSeen = true;
    }

    private static void tickUpdateHealth() {
        if (MC.player != null && MC.player.getMaxHealth() > 0 && health != null/* && MC.player.hurtTime > 8*/) {
            health = new Resource(Math.round(MC.player.getHealth() * health.max / MC.player.getMaxHealth()), health.max, health.overflow);
        }
    }

    private static void updateMana(Matcher matcher) {
        int value = (int) parseNumber(matcher.group("mana"));
        int max = (int) parseNumber(matcher.group("max"));
        int overflow = matcher.group("overflow") == null ? 0 : (int) parseNumber(matcher.group("overflow"));
        mana.update(new Resource(value, max, overflow));
        manaSeen = true;
    }

    private static void updateVitality(Matcher matcher) {
        int value = (int) parseNumber(matcher.group("vitality"));
        int max = (int) parseNumber(matcher.group("max"));
        vitality = new Resource(Math.min(value, max), max, 0);
        vitalitySeen = true;
    }

    private static void tick(Minecraft client) {
        if (!ConfigManager.statusBars.getValue() || !ToolList.isInSkyblock()) {
            return;
        }
        ticks++;
        mana.tick();

        Player player = client.player;
        if (player == null) {
            return;
        }

        tickUpdateHealth();

        // 与 Skyblocker 相同的近似算法：客户端移动速度换算成 Speed 数值
        float rawSpeed = player.isSprinting() ? (player.getSpeed() / 1.3f) * 1000 : player.getSpeed() * 1000;
        speed = new Resource(Math.round(rawSpeed), getSpeedMax(player), 0);

        int maxAir = player.getMaxAirSupply();
        air = new Resource(Math.max(0, Math.min(player.getAirSupply(), maxAir)), maxAir, 0);

        updateCosts();
    }

    private static final Pattern ABILITY_ACTIVE_TYPE = Pattern.compile("Ability: .*? ([A-Z ]{2,})");
    private static final Pattern ABILITY_COST = Pattern.compile("(\\w+) Cost: ([\\d.]+)(%?)");

    private static void updateCosts() {
        if(MC.player == null) return;

        List<Component> lines = MC.player.getMainHandItem().getTooltipLines(Item.TooltipContext.of(MC.level), MC.player, TooltipFlag.NORMAL);

        String abilityActiveType = null;

        String manaCostAbilityActiveType = null;
        float tempManaCost = 0;
        String healthCostAbilityActiveType = null;
        float tempHealthCost = 0;
        String vitalityCostAbilityActiveType = null;
        float tempVitalityCost = 0;

        for (Component line : lines) {
            String text = ToolList.getInstance().deleteColorCode(line.getString());
            Matcher matcher = ABILITY_ACTIVE_TYPE.matcher(text);
            if (matcher.find()) {
                abilityActiveType = matcher.group(1);
            } else if(abilityActiveType != null) {
                matcher = ABILITY_COST.matcher(text);
                if(matcher.find()) {
                    String costType = matcher.group(1);
                    String cost = matcher.group(2);
                    String percent = matcher.group(3);
                    float costValue = Float.parseFloat(cost);
                    switch (costType) {
                        case "Mana" -> {
                            String tempStr = compareAbilityActiveType(manaCostAbilityActiveType, abilityActiveType);
                            if (!Objects.equals(tempStr, manaCostAbilityActiveType)) {
                                manaCostAbilityActiveType = tempStr;
                                if ("%".equals(percent)) {
                                    tempManaCost = costValue / 100 * getMana().max;
                                } else {
                                    tempManaCost = costValue;
                                }
                            }
                        }
                        case "Health" -> {
                            String tempStr = compareAbilityActiveType(healthCostAbilityActiveType, abilityActiveType);
                            if (!Objects.equals(tempStr, healthCostAbilityActiveType)) {
                                healthCostAbilityActiveType = tempStr;
                                if ("%".equals(percent)) {
                                    tempHealthCost = costValue / 100 * health.max;
                                } else {
                                    tempHealthCost = costValue;
                                }
                            }
                        }
                        case "Vitality" -> {
                            String tempStr = compareAbilityActiveType(vitalityCostAbilityActiveType, abilityActiveType);
                            if (!Objects.equals(tempStr, vitalityCostAbilityActiveType)) {
                                vitalityCostAbilityActiveType = tempStr;
                                if ("%".equals(percent)) {
                                    tempVitalityCost = costValue / 100 * vitality.max;
                                } else {
                                    tempVitalityCost = costValue;
                                }
                            }
                        }
                    }
                }
            }
        }

        setHealthCost((int) tempHealthCost);
        setManaCost((int) tempManaCost);
        setVitalityCost((int) tempVitalityCost);
    }

    private static String compareAbilityActiveType(String oldType, String newType) {
        if(oldType == null || MC.player == null) return newType;
        boolean playerSneak = MC.player.isCrouching();
        if(playerSneak) {
            if(newType.startsWith("SNEAK") && !oldType.startsWith("SNEAK")) {
                return newType;
            }
        } else {
            if(newType.equals("RIGHT CLICK") && !oldType.equals("RIGHT CLICK")) {
                return newType;
            }
        }

        return oldType;
    }

    private static int getSpeedMax(Player player) {
        int max = 400;
        int youngDragonPieces = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            String name = player.getItemBySlot(slot).getHoverName().getString();
            if (name.contains("Racing Helmet")) {
                max = 500;
            } else if (name.contains("Young Dragon")) {
                youngDragonPieces++;
            }
        }
        if (youngDragonPieces == 4) {
            max = 500;
        }
        return max;
    }

    private static void reset() {
        health = new Resource(100, 100, 0);
        mana.reset(new Resource(100, 100, 0));
        vitality = new Resource(100, 100, 0);
        speed = new Resource(0, 400, 0);
        air = new Resource(300, 300, 0);
        defense = 0;
        healthCost = 0;
        manaCost = 0;
        vitalityCost = 0;
        healthSeen = false;
        manaSeen = false;
        defenseSeen = false;
        vitalitySeen = false;
        ticks = 0;
        lastMessage = Component.empty();
        lastReturn = Component.empty();
        lastMessageTime = 0;
    }

    private static long parseNumber(String value) {
        String normalized = value.replace(",", "").trim().toUpperCase(Locale.ROOT);
        double multiplier = 1;
        if (normalized.endsWith("K")) {
            multiplier = 1_000;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("M")) {
            multiplier = 1_000_000;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("B")) {
            multiplier = 1_000_000_000;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("T")) {
            multiplier = 1_000_000_000_000d;
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return (long) (Double.parseDouble(normalized) * multiplier);
    }

    public record Resource(int value, int max, int overflow) {
    }

    /**
     * action bar 不保证每 tick 都更新，这里根据最近一次增量估算回蓝。
     */
    public static final class EstimatedResource {
        private Resource resource;
        private int perSecond;
        private long lastTick;
        private int lastValue;

        private EstimatedResource(Resource baseValue) {
            this.resource = baseValue;
        }

        private void update(Resource newValue) {
            this.resource = newValue;
            if (newValue.value() != newValue.max() && lastValue < newValue.value()) {
                perSecond = Math.max(newValue.value() - lastValue, 0);
            }
            if (lastValue != newValue.value() || newValue.value() == newValue.max()) {
                lastTick = ticks;
            }
            lastValue = newValue.value();
        }

        private void tick() {
            if (ticks - lastTick > 0 && (ticks - lastTick) % 20 == 0) {
                resource = new Resource(Math.min(resource.value() + perSecond, resource.max()), resource.max(), resource.overflow());
            }
        }

        private boolean isEstimated() {
            return ticks - lastTick > 30;
        }

        private void reset(Resource baseValue) {
            this.resource = baseValue;
            this.perSecond = 0;
            this.lastTick = 0;
            this.lastValue = 0;
        }

        public Resource resource() {
            return resource;
        }
    }
}
