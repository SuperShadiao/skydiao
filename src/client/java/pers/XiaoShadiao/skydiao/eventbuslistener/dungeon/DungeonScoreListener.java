package pers.XiaoShadiao.skydiao.eventbuslistener.dungeon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.tab.TabReader;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 地牢分数估算，公式和 Skyblocker 的 {@code DungeonScore} 一致：
 * 时间分 100 + 探索分 100 + 技能分 100 + 奖励分（墓穴/隐藏房间/王子/蝙蝠），入口层各项乘 0.7。
 *
 * <p>数据来源：侧边栏的 Cleared 百分比和层数、Tab 里的秘密百分比/完成房间/墓穴/谜题状态，
 * 以及聊天栏的死亡、Mimic/Prince/Bat 和血房击杀消息。
 * 依赖 API 的部分（市长 Paul +10、灵宠死亡惩罚减免）暂时没有接入。
 */
public class DungeonScoreListener extends AbstractListener {

    private static final Pattern FLOOR_PATTERN = Pattern.compile(".*The Catacombs \\(([EFM])(\\d*)\\)");
    private static final Pattern CLEARED_PATTERN = Pattern.compile("Cleared: (\\d+)%");
    private static final Pattern SECRETS_PATTERN = Pattern.compile("Secrets Found: (\\d+\\.?\\d*)%");
    private static final Pattern CRYPTS_PATTERN = Pattern.compile("Crypts: (\\d+)");
    private static final Pattern ROOMS_PATTERN = Pattern.compile("Completed Rooms: (\\d+)");
    private static final Pattern PUZZLE_COUNT_PATTERN = Pattern.compile("Puzzles: \\((\\d+)\\)");
    private static final Pattern PUZZLE_STATE_PATTERN = Pattern.compile(".+?: \\[(.)]");
    /** Tab 队伍栏里的死亡数：{@code Team Deaths: 2}。 */
    private static final Pattern TEAM_DEATHS_PATTERN = Pattern.compile("Team Deaths: (\\d+)");
    private static final Pattern MIMIC_PATTERN = Pattern.compile(".*?(?:Mimic dead!?|Mimic Killed!|\\$SKYTILS-DUNGEON-SCORE-MIMIC\\$)$");
    private static final Pattern PRINCE_PATTERN = Pattern.compile(".*?(?:Prince dead!?|Prince Killed!)$");
    private static final Pattern BAT_PATTERN = Pattern.compile(".*?(?:Bat dead!?|Bat Killed!)$");
    private static final String PRINCE_MESSAGE = "A Prince falls. +1 Bonus Score";
    private static final String BAT_MESSAGE = "A Bat has been slain. +1 Bonus Score";
    private static final Pattern MIMIC_FLOORS = Pattern.compile("[FM][67]");
    /** 结算界面的最终分数：Team Score: 311 (S+)。 */
    private static final Pattern TEAM_SCORE = Pattern.compile(".*Team Score: (\\d+).*");

    private static volatile boolean started;
    private static volatile int score;
    private static volatile int crypts;
    /** 市长 Paul 的 +10 加成：由上一局的结算分数推断，之后所有场次沿用。 */
    private static volatile boolean paulBonus;
    private static String floor = "";
    private static FloorRequirement requirement = FloorRequirement.NONE;
    private static boolean entrance;
    private static boolean mimicFloor;
    private static boolean bloodRoomCompleted;
    private static long bloodRoomDoneAt = -1;
    private static long startingTime;
    private static int puzzleCount;
    private static int deathCount;
    private static boolean mimicKilled;
    private static boolean princeKilled;
    private static boolean batKilled;
    private static int ticks;

    public static boolean isDungeonStarted() {
        return started;
    }

    public static int getScore() {
        return score;
    }

    public static int getCrypts() {
        return crypts;
    }

    public static int getDeaths() {
        return deathCount;
    }

