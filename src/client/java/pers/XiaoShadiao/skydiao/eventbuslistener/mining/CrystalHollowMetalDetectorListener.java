package pers.XiaoShadiao.skydiao.eventbuslistener.mining;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 搬迁自 Skyblocker 的 MetalDetector.
 * 在 Mines of Divan 使用金属探测器时, 根据动作栏提示的宝藏距离逐步缩小宝藏可能生成的位置.
 */
public class CrystalHollowMetalDetectorListener extends AbstractMiningListener {

    private static final Pattern TREASURE_PATTERN = Pattern.compile("(§3§lTREASURE: §b)(\\d+\\.?\\d?)m");
    private static final Pattern KEEPER_PATTERN = Pattern.compile("Keeper of (\\w+)");
    private static final Map<String, Vec3i> KEEPER_OFFSETS = Map.of(
            "Diamond", new Vec3i(33, 0, 3),
            "Lapis", new Vec3i(-33, 0, -3),
            "Emerald", new Vec3i(-3, 0, 33),
            "Gold", new Vec3i(3, 0, -33)
    );
    private static final Set<Vec3i> KNOWN_CHEST_OFFSETS = Set.of(
            new Vec3i(-38, -22, 26),
            new Vec3i(38, -22, -26),
            new Vec3i(-40, -22, 18),
            new Vec3i(-41, -20, 22),
            new Vec3i(-5, -21, 16),
            new Vec3i(40, -22, -30),
            new Vec3i(-42, -20, -28),
            new Vec3i(-43, -22, -40),
            new Vec3i(42, -19, -41),
            new Vec3i(43, -21, -16),
            new Vec3i(-1, -22, -20),
            new Vec3i(6, -21, 28),
            new Vec3i(7, -21, 11),
            new Vec3i(7, -21, 22),
            new Vec3i(-12, -21, -44),
            new Vec3i(12, -22, 31),
            new Vec3i(12, -22, -22),
            new Vec3i(12, -21, 7),
            new Vec3i(12, -21, -43),
            new Vec3i(-14, -21, 43),
            new Vec3i(-14, -21, 22),
            new Vec3i(-17, -21, 20),
            new Vec3i(-20, -22, 0),
            new Vec3i(1, -21, 20),
            new Vec3i(19, -22, 29),
            new Vec3i(20, -22, 0),
            new Vec3i(20, -21, -26),
            new Vec3i(-23, -22, 40),
            new Vec3i(22, -21, -14),
            new Vec3i(-24, -22, 12),
            new Vec3i(23, -22, 26),
            new Vec3i(23, -22, -39),
            new Vec3i(24, -22, 27),
            new Vec3i(25, -22, 17),
            new Vec3i(29, -21, -44),
            new Vec3i(-31, -21, -12),
            new Vec3i(-31, -21, -40),
            new Vec3i(30, -21, -25),
            new Vec3i(-32, -21, -40),
            new Vec3i(-36, -20, 42),
            new Vec3i(-37, -21, -14),
            new Vec3i(-37, -21, -22)
    );

    private volatile boolean inMinesOfDivan;
    private Vec3i minesCenter;
    private double previousDistance;
    private Vec3 previousPlayerPos;
    private boolean newTreasure = true;
    private boolean startedLooking;
    private volatile List<Vec3i> possibleBlocks = new ArrayList<>();

    private Component lastMsg = Component.empty();

    @Override
    public String getListenerName() {
        return "CrystalHollowMetalDetectorListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.ALLOW_GAME.register(this::onGameMessage);
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((m, l) -> reset());
    }

    private void onStartTick(Minecraft mc) {
        inMinesOfDivan = ConfigManager.crystalHollowMetalDetector.getValue() && isInCrystalHollows() && isInMinesOfDivan();
    }

    private boolean isInMinesOfDivan() {
        if (mc.level == null || mc.player == null) return false;
        return ToolList.getInstance().fetchScoreboardLinesNoColor().stream().anyMatch(line -> line.contains("Mines of Divan"));
    }

    /**
     * 处理动作栏中金属探测器的距离提示, 以及挖到宝藏后的重置消息.
     */
    private boolean onGameMessage(Component text, boolean overlay) {
        if (!ConfigManager.crystalHollowMetalDetector.getValue() || !isInCrystalHollows()) return true;
        if (!overlay) {
            checkChestFound(text);
            return true;
        }
        if (!inMinesOfDivan || mc.player == null) return true;

        Matcher treasureDistanceMatcher = TREASURE_PATTERN.matcher(text.getString());
        if (!treasureDistanceMatcher.find()) return true;

        double distance = Double.parseDouble(treasureDistanceMatcher.group(2));
        Vec3 playerPos = mc.player.position();
        int previousPossibleBlockCount = possibleBlocks.size();

        if (!startedLooking) {
            startedLooking = true;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e手持金属探测器右键保持静止, 会自动根据距离猜测可能的位置"));
        }

        //如果可以找到矿洞中心, 使用已知的箱子偏移量加快搜索
        if (minesCenter == null) {
            findCenterOfMines();
        }

        Vec3 deltaMovement = mc.player.getDeltaMovement();
        if (/*distance == previousDistance && *//*playerPos.equals(previousPlayerPos)*/deltaMovement.x == 0 && deltaMovement.z == 0) {
            updatePossibleBlocks(distance, playerPos);
            if(possibleBlocks.isEmpty()) {
                delayTickExecutor.delayExec(() -> onGameMessage(text, overlay), 2);
            }
        }

        if (possibleBlocks.size() != previousPossibleBlockCount) {
            if (possibleBlocks.size() == 1) {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §a已确定宝藏位置!"));
            } else if (possibleBlocks.size() > 1) {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §e可能的宝藏位置: §b" + possibleBlocks.size() + " §e个"));
            }
        }

        previousDistance = distance;
        previousPlayerPos = playerPos;
        return true;
    }

