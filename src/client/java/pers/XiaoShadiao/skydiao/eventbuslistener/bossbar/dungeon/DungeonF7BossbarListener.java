package pers.XiaoShadiao.skydiao.eventbuslistener.bossbar.dungeon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.hud.CustomBossbar;
import pers.XiaoShadiao.skydiao.hud.StarRailNotification;
import pers.XiaoShadiao.skydiao.mixin.client.MixinBossbarEventGetter;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.*;

public class DungeonF7BossbarListener extends AbstractDungeonBossbar {

    public static final Identifier F7_BOSS_ICON = Objects.requireNonNull(Identifier.tryBuild("skydiao", "textures/skyblock/boss/wither.png"));
    public static final Component[] NAMES = new Component[]{Component.literal("Maxor"), Component.literal("Storm"), Component.literal("Goldor"), Component.literal("Necron")};
    public static final double[] HEALTHES = new double[]{100_000_000, 400_000_000, 750_000_000, 1_000_000_000};

    private LerpingBossEvent f7Boss;
    private WitherBoss f7BossTarget;

    private int remainTerminal = 29;
    private int terminals = 29;

    private int /*currentStageReturn, */currentStage;
    private int currentWeakness = 0;
    private int stopTick = 0;
    private Vec3 stage2LastPosition;

    private boolean isNecronUsingUltimateSkill;

    @Override
    public int getStage() {
        return currentStage;
    }

    @Override
    public int getMaxStage() {
        return 4;
    }

    @Override
    public double getHealth() {
        // float health = f7Boss.getHealth();
        if(f7Boss == null) return 0;
        double scale = f7Boss.getProgress();
        return getMaxHealth() * (scale < 0.02 ? 0 : scale);
    }

    @Override
    public double getMaxHealth() {
        // maxHealth = Math.max(maxHealth, Math.max(f7Boss.getHealth(), f7Boss.getMaxHealth()));
        return HEALTHES[currentStage - 1];
    }

    @Override
    public boolean hasWeakness() {
        return currentStage <= 2;
    }

    @Override
    public int getWeakness() {
        return currentWeakness;
    }

    @Override
    public int getMaxWeakness() {
        return currentStage == 1 ? 2 : currentStage == 2 ? 1 : 0;
    }

    @Override
    public Identifier getHeadIcon() {
        return F7_BOSS_ICON;
    }

    @Override
    public boolean isImmune() {
        return (currentStage <= 2 && getWeakness() > 0) || (currentStage == 3 && remainTerminal > 0) || (currentStage == 4 && isNecronUsingUltimateSkill);
    }

    @Override
    public boolean isPowerUpAvaliable() {
        return currentStage == 3 || currentStage == 4;
    }

    @Override
    public boolean isPowerUp() {
        return (currentStage == 3 && remainTerminal > 0) || (currentStage == 4 && isNecronUsingUltimateSkill);
    }

    @Override
    public Identifier getPowerUpPotionIcon() {
        return Gui.getMobEffectSprite(currentStage == 4 ? MobEffects.STRENGTH : MobEffects.RESISTANCE);
    }

    @Override
    public int getPowerUp() {
        return remainTerminal;
    }

    @Override
    public CustomBossbar.PowerUpStyle getPowerUpStyle() {
        return CustomBossbar.PowerUpStyle.READY;
    }

    @Override
    public int getMaxPowerUp() {
        return terminals;
    }

    @Override
    public CustomBossbar.PowerUpTextState getPowerUpTextState() {
        return currentStage == 3 ? CustomBossbar.PowerUpTextState.NUMBER_WITH_MAX : CustomBossbar.PowerUpTextState.NONE;
    }

    @Override
    public boolean isBattleOver() {
        return currentStage == 4 && f7Boss == null;
    }

    @Override
    public void onBattleOver() {
        f7Boss = null;
        f7BossTarget = null;
        currentStage = 0;
    }

    @Override
    public LivingEntity getTargetEntity() {
        return f7BossTarget;
    }

    @Override
    public Component getDisplayName() {
        return NAMES[currentStage - 1];
    }

    @Override
    public void onStageEnter(int stage) {
        if(stage == 1) currentStage = 1;
        f7BossTarget = null;
        if(stage == 1) {
            currentWeakness = 2;
        }
        if(stage == 2) {
            currentWeakness = 1;
        }
        if(stage == 3) {
            remainTerminal = terminals = 29;
            visitedTerminalMsg.clear();
        }

        if(currentStage == 2) {
            addStarRailNotification("第二阶段", StarRailNotification.Type.warning);
        }
        if(currentStage == 3) {
            addStarRailNotification("第三阶段", StarRailNotification.Type.warning);
        }
        if(currentStage == 4) {
            addStarRailNotification("最终阶段", StarRailNotification.Type.warning);
        }
    }