    @Override
    public String getListenerName() {
        return "DungeonScore";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        // 直接抓包：部分 mod 会改写聊天栏内容，收消息事件可能拿不到原文
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onUnload);
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        reset();
    }

    private boolean onPacket(Packet<?> packet, PacketListener listener, PacketProcessor processor) {
        if (packet instanceof ClientboundSystemChatPacket(Component content, boolean overlay) && !overlay) {
            handleMessage(content.getString());
        } else if (packet instanceof ClientboundEntityEventPacket event
                && event.getEventId() == EntityEvent.DEATH && mc.level != null) {
            // F6/F7 的 Mimic 是没穿装备的小僵尸，死亡事件比聊天消息更可靠
            handleEntityDeath(event.getEntity(mc.level));
        }
        return false;
    }

    private void handleEntityDeath(Entity entity) {
        if (mimicKilled || !mimicFloor) return;
        if (!(entity instanceof Zombie zombie) || !zombie.isBaby()) return;
        if (!zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                || !zombie.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                || !zombie.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                || !zombie.getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
            return;
        }
        mimicKilled = true;
        ToolList.sendChatMessage("/pc Mimic dead!");
    }

    private void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || !StatusManager.get().isInDungeon()) {
            reset();
            return;
        }
        if (floor.isEmpty()) detectFloor();
        if (!started) {
            if (floor.isEmpty()) return;
            started = true;
            startingTime = System.currentTimeMillis();
            puzzleCount = readPuzzleCount();
            try {
                requirement = FloorRequirement.valueOf(floor);
            } catch (IllegalArgumentException ignored) {
                requirement = FloorRequirement.NONE;
            }
            mimicFloor = MIMIC_FLOORS.matcher(floor).matches();
            entrance = floor.equals("E");
        }
        if (bloodRoomDoneAt > 0 && System.currentTimeMillis() >= bloodRoomDoneAt) {
            bloodRoomCompleted = true;
            bloodRoomDoneAt = -1;
        }
        if (++ticks % 20 == 0) {
            Metrics metrics = readMetrics();
            crypts = metrics.crypts;
            // 死亡数只从 Tab 的 Team Deaths 读取，聊天里的死亡消息格式不稳定
            if (metrics.teamDeaths >= 0) deathCount = metrics.teamDeaths;
            score = calculateScore(metrics);
        }
    }

    private void handleMessage(String raw) {
        if (!started) return;
        String text = ToolList.getInstance().deleteColorCode(raw);
        Matcher teamScore = TEAM_SCORE.matcher(text);
        if (teamScore.matches()) {
            detectPaulBonus(Integer.parseInt(teamScore.group(1)));
            return;
        }
        if (text.equals("[BOSS] The Watcher: You have proven yourself. You may pass.")) {
            bloodRoomDoneAt = System.currentTimeMillis() + 5000;
            return;
        }
        if (MIMIC_PATTERN.matcher(text).matches()) {
            mimicKilled = true;
        } else if (text.equals(PRINCE_MESSAGE)) {
            // 原生消息只有自己这一端会收到，顺手帮队友报一下；第三方消息不重复发
            if (!princeKilled) ToolList.sendChatMessage("/pc Prince dead!");
            princeKilled = true;
        } else if (PRINCE_PATTERN.matcher(text).matches()) {
            princeKilled = true;
        } else if (text.equals(BAT_MESSAGE)) {
            if (!batKilled) ToolList.sendChatMessage("/pc Bat dead!");
            batKilled = true;
        } else if (BAT_PATTERN.matcher(text).matches()) {
            batKilled = true;
        }
    }

    private static void reset() {
        started = false;
        score = 0;
        floor = "";
        requirement = FloorRequirement.NONE;
        entrance = false;
        mimicFloor = false;
        bloodRoomCompleted = false;
        bloodRoomDoneAt = -1;
        startingTime = 0;
        puzzleCount = 0;
        deathCount = 0;
        mimicKilled = false;
        princeKilled = false;
        batKilled = false;
        ticks = 0;
    }

    private static void detectFloor() {
        for (String line : ToolList.getInstance().fetchScoreboardLinesNoColor()) {
            Matcher matcher = FLOOR_PATTERN.matcher(line);
            if (matcher.matches()) {
                floor = matcher.group(1) + matcher.group(2);
                return;
            }
        }
    }

    /**
     * 用结算分数推断市长 Paul 的 +10 是否生效：
     * 实际分数 ≥ 311 说明基础分已经到顶还多出加成；比预测低 8 分以上说明多算了这 10 分。
     */
    private static void detectPaulBonus(int actual) {
        if (actual >= 313) { // 后期小沙雕测试应该设置为313
            if (!paulBonus) ToolList.getInstance().log.info("地牢分数：检测到市长 Paul 加成 (+10)");
            paulBonus = true;
        } else if (actual <= score - 8) {
            if (paulBonus) ToolList.getInstance().log.info("地牢分数：未检测到市长 Paul 加成，取消 (+10)");
            paulBonus = false;
        }
    }

    private static int calculateScore(Metrics metrics) {
        if (entrance) {
            return Math.round(calculateTimeScore() * 0.7f) + Math.round(calculateExploreScore(metrics) * 0.7f)
                    + Math.round(calculateSkillScore(metrics) * 0.7f) + Math.round(calculateBonusScore(metrics) * 0.7f);
        }
        return calculateTimeScore() + calculateExploreScore(metrics) + calculateSkillScore(metrics) + calculateBonusScore(metrics);
    }

    private static int calculateTimeScore() {
        int score = 100;
        int timeSpent = (int) ((System.currentTimeMillis() - startingTime) / 1000);
        if (requirement.timeLimit <= 0 || timeSpent < requirement.timeLimit) return score;

        double past = ((double) (timeSpent - requirement.timeLimit) / requirement.timeLimit) * 100;
        if (past < 20) return score - (int) past / 2;
        if (past < 40) return score - (int) (10 + (past - 20) / 4);
        if (past < 50) return score - (int) (15 + (past - 40) / 5);
        if (past < 60) return score - (int) (17 + (past - 50) / 6);
        return Math.clamp(score - (int) (18 + (2.0 / 3.0) + (past - 60) / 7), 0, 100);
    }

    private static int calculateExploreScore(Metrics metrics) {
        int totalRooms = totalRooms(metrics);
        int completedRoomScore = Math.clamp(totalRooms != 0
                ? (int) (60.0 * (metrics.rooms + extraCompletedRooms()) / totalRooms) : 0, 0, 60);
        double required = Math.max(1, requirement.percentage);
        int secretsScore = Math.clamp((int) (40 * Math.min(required, metrics.secrets) / required), 0, 40);
        return completedRoomScore + secretsScore;
    }

    private static int calculateSkillScore(Metrics metrics) {
        int totalRooms = totalRooms(metrics);
        int completedRoomScore = Math.clamp(totalRooms != 0
                ? (int) (80.0 * (metrics.rooms + extraCompletedRooms()) / totalRooms) : 0, 0, 80);
        return 20 + Math.clamp(completedRoomScore - metrics.puzzlePenalty - deathCount * 2, 0, 80);
    }

    private static int calculateBonusScore(Metrics metrics) {
        int cryptsScore = Math.clamp(metrics.crypts, 0, 5);
        int mimicScore = mimicKilled ? 2 : 0;
        // 没播报 Mimic 死亡但秘密已经 100%，说明 Mimic 一定被打了
        if (metrics.secrets >= 100 && mimicFloor) mimicScore = 2;
        int paulScore = paulBonus ? 10 : 0;
        return paulScore + cryptsScore + mimicScore + (princeKilled ? 1 : 0) + (batKilled ? 1 : 0);
    }

    /** 血房还没打完时补 2 个房间（入口层 1 个），打完血房但还没进 Boss 再补 1 个。 */
    private static int extraCompletedRooms() {
        if (!bloodRoomCompleted) return entrance ? 1 : 2;
        return entrance ? 0 : 1;
    }

    private static int totalRooms(Metrics metrics) {
        return metrics.clearPercent <= 0 ? 0 : (int) Math.round(metrics.rooms / metrics.clearPercent);
    }

    private static int readPuzzleCount() {
        for (String raw : readTabLines()) {
            Matcher matcher = PUZZLE_COUNT_PATTERN.matcher(raw);
            if (matcher.find()) return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    private static Metrics readMetrics() {
        Metrics metrics = new Metrics();
        for (String raw : readTabLines()) {
            Matcher matcher = SECRETS_PATTERN.matcher(raw);
            if (matcher.find()) metrics.secrets = Double.parseDouble(matcher.group(1));
            matcher = CRYPTS_PATTERN.matcher(raw);
            if (matcher.find()) metrics.crypts = Integer.parseInt(matcher.group(1));
            matcher = ROOMS_PATTERN.matcher(raw);
            if (matcher.find()) metrics.rooms = Integer.parseInt(matcher.group(1));
            matcher = TEAM_DEATHS_PATTERN.matcher(raw);
            if (matcher.find()) metrics.teamDeaths = Integer.parseInt(matcher.group(1));
            matcher = PUZZLE_STATE_PATTERN.matcher(raw);
            if (matcher.find() && (matcher.group(1).equals("✖") || matcher.group(1).equals("✦"))) {
                metrics.puzzlePenalty += 10;
            }
        }
        for (String line : ToolList.getInstance().fetchScoreboardLinesNoColor()) {
            Matcher matcher = CLEARED_PATTERN.matcher(line);
            if (matcher.find()) {
                metrics.clearPercent = Integer.parseInt(matcher.group(1)) / 100.0;
                break;
            }
        }
        return metrics;
    }

    private static List<String> readTabLines() {
        return TabReader.getNoColorLines();
    }

    /** 按分数段给出和 Skyblocker HUD 一样的评级文本。 */
    public static String formatScore() {
        int value = score;
        if (value < 100) return "§c" + String.format("%03d", value) + " §7(D)";
        if (value < 160) return "§9" + String.format("%03d", value) + " §7(C)";
        if (value < 230) return "§a" + String.format("%03d", value) + " §7(B)";
        if (value < 270) return "§5" + String.format("%03d", value) + " §7(A)";
        if (value < 300) return "§e" + String.format("%03d", value) + " §7(S)";
        return "§e§l" + String.format("%03d", value) + " §7(S+)";
    }

    private static final class Metrics {
        private double secrets;
        private int crypts;
        private int rooms;
        private int puzzlePenalty;
        private double clearPercent;
        /** Tab 里的 Team Deaths；没有读到时为 -1。 */
        private int teamDeaths = -1;
    }

    private enum FloorRequirement {
        E(30, 1200), F1(30, 600), F2(40, 600), F3(50, 600), F4(60, 720), F5(70, 600),
        F6(85, 720), F7(100, 840), M1(100, 480), M2(100, 480), M3(100, 480), M4(100, 480),
        M5(100, 480), M6(100, 600), M7(100, 840), NONE(0, 0);

        private final int percentage;
        private final int timeLimit;

        FloorRequirement(int percentage, int timeLimit) {
            this.percentage = percentage;
            this.timeLimit = timeLimit;
        }
    }
}
