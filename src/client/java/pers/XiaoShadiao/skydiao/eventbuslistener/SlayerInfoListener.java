package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SlayerInfoListener extends AbstractListener {

    public enum SlayerType {
        ZOMBIE("Revenant Horror"),
        SPIDER("Tarantula Broodfather"),
        WOLF("Sven Packmaster"),
        ENDERMAN("Voidgloom Seraph"),
        BLAZE("Inferno Demonlord"),
        VAMPIRE("Riftstalker Bloodfiend");

        public final String displayName;

        SlayerType(String displayName) {
            this.displayName = displayName;
        }
    }

    public static final class Boss {
        public final ArmorStand anchor;
        public final String spawner;
        public ArmorStand nameEntity;
        public ArmorStand timeEntity;
        public LivingEntity base;
        public SlayerType type;
        public String tier;

        private Boss(ArmorStand anchor, String spawner) {
            this.anchor = anchor;
            this.spawner = spawner;
        }
    }

    public static final Pattern NAME_PATTERN = Pattern.compile("Spawned by: (\\[.+\\] )?(?<name>\\S+)");
    public static final Pattern VS_PATTERN = Pattern.compile("(?<time>\\d+:\\d+)(?<ability>( \\S+ \\d+\\.\\d+s)*)");
    public static final Pattern BS_PATTERN = Pattern.compile("(?<phase>ASHEN|SPIRIT|AURIC|CRYSTAL) .+\\d (?<time>\\d+:\\d+)");
    public static final Pattern TIME_PATTERN = Pattern.compile("(?<time>\\d+:\\d+)");
    public static final Pattern HP_PATTERN = Pattern.compile(".+ (((?<hp>\\d+(\\.(\\d)+)?)(?<extra>[Mk]?)❤)|((?<shield>\\d+) Hits?)).*");
    public static final Pattern ABILITY_PATTERN = Pattern.compile("(?<name>\\S+) (?<time>\\d+.\\d+)s*");

    private static final double SEARCH_RADIUS = 16.0;
    private static final int SCAN_INTERVAL = 5;

    private static final Pattern ICHOR_PATTERN = Pattern.compile("\\d+\\.\\d+s");
    private static final List<ArmorStand> ICHORS = new ArrayList<>();

    private static Boss current;
    private static boolean pinned;
    private static int cooldown;

    @Override
    public String getListenerName() {
        return "SlayerInfoListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> scan());
        AttackEntityCallback.EVENT.register(SlayerInfoListener::onAttack);
        LevelRenderEvents.END_MAIN.register(SlayerInfoListener::onLastRender);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((mc, level) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void reset() {
        current = null;
        pinned = false;
        ICHORS.clear();
    }

    public static Boss current() {
        return current;
    }

    public static String stripName(ArmorStand stand) {
        if (stand.getCustomName() == null) return "";
        return ToolList.getInstance().deleteColorCode(stand.getCustomName().getString()).trim();
    }

    private static void scan() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            current = null;
            return;
        }
        if (cooldown-- > 0) return;
        cooldown = SCAN_INTERVAL;

        AABB box = client.player.getBoundingBox().inflate(SEARCH_RADIUS);
        Boss candidate = null;
        double bestDistance = Double.MAX_VALUE;
        for (ArmorStand stand : client.level.getEntitiesOfClass(ArmorStand.class, box)) {
            String name = stripName(stand);
            Matcher matcher = NAME_PATTERN.matcher(name);
            if (!matcher.matches()) continue;
            double distance = stand.distanceToSqr(client.player);
            if (distance >= bestDistance) continue;
            Boss boss = resolve(client, stand, matcher.group("name"), box);
            if (boss == null) continue;
            bestDistance = distance;
            candidate = boss;
        }
        if (pinned && current != null && current.base != null && current.base.isAlive()) {
            updateIchors(client, box);
            return;
        }
        pinned = false;
        current = candidate;
        if (candidate != null && candidate.type == SlayerType.VAMPIRE) {
            updateIchors(client, box);
        } else {
            ICHORS.clear();
        }
    }

    private static void updateIchors(Minecraft client, AABB box) {
        ICHORS.removeIf(stand -> !stand.isAlive() || stand.level() != client.level);
        for (ArmorStand stand : client.level.getEntitiesOfClass(ArmorStand.class, box)) {
            if (ICHORS.contains(stand)) continue;
            if (ICHOR_PATTERN.matcher(stripName(stand)).matches()) ICHORS.add(stand);
        }
    }

    private static InteractionResult onAttack(Player player, Level level, InteractionHand hand, Entity entity,
                                              @Nullable EntityHitResult hitResult) {
        if (entity == null) return InteractionResult.PASS;
        Boss boss = resolveNear(level, entity);
        if (boss != null) {
            current = boss;
            pinned = true;
        }
        return InteractionResult.PASS;
    }

    private static Boss resolveNear(Level level, Entity target) {
        Minecraft client = Minecraft.getInstance();
        AABB box = target.getBoundingBox().inflate(3.0);
        for (ArmorStand anchor : level.getEntitiesOfClass(ArmorStand.class, box)) {
            Matcher matcher = NAME_PATTERN.matcher(stripName(anchor));
            if (!matcher.matches()) continue;
            Boss boss = resolve(client, anchor, matcher.group("name"), box);
            if (boss != null && boss.base == target) return boss;
        }
        return null;
    }

    private static void onLastRender(LevelRenderContext context) {
        if (!ConfigManager.slayerInfo.getValue()) return;
        if (current == null || current.type != SlayerType.VAMPIRE) return;
        if (ICHORS.isEmpty()) return;
        RenderUtils.WorldRender render =
                RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        for (ArmorStand ichor : ICHORS) {
            if (!ichor.isAlive()) continue;
            Vec3 pos = ichor.position();
            RenderUtils.renderTrace(render, pos, 0.05F, 0.6F, 0.54F, 1.0F);
            RenderUtils.renderCircle(render, pos, 0.45F, 20);
        }
        render.finishDraw();
        for (ArmorStand ichor : ICHORS) {
            if (!ichor.isAlive()) continue;
            RenderUtils.renderNameTag(context, ichor.position().add(0, 1.2, 0), Component.literal("Ichor"));
        }
    }

    private static Boss resolve(Minecraft client, ArmorStand anchor, String spawner, AABB box) {
        Boss boss = new Boss(anchor, spawner);
        for (ArmorStand stand : client.level.getEntitiesOfClass(ArmorStand.class, box)) {
            if (stand == anchor) continue;
            if (Math.abs(stand.getX() - anchor.getX()) > 0.2) continue;
            if (Math.abs(stand.getZ() - anchor.getZ()) > 0.2) continue;
            if (Math.abs(stand.getY() - anchor.getY()) > 2.0) continue;
            String name = stripName(stand);
            if (name.isEmpty()) continue;
            if (boss.nameEntity == null) {
                SlayerType type = typeOf(name);
                if (type == null) continue;
                boss.nameEntity = stand;
                boss.type = type;
                boss.tier = tierOf(name);
            } else if (boss.timeEntity == null && matchesTime(boss.type, name)) {
                boss.timeEntity = stand;
            }
        }
        if (boss.nameEntity == null) return null;
        boss.base = findBase(client, anchor, boss.type);
        return boss;
    }

    private static boolean matchesTime(SlayerType type, String name) {
        String normalized = name.replace("KILLER SPRING", "KILLERSPRING");
        return switch (type) {
            case BLAZE -> BS_PATTERN.matcher(normalized).matches();
            case VAMPIRE -> VS_PATTERN.matcher(normalized).matches();
            default -> TIME_PATTERN.matcher(normalized).matches();
        };
    }

    private static SlayerType typeOf(String name) {
        for (SlayerType type : SlayerType.values()) {
            if (name.contains(type.displayName)) return type;
        }
        if (name.contains("Bloodfiend")) return SlayerType.VAMPIRE;
        if (name.contains("Atoned Horror")) return SlayerType.ZOMBIE;
        return null;
    }

    private static String tierOf(String name) {
        if (name.contains("Atoned Horror")) return "V";
        for (String tier : new String[]{" V ", " IV ", " III ", " II ", " I "}) {
            if (name.contains(tier)) return tier.trim();
        }
        return "I";
    }

    private static LivingEntity findBase(Minecraft client, ArmorStand anchor, SlayerType type) {
        AABB box = anchor.getBoundingBox().inflate(2.0);
        for (LivingEntity entity : client.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity == anchor) continue;
            if (Math.abs(entity.getX() - anchor.getX()) > 1.5) continue;
            if (Math.abs(entity.getZ() - anchor.getZ()) > 1.5) continue;
            if (!matchesBase(type, entity)) continue;
            return entity;
        }
        return null;
    }

    private static boolean matchesBase(SlayerType type, LivingEntity entity) {
        return switch (type) {
            case ZOMBIE -> entity instanceof Zombie;
            case SPIDER -> entity instanceof Spider;
            case WOLF -> entity instanceof Wolf;
            case ENDERMAN -> entity instanceof Enderman;
            case BLAZE -> entity instanceof Blaze;
            case VAMPIRE -> entity instanceof Player;
        };
    }

    public static int maxHealth(SlayerType type, String tier) {
        int index = switch (tier == null ? "I" : tier) {
            case "V" -> 4;
            case "IV" -> 3;
            case "III" -> 2;
            case "II" -> 1;
            default -> 0;
        };
        int[][] table = switch (type) {
            case ZOMBIE -> new int[][]{{500}, {20000}, {400000}, {1500000}, {10000000}};
            case SPIDER -> new int[][]{{750}, {30000}, {900000}, {2400000}};
            case WOLF -> new int[][]{{2000}, {40000}, {750000}, {2000000}};
            case ENDERMAN -> new int[][]{{300000}, {12000000}, {50000000}, {210000000}};
            case BLAZE -> new int[][]{{2500000}, {10000000}, {45000000}, {150000000}};
            case VAMPIRE -> new int[][]{{625}, {1100}, {1800}, {2400}, {3000}};
        };
        if (index >= table.length) index = table.length - 1;
        return table[index][0];
    }

    public static int nextPhase(SlayerType type, String tier, double health) {
        if (type == SlayerType.VAMPIRE) {
            return switch (tier == null ? "I" : tier) {
                case "V" -> health > 2400 ? 2400 : health > 1800 ? 1800 : health > 1200 ? 1200 : health > 600 ? 600 : 0;
                case "IV" -> health > 1800 ? 1800 : health > 1100 ? 1100 : health > 480 ? 480 : 0;
                case "III" -> health > 1350 ? 1350 : health > 720 ? 720 : health > 360 ? 360 : 0;
                case "II" -> health > 660 ? 660 : health > 220 ? 220 : 0;
                default -> health > 313 ? 313 : health > 125 ? 125 : 0;
            };
        }
        if (type == SlayerType.ENDERMAN) {
            return switch (tier == null ? "I" : tier) {
                case "IV" -> health > 140000000 ? 140000000 : health > 70000000 ? 70000000 : 0;
                case "III", "II" -> health > 33333333 ? 33333333 : health > 16666666 ? 16666666 : 0;
                default -> health > 200000 ? 200000 : health > 100000 ? 100000 : 0;
            };
        }
        if (type == SlayerType.BLAZE) {
            return switch (tier == null ? "I" : tier) {
                case "IV" -> health > 100000000 ? 100000000 : health > 50000000 ? 50000000 : 0;
                case "III" -> health > 30000000 ? 30000000 : health > 15000000 ? 15000000 : 0;
                case "II" -> health > 5000000 ? 5000000 : 0;
                default -> health > 1250000 ? 1250000 : 0;
            };
        }
        return 0;
    }
}
