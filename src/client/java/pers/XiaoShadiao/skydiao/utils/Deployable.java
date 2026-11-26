package pers.XiaoShadiao.skydiao.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.util.regex.Pattern;

public enum Deployable {

    RADIANT("Radiant", "§aRadiant", 18, DeployableType.FLUX, 1),
    MANA_FLUX("Mana Flux", "§9Mana Flux", 18, DeployableType.FLUX, 2),
    OVERFLUX("Overflux", "§5Overflux", 18, DeployableType.FLUX, 3),
    PLASMAFLUX("Plasmaflux", "§d§lPlasmaflux", 20, DeployableType.FLUX, 4),
    DWARVEN_LANTERN("Dwarven Lantern", "§fDwarven Lantern", 30, DeployableType.LANTERN, 1),
    MITHRIL_LANTERN("Mithril Lantern", "§aMithril Lantern", 30, DeployableType.LANTERN, 2),
    TITANIUM_LANTERN("Titanium Lantern", "§9Titanium Lantern", 30, DeployableType.LANTERN, 3),
    GLACITE_LANTERN("Glacite Lantern", "§5Glacite Lantern", 30, DeployableType.LANTERN, 4, true),
    WILL_O_WISP("Will-o'-wisp", "§6§lWill-o'-wisp", 30, DeployableType.LANTERN, 5, true),
    BLACK_HOLE("Black Hole", "§5Black Hole", 20, DeployableType.BLACK_HOLE),
    UMBERELLA("Umberella", "§9Umberella", 30, DeployableType.UMBERELLA),
    ;

    private final String displayName;
    private final int range;
    private final DeployableType type;
    private final int tier;
    private final boolean fullShaft;
    private final Pattern pattern;

    private LivingEntity entity;
    // 过期时间戳（毫秒），0 表示已过期
    private long expiryTime;

    Deployable(String deployableName, String displayName, int range, DeployableType type) {
        this(deployableName, displayName, range, type, 0, false);
    }

    Deployable(String deployableName, String displayName, int range, DeployableType type, int tier) {
        this(deployableName, displayName, range, type, tier, false);
    }

    Deployable(String deployableName, String displayName, int range, DeployableType type, int tier, boolean fullShaft) {
        this.displayName = displayName;
        this.range = range;
        this.type = type;
        this.tier = tier;
        this.fullShaft = fullShaft;
        this.pattern = Pattern.compile(deployableName + " (?<time>\\d+)s");
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRange() {
        return range;
    }

    public DeployableType getType() {
        return type;
    }

    public int getTier() {
        return tier;
    }

    public boolean isFullShaft() {
        return fullShaft;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public void setEntity(LivingEntity entity) {
        this.entity = entity;
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    public void setExpiryTime(long expiryTime) {
        this.expiryTime = expiryTime;
    }

    public boolean isInRange(LivingEntity entity) {
        if (hasShaftBuff()) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return range > entity.distanceTo(mc.player);
    }

    public boolean isInRange() {
        if (hasShaftBuff()) return true;
        if (entity == null) return false;
        return isInRange(entity);
    }

    public boolean hasShaftBuff() {
        return fullShaft && "mineshaft".equals(StatusManager.get().getMode());
    }

    public boolean isActive() {
        // Mineshaft 比实体渲染距离更大
        return expiryTime > System.currentTimeMillis()
                && ((entity != null && ToolList.getInstance().isEntityOnWorld(entity)) || hasShaftBuff());
    }

    public boolean isExpired() {
        return !isActive();
    }

    public void reset() {
        entity = null;
        expiryTime = 0;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public void setRemainSecond(int time) {
        expiryTime = System.currentTimeMillis() + 1000L * time;
    }

    public int getRemainSecond() {
        return (int) ((expiryTime - System.currentTimeMillis()) / 1000L);
    }
}