    /**
     * 挖到宝藏后重置助手.
     */
    private void checkChestFound(Component text) {
        if (!inMinesOfDivan) return;
        if (text.getString().startsWith("You found")) {
            newTreasure = true;
            possibleBlocks = new ArrayList<>();
        }
    }

    /**
     * 用玩家与宝藏的距离缩小可能的位置, 直到只剩一个.
     *
     * @param distance  与宝藏的距离
     * @param playerPos 玩家位置
     */
    private void updatePossibleBlocks(double distance, Vec3 playerPos) {
        List<Vec3i> candidates = new ArrayList<>();
        if (newTreasure) {
            newTreasure = false;
            if (minesCenter != null) {
                //已知矿洞中心时直接检查预定义的箱子偏移量
                for (Vec3i knownOffset : KNOWN_CHEST_OFFSETS) {
                    Vec3i checkPos = minesCenter.offset(knownOffset).offset(0, 1, 0);
                    if (Math.abs(playerPos.distanceTo(Vec3.atLowerCornerOf(checkPos)) - distance) < 0.25) {
                        candidates.add(checkPos);
                    }
                }
            } else {
                for (int x = (int) -distance; x <= distance; x++) {
                    for (int z = (int) -distance; z <= distance; z++) {
                        Vec3i checkPos = new Vec3i((int) playerPos.x + x, (int) playerPos.y, (int) playerPos.z + z);
                        if (Math.abs(playerPos.distanceTo(Vec3.atLowerCornerOf(checkPos)) - distance) < 0.25) {
                            candidates.add(checkPos);
                        }
                    }
                }
            }
        } else {
            for (Vec3i location : possibleBlocks) {
                if (Math.abs(playerPos.distanceTo(Vec3.atLowerCornerOf(location)) - distance) < 0.25) {
                    candidates.add(location);
                }
            }
        }

        possibleBlocks = candidates;

        //如果可能位置为空则说明计算出错, 重置后重新开始
        if (candidates.isEmpty()) {
            newTreasure = true;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c暂无搜索结果, 开始重新检测"));
        }
    }

    /**
     * 通过 Keeper 的盔甲架名称找到 Mines of Divan 的中心点, 以便使用预定义的箱子偏移量.
     */
    private void findCenterOfMines() {
        if (mc.player == null || mc.level == null) return;
        AABB searchBox = mc.player.getBoundingBox().inflate(500d);
        List<ArmorStand> armorStands = mc.level.getEntitiesOfClass(ArmorStand.class, searchBox, ArmorStand::hasCustomName);

        for (ArmorStand armorStand : armorStands) {
            String name = armorStand.getName().getString();
            Matcher nameMatcher = KEEPER_PATTERN.matcher(name);

            if (nameMatcher.matches()) {
                Vec3i offset = KEEPER_OFFSETS.get(nameMatcher.group(1));
                if (offset == null) continue;
                minesCenter = armorStand.blockPosition().offset(offset);
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §a已定位 Mines of Divan 的中心点"));
                return;
            }
        }
    }

    private void reset() {
        inMinesOfDivan = false;
        minesCenter = null;
        possibleBlocks = new ArrayList<>();
        previousPlayerPos = null;
        previousDistance = 0;
        newTreasure = true;
        startedLooking = false;
    }

    /**
     * 渲染宝藏或可能的宝藏位置.
     */
    private void onLastRender(LevelRenderContext context) {
        //只在开启功能, 位于 Mines of Divan 且可能位置数量不多时渲染
        List<Vec3i> blocks = possibleBlocks;
        if (!inMinesOfDivan || blocks.isEmpty() || blocks.size() > 8) return;

        RenderUtils.WorldRender worldRenderLine = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        RenderUtils.WorldRender worldRenderFill = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);
        //只有一个位置时渲染黄色路径点和指向它的引导线
        if (blocks.size() == 1) {
            Vec3i treasure = blocks.getFirst().offset(0, -1, 0);
            BlockPos treasurePos = new BlockPos(treasure.getX(), treasure.getY(), treasure.getZ());
            RenderUtils.renderESP(worldRenderLine, treasurePos, 1f, 1f, 1f, 1f, false);
            RenderUtils.renderESP(worldRenderFill, treasurePos, 1f, 1f, 1f, 1f, true);
            RenderUtils.renderWaypoint(context, Vec3.atCenterOf(treasurePos), Component.literal("§e宝藏"), 1.414f);
            if (mc.player != null) {
                RenderUtils.renderTrace(worldRenderLine, treasurePos, 1f, 1f, 1f, 1f);
            }
        } else {
            for (Vec3i block : blocks) {
                RenderUtils.renderESP(worldRenderLine, new BlockPos(block.getX(), block.getY(), block.getZ()), 1f, 1f, 1f, 1f, false);
            }
        }
        worldRenderLine.finishDraw();
        worldRenderFill.finishDraw();
    }
}