    @Override
    public boolean shouldNotRenderOtherBoss(LivingEntity e) {
        boolean flag1 = e instanceof WitherBoss || e instanceof EnderDragon;
        if(flag1) return true;
        boolean flag2 = e.getHealth() <= 0;
        if(flag2) return true;
        return false;
    }

    @Override
    public boolean shouldXRayBoss() {
        return true;
    }

    private BlockPos simonSaysStartButton = new BlockPos(110, 121, 91);
    private int lastSimonSaysButtonCount;
    private int simonSaysButtonCount;
    private final Set<BlockPos> targetsimonSaysButton = new HashSet<>();
    private boolean isDoingSimonSays = false;

    private boolean enteredGoldorCoreTunnel;

    @Override
    public String getListenerName() {
        return "SRBDungeonF7Bossbar";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::onWorldUnload);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        CustomFabricEvents.MOUSE_BUTTON_EVENT.register(this::onMouseClick);
    }

    private boolean onMouseClick(long windowsHandle, MouseButtonInfo mouseButtonInfo, int pressState) {
        if(mouseButtonInfo.button() == 1 && pressState == 1 && mc.hitResult instanceof BlockHitResult blockHitResult && blockHitResult.getType() == HitResult.Type.BLOCK && blockHitResult.getBlockPos().equals(simonSaysStartButton)) {
            sendDungeonF7ChatMessage(ConfigManager.dungeonf7msgbotsimonsaysstart.getValue());
            targetsimonSaysButton.clear();
            isDoingSimonSays = true;
        }
        return false;
    }

    private void onClientTick(Minecraft mc) {
        if(mc.player == null || mc.level == null || isInMasterDungeonFloor() || !isInCorrectDungeon()) return;

        MixinBossbarEventGetter bossbarEventGetter = (MixinBossbarEventGetter) mc.gui.getBossOverlay();
        boolean[] flag = new boolean[] {false};
        bossbarEventGetter.getEvents().forEach((uuid, bossEvent) -> {
            String name = ToolList.getInstance().deleteColorCode(bossEvent.getName().getString());
            int bossIndex = getBossIndex(name);
            if(bossIndex != -1) {
                if(bossEvent.getProgress() > 0.01) currentStage = bossIndex + 1;
                f7Boss = bossEvent;
                setCurrentStarRailBossBar(this);
                flag[0] = true;
            }
        });
        if(!flag[0]) {
            f7Boss = null;
        }

        for (Entity temp : mc.level.entitiesForRendering()) {
            LivingEntity living = temp.asLivingEntity();
            E2AMappingListener.MobInfo mobInfo = e2AMappingListener.getMobInfo(living);
            if(temp instanceof WitherBoss) {
                if(f7BossTarget != temp) {
                    if(!temp.isInvisible() && Math.max(ToolList.getInstance().isDevEnvironment() ? getBossIndex(temp.getName().getString()) : -1, Optional.ofNullable(mobInfo).map(b -> b.armorStand).map(info -> getBossIndex(info.getName().getString())).orElse(-1)) >= currentStage - 1) {
                        f7BossTarget = (WitherBoss) temp;
                    } else {
                        f7BossTarget = null;
                    }
                }
            }
        }

        if (f7BossTarget != null) {
            if(stage1LaserFlag && (f7BossTarget.distanceToSqr(73, 227, 73) < 5 * 5) && currentStage == 1) {
                stage1LaserFlag = false;
                currentWeakness = 0;
            }
            if(currentStage == 2) {
                Vec3 position = f7BossTarget.position();
                if(position.equals(stage2LastPosition)) {
                    stopTick = 0;
                    stormThunderFlagTime = 0;
                } else {
                    stopTick++;
                }
                stage2LastPosition = f7BossTarget.position();

                if(stopTick >= 5) {
                    if(f7BossTarget.distanceToSqr(73, 178, 54) <= 4 * 4) {
                        if(System.currentTimeMillis() - stormThunderFlagTime >= 1000) {
                            stormThunderFlagTime = System.currentTimeMillis();
                            addStarRailNotification("Storm正在准备释放致命攻击, 站在完整的柱子下以避免死亡!", StarRailNotification.Type.warning);
                        }
                    }
                }
            }
            if(currentStage == 4) {
                if (mc.player.hasEffect(MobEffects.BLINDNESS)) {
                    isNecronUsingUltimateSkill = true;
                }
            }
        }

        if(currentStage == 3) {
            lastSimonSaysButtonCount = simonSaysButtonCount;
            int temp = 0;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(110, 123, 92), new BlockPos(110, 120, 95))) {
                if (mc.level.getBlockState(pos).getBlock().equals(Blocks.STONE_BUTTON)) temp++;
            }
            boolean hasLantern = false;
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(111, 123, 92), new BlockPos(111, 120, 95))) {
                if (mc.level.getBlockState(pos).getBlock().equals(Blocks.SEA_LANTERN)) {
                    targetsimonSaysButton.add(pos.immutable());
                    hasLantern = true;
                }
            }
            simonSaysButtonCount = temp;

            if (isDoingSimonSays && simonSaysButtonCount != 16 && lastSimonSaysButtonCount == 16 && (hasLantern || targetsimonSaysButton.size() == 5)) {
                sendDungeonF7ChatMessage(ConfigManager.dungeonf7msgbotsimonsays[Mth.clamp(targetsimonSaysButton.size() - 1, 0, 4)].getValue());
                if(targetsimonSaysButton.size() == 5) {
                    isDoingSimonSays = false;
                }
            }
            boolean inArea = ToolList.getInstance().isEntityInArea(mc.player, new BlockPos(50, 115, 56), new BlockPos(58, 122, 57));
            if (inArea && !enteredGoldorCoreTunnel) {
                enteredGoldorCoreTunnel = true;
                sendDungeonF7ChatMessage(ConfigManager.dungeonf7msgbotcoretunnel.getValue());
            } else if(!inArea && enteredGoldorCoreTunnel) {
                enteredGoldorCoreTunnel = false;
            }
        }

    }

    private void onWorldUnload(Minecraft mc, ClientLevel level) {
        f7Boss = null;
        f7BossTarget = null;
        currentStage = 0;
        visitedTerminalMsg.clear();
        isDoingSimonSays = false;
        targetsimonSaysButton.clear();
    }

    private boolean stage1LaserFlag = false;
    private final Set<String> visitedTerminalMsg = new HashSet<>();

    private void onChat(Component component, boolean b) {
        String message = ToolList.getInstance().deleteColorCode(component.getString());
        if(currentStage == 1) {
            if (message.equals("1/2 Energy Crystals are now active!")) {
                if(currentWeakness > 1) currentWeakness = 1;
            }
            if (message.equals("The Energy Laser is charging up!")) {
                stage1LaserFlag = true;
            }
        }
        if(currentStage == 2) {
            if(message.equals("[BOSS] Storm: Oof") || message.equals("[BOSS] Storm: Ouch, that hurt!")) {
                currentWeakness = 0;
            }
        }
        if(currentStage == 3) {
            if (!message.contains(">") && (message.contains("activated a terminal") || message.contains("activated a lever") || message.contains("completed a device"))) {
                if(!visitedTerminalMsg.contains(message)) {
                    visitedTerminalMsg.add(message);
                    remainTerminal--;
                }
            }
            if (message.contains("(7/7)") || message.contains("(8/8)")) visitedTerminalMsg.clear();
        }
        if(currentStage == 4) {
            if(message.equals("[BOSS] Necron: ARGH!")) {
                isNecronUsingUltimateSkill = false;
            }
        }
    }

    private long stormThunderFlagTime = 0;

    private boolean onPacket(Packet<PacketListener> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        Map.Entry<String, StarRailNotification.Type> turnItToStarRailMsg = null;
        boolean cancelFlag = true;

        Component ic = ToolList.getInstance().tryGetTitleFromPacket(packet);
        if(ic == null) return false;
        String message = ToolList.getInstance().deleteColorCode(ic.getString());
        if(message.contains("enraged")) {
            turnItToStarRailMsg = new AbstractMap.SimpleEntry<>(message, StarRailNotification.Type.warning);
            currentWeakness = getMaxWeakness();
        }
        if(
                message.contains("Energy") ||
                        message.contains("activated a terminal") || message.contains("activated a lever") || message.contains("completed a device") || message.contains("The gate") || message.contains("The Core entrance")
        ) {
            turnItToStarRailMsg = new AbstractMap.SimpleEntry<>(message, StarRailNotification.Type.success);
        }
        if(currentStage == 2) {
            if(message.trim().matches("[2-7]")) {
                if(System.currentTimeMillis() - stormThunderFlagTime >= 1000) {
                    stormThunderFlagTime = System.currentTimeMillis();
                    turnItToStarRailMsg = new AbstractMap.SimpleEntry<>("Storm正在准备释放致命攻击, 站在完整的柱子下以避免死亡!", StarRailNotification.Type.warning);
                    cancelFlag = false;
                }
            } else if(message.trim().equals("1")) {
                stormThunderFlagTime = 0;
            }
        }

        if(turnItToStarRailMsg != null) {
            if (addStarRailNotification(turnItToStarRailMsg.getKey(), turnItToStarRailMsg.getValue())) {
                return cancelFlag;
            }
        }

        return false;
    }

    private int getBossIndex(String bossName) {
        for (int i = 0; i < NAMES.length; i++) {
            if (bossName.contains(NAMES[i].getString())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getFloor() {
        return 7;
    }

}
