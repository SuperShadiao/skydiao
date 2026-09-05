package pers.XiaoShadiao.skydiao.eventbuslistener.bossbar.dungeon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
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
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
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
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

public class DungeonF7BossbarListener extends AbstractDungeonBossbar {

    public static final Identifier F7_BOSS_ICON = Objects.requireNonNull(Identifier.tryBuild("skydiao", "textures/skyblock/boss/wither.png"));
    public static final Component[] NAMES = new Component[]{Component.literal("Maxor"), Component.literal("Storm"), Component.literal("Goldor"), Component.literal("Necron"), Component.literal("Wither King")};
    public static final double[] HEALTHES = new double[]{100_000_000, 400_000_000, 750_000_000, 1_000_000_000, 5_000_000_000d};
    public static final double[] MM_HEALTHES = new double[]{800_000_000, 1_000_000_000, 1_200_000_000, 1_400_000_000, 5_000_000_000d};

    private LerpingBossEvent f7Boss;
    private WitherBoss f7BossTarget;

    private int remainTerminal = 29;
    private int terminals = 29;

    private int /*currentStageReturn, */currentStage;
    private int currentWeakness = 0;
    private int stopTick = 0;
    private Vec3 stage2LastPosition;

    private boolean stage3LeapTip;

    private boolean stage4PlatformHasBlock;
    private boolean stage4PlatformTip;
    //  53 64 113 56 64 116
    private final BlockPos stage4PlatformCorn1 = new BlockPos(53, 64, 113);
    private final BlockPos stage4PlatformCorn2 = new BlockPos(56 - 1, 64 - 1, 116 - 1);

    private final BlockPos stage3LeapPositionCorn1 = new BlockPos(110, 125, 88);
    private final BlockPos stage3LeapPositionCorn2 = new BlockPos(102, 120, 99);

    private boolean isNecronUsingUltimateSkill;

    private int stage5DragonCount = 0;

    private record WitherDragonData(int entityId, ColorType colorType) {
        private enum ColorType {
            GREEN(new BlockPos(26, 6, 94), new BlockPos(32, 23, 94), Color.GREEN),
            RED(new BlockPos(26, 6, 59), new BlockPos(32, 22, 59), Color.RED),
            ORANGE(new BlockPos(86, 6, 56), new BlockPos(80, 23, 56), Color.ORANGE),
            BLUE(new BlockPos(85, 6, 94), new BlockPos(79, 23, 94), Color.BLUE),
            PURPLE(new BlockPos(56, 8, 126), new BlockPos(56, 22, 120), Color.MAGENTA),
            ;
            private final BlockPos spawnPos;
            private final BlockPos deathDetection;
            private final Color color;
            ColorType(BlockPos pos, BlockPos deathDetection, Color color) {
                this.spawnPos = pos;
                this.deathDetection = deathDetection;
                this.color = color;
            }

            private double horizontalSpawnDistanceSqrToEntity(Entity entity) {
                return entity.position().horizontal().distanceToSqr(spawnPos.getX() + 0.5, 0, spawnPos.getZ() + 0.5);
            }

            private boolean isDragonInSpawnArea(EnderDragon dragon) {
                return horizontalSpawnDistanceSqrToEntity(dragon) < 100;
            }

            private boolean isDragonNearbySpawnArea(EnderDragon dragon) {
                return horizontalSpawnDistanceSqrToEntity(dragon) < 784;
            }

            private boolean isDragonDead() {
                return mc.level != null && mc.level.getBlockState(deathDetection).isAir();
            }

            public static ColorType guessColor(EnderDragon dragon) {
                return Stream.of(values()).min(Comparator.comparingDouble(type -> type.horizontalSpawnDistanceSqrToEntity(dragon))).orElseThrow(AssertionError::new);
            }
        }
    }
    private final List<WitherDragonData> witherDragons = new ArrayList<>();

    @Override
    public int getStage() {
        return currentStage;
    }

    @Override
    public int getMaxStage() {
        return masterFloorFlag ? 5 : 4;
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
        return (masterFloorFlag ? MM_HEALTHES : HEALTHES)[currentStage - 1];
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
        return (currentStage <= 2 && getWeakness() > 0) || (currentStage == 3 && remainTerminal > 0) || (currentStage == 4 && isNecronUsingUltimateSkill) || (currentStage == 5 && stage5DragonCount == 5);
    }

    @Override
    public boolean isPowerUpAvaliable() {
        return currentStage >= 3;
    }

    @Override
    public boolean isPowerUp() {
        return (currentStage == 3 && remainTerminal > 0) ||
                (currentStage == 4 && isNecronUsingUltimateSkill) ||
                (currentStage == 5 && stage5DragonCount == 5);
    }

    @Override
    public Identifier getPowerUpPotionIcon() {
        return Gui.getMobEffectSprite(currentStage >= 4 ? MobEffects.STRENGTH : MobEffects.RESISTANCE);
    }

    @Override
    public int getPowerUp() {
        if(currentStage == 5) return stage5DragonCount;
        return remainTerminal;
    }

    @Override
    public CustomBossbar.PowerUpStyle getPowerUpStyle() {
        return currentStage == 5 && stage5DragonCount < 5 ? CustomBossbar.PowerUpStyle.CHARGING : CustomBossbar.PowerUpStyle.READY;
    }

    @Override
    public int getMaxPowerUp() {
        if(currentStage == 5) return 5;
        return terminals;
    }

    @Override
    public CustomBossbar.PowerUpTextState getPowerUpTextState() {
        return currentStage == 3 || currentStage == 5 ? CustomBossbar.PowerUpTextState.NUMBER_WITH_MAX : CustomBossbar.PowerUpTextState.NONE;
    }

    @Override
    public boolean isBattleOver() {
        if(masterFloorFlag) return false;
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
            stage3BreakGateFlag = true;
            ToolList.addThreadedTask(() -> {
                Thread.sleep(2000);
                addStarRailNotification("通过完成所有Terminal, Device和拉杆来解除Goldor的免疫状态!", StarRailNotification.Type.warning);
                Thread.sleep(3000);
                addStarRailNotification("靠近Goldor将受到大量伤害, 通过攻击Goldor可延缓其移动速度!", StarRailNotification.Type.warning);
                return null;
            });
        }

        if(currentStage == 2) {
            addStarRailNotification("第二阶段", StarRailNotification.Type.warning);
        }
        if(currentStage == 3) {
            addStarRailNotification("第三阶段", StarRailNotification.Type.warning);
        }
        if(currentStage == 4) {
            addStarRailNotification(masterFloorFlag ? "第四阶段" : "最终阶段", StarRailNotification.Type.warning);
        }
        if(currentStage == 5) {
            addStarRailNotification("最终阶段", StarRailNotification.Type.warning);
            ToolList.addThreadedTask(() -> {
                Thread.sleep(8000);
                addStarRailNotification("在对应凋零龙生成点击杀对应凋零龙即可削减凋零王生命值!", StarRailNotification.Type.warning);
                return null;
            });
        }
    }

    @Override
    public boolean shouldNotRenderOtherBoss(LivingEntity e) {
        boolean flag1 = e instanceof WitherBoss;
        if(flag1) return true;
        boolean flag2 = e.getHealth() <= 0;
        if(flag2) return true;
        return false;
    }

    @Override
    public boolean shouldXRayBoss() {
        return currentStage != 5;
    }

    private final BlockPos simonSaysStartButton = new BlockPos(110, 121, 91);
    private int lastSimonSaysButtonCount;
    private int simonSaysButtonCount;
    private final List<BlockPos> targetsimonSaysButton = new ArrayList<>();
    private boolean isDoingSimonSays = false;

    private boolean enteredGoldorCoreTunnel;

    private boolean passWatcherFlag;

    private boolean masterFloorFlag;

    private boolean stage3BreakGateFlag;
    private boolean stage3InGateCD;

    @Override
    public String getListenerName() {
        return "SRBDungeonF7Bossbar";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onWorldUnload);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        CustomFabricEvents.MOUSE_BUTTON_EVENT.register(this::onMouseClick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((a,b) -> {
            passWatcherFlag = false;
            masterFloorFlag = false;
            witherDragons.clear();
        });
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(mc.level == null || (!masterFloorFlag && !isInCorrectDungeon())) return;

        RenderUtils.WorldRender wr1 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wr2 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_FILL);
        boolean flag = false;
        for (BlockPos pos : BlockPos.betweenClosed(stage4PlatformCorn1, stage4PlatformCorn2)) {
            if(mc.level.getBlockState(pos).getBlock() != Blocks.AIR) {
                RenderUtils.renderESP(wr2, pos, 200 / 255f, 0, 1f, 1f, true);
                RenderUtils.renderESP(wr1, pos, 200 / 255f, 0, 1f, 1f, false);
                flag = true;
            }
        }
        if(currentStage == 2 && ConfigManager.f7DrawStormFireballTarget.getValue()) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if(!(entity instanceof Fireball fireball)) continue;

                Vec3 fireballEyePos = fireball.getEyePosition();
                HitResult hitResult = ToolList.getInstance().predictPlayerAimBlock(fireballEyePos, fireballEyePos.add(fireball.getDeltaMovement().multiply(70, 70, 70)));
                if(hitResult.getType() == HitResult.Type.BLOCK && hitResult instanceof BlockHitResult blockHitResult) {
                    BlockPos target = blockHitResult.getBlockPos();
                    Vec3 location = blockHitResult.getLocation();
                    for (BlockPos pos : BlockPos.betweenClosed(target.offset(-4, -4, -4), target.offset(4, 4, 4))) {
                        if(!mc.level.getBlockState(pos).isAir()) {
                            RenderUtils.renderESP(wr2, pos, 1, 0, 0f, 1f, true);
                            RenderUtils.renderESP(wr1, pos, 1, 0, 0f, 1f, false);
                        }
                    }
                    RenderUtils.renderWorldLine(wr1, fireballEyePos, location, 1, 0, 0, 1);
                }
            }
        }
        if(ConfigManager.f7DrawWitherDragonESP.getValue()) {
            Iterator<WitherDragonData> it = witherDragons.iterator();
            while(it.hasNext()) {
                WitherDragonData data = it.next();
                Entity entity = mc.level.getEntity(data.entityId);
                if (!(entity instanceof EnderDragon enderDragon) || data.colorType.isDragonDead()) continue;
                if (enderDragon.getHealth() <= 0) {
                    it.remove();
                    continue;
                }
                if (data.colorType.isDragonNearbySpawnArea(enderDragon)) {
                    RenderUtils.renderESP(wr1, enderDragon, data.colorType.color.getRed() / 255f, data.colorType.color.getGreen() / 255f, data.colorType.color.getBlue() / 255f, 1f, false);
                    if (data.colorType.isDragonInSpawnArea(enderDragon)) {
                        RenderUtils.renderESP(wr2, enderDragon, data.colorType.color.getRed() / 255f, data.colorType.color.getGreen() / 255f, data.colorType.color.getBlue() / 255f, 1f, true);
                    }
                    RenderUtils.renderTrace(wr1, enderDragon, data.colorType.color.getRed() / 255f, data.colorType.color.getGreen() / 255f, data.colorType.color.getBlue() / 255f, 1f);
                } else if (enderDragon.getHealth() / enderDragon.getMaxHealth() > 0.2) {
                    RenderUtils.renderESP(wr1, enderDragon, 1, 1, 1, 1f, false);
                }
            }
        }
        wr2.finishDraw();
        wr1.finishDraw();
        stage4PlatformHasBlock = flag;
    }

    private boolean onMouseClick(long windowsHandle, MouseButtonInfo mouseButtonInfo, int pressState) {
        if(mc.player == null || mc.level == null /*|| isInMasterDungeonFloor() */|| (!passWatcherFlag && !isInCorrectDungeon())) return false;
        if(mouseButtonInfo.button() == 1 && pressState == 1 && mc.hitResult instanceof BlockHitResult blockHitResult && blockHitResult.getType() == HitResult.Type.BLOCK && blockHitResult.getBlockPos().equals(simonSaysStartButton)) {
            sendDungeonF7ChatMessage(ConfigManager.dungeonf7msgbotsimonsaysstart.getValue());
            targetsimonSaysButton.clear();
            isDoingSimonSays = true;
        }
        return false;
    }

    private void onClientTick(Minecraft mc) {
        if(mc.player == null || mc.level == null || /*isInMasterDungeonFloor() || */(!passWatcherFlag && !isInCorrectDungeon())) return;

        masterFloorFlag |= isInMasterDungeonFloor();

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
            if(stormThunderAfterTipTick > 0) {
                stormThunderAfterTipTick--;
                if(stormThunderAfterTipTick == 1) {
                    if(currentStage == 2) {
                        addStarRailNotification("通过踩下对应压板使柱子下落压中Storm使其眩晕!", StarRailNotification.Type.warning);
                    }
                }
            }
            if(currentStage == 2) {
                Vec3 position = f7BossTarget.position();
                if(!position.equals(stage2LastPosition)) {
                    stopTick = 0;
                    stormThunderFlagTime = 0;
                } else {
                    stopTick++;
                }
                stage2LastPosition = f7BossTarget.position();

                if(stopTick >= 5) {
                    if(f7BossTarget.distanceToSqr(72, 179, 53) <= 5 * 5) {
                        if(System.currentTimeMillis() - stormThunderFlagTime >= 1500) {
                            stormThunderFlagTime = System.currentTimeMillis();
                            stormThunderAfterTipTick = 50;
                            addStarRailNotification("Storm正在准备释放致命落雷, 站在完整的柱子下以避免死亡!", StarRailNotification.Type.warning);
                        }
                    }
                }
            }
            if(currentStage == 3) {
                if(getHealth() == 0 && !stage4PlatformTip) {
                    stage4PlatformTip = true;
                    if(stage4PlatformHasBlock) addStarRailNotification("在第四阶段开始前通过用Dungeonbreaker破坏9个被紫色标注的方块, 来防止平台坍塌!", StarRailNotification.Type.success);
                }
            } else {
                stage4PlatformTip = false;
            }
        }
        if (currentStage == 4) {
            if (mc.player.hasEffect(MobEffects.BLINDNESS)) {
                isNecronUsingUltimateSkill = true;
            }
        }

        if(currentStage == 2) {
            if(getHealth() == 0 && !stage3LeapTip) {
                if(mc.player.onGround() && ToolList.getInstance().isEntityInArea(mc.player, stage3LeapPositionCorn1, stage3LeapPositionCorn2)) {
                    stage3LeapTip = true;
                    if(!ConfigManager.dungeonf7msgbotssleap.getValue().isEmpty()) sendDungeonF7ChatMessage(ConfigManager.dungeonf7msgbotssleap.getValue() + " (SS Leap)");
                }
            }
        } else {
            stage3LeapTip = false;
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
                    if(!targetsimonSaysButton.contains(pos)) targetsimonSaysButton.add(pos.immutable());
                    hasLantern = true;
                }
            }
            simonSaysButtonCount = temp;

            if (isDoingSimonSays && simonSaysButtonCount != 16 && lastSimonSaysButtonCount == 16 && (hasLantern || targetsimonSaysButton.size() >= 4)) {
                sendDungeonF7ChatMessage(ConfigManager.dungeonf7msgbotsimonsays[Mth.clamp(targetsimonSaysButton.size() - 1, 0, 4)].getValue());
                if(targetsimonSaysButton.size() >= 4) {
                    isDoingSimonSays = false;
                }
                targetsimonSaysButton.clear();
            }
            boolean inArea = ToolList.getInstance().isEntityInArea(mc.player, new BlockPos(50, 115, 54), new BlockPos(58, 122, 57));
            if (inArea && !enteredGoldorCoreTunnel) {
                enteredGoldorCoreTunnel = true;
                sendDungeonF7ChatMessage(ConfigManager.dungeonf7msgbotcoretunnel.getValue());
            } else if(!inArea && enteredGoldorCoreTunnel) {
                enteredGoldorCoreTunnel = false;
            }
        }
        if(currentStage == 5) {
            int tempCount = 0;
            for (String s : ToolList.getInstance().fetchScoreboardLinesNoColor()) {
                if(s.contains("Dragon") && !s.contains("Dragons")) tempCount++;
            }
            if(stage5DragonCount != tempCount) {
                if(tempCount == 5) {
                    addStarRailNotification("五条凋零龙已生成! 请尽快击杀一条龙, 否则此次地牢将直接失败!", StarRailNotification.Type.warning);
                }
            }
            if(!(stage5DragonCount == 5 && tempCount == 0)) stage5DragonCount = tempCount;
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
        if (message.equals("[BOSS] The Watcher: You have proven yourself. You may pass.")) {
            passWatcherFlag = true;
        }
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
        if(message.contains(">") && message.contains("[SkyDiao]") && message.contains("(SS Leap)")) {
            stage3LeapTip = true;
        }

        if("[BOSS] Maxor: DON'T DISAPPOINT ME, I HAVEN'T HAD A GOOD FIGHT IN A WHILE.".equals(message)) {
            addStarRailNotification("通过反复获取两个能量水晶并分别置于两个充能台上激活激光!", StarRailNotification.Type.warning);
        }
        if((mc.getUser().getName() + " picked up an Energy Crystal!").equals(message)) {
            addStarRailNotification("你获取了一个水晶! 将其放置在充能台上!", StarRailNotification.Type.success);
        }
    }

    private long stormThunderFlagTime = 0;
    private int stormThunderAfterTipTick = 0;

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if(mc.level == null || !StatusManager.get().isInDungeon()) {
            return false;
        }
        if(packet instanceof ClientboundAddEntityPacket addEntityPacket) {
            if (addEntityPacket.getType() == EntityType.ENDER_DRAGON) {
                delayTickExecutor.delayExec(() -> {
                    int id = addEntityPacket.getId();
                    for (WitherDragonData witherDragon : witherDragons) {
                        if(witherDragon.entityId == id) {
                            return;
                        }
                    }
                    Entity entity = mc.level.getEntity(id);
                    if(entity instanceof EnderDragon dragon) {
                        WitherDragonData.ColorType colorType = WitherDragonData.ColorType.guessColor(dragon);
                        witherDragons.removeIf(data -> data.colorType == colorType);
                        witherDragons.add(new WitherDragonData(id, colorType));
                    }
                }, 2);
            }
        }

        Map.Entry<String, StarRailNotification.Type> turnItToStarRailMsg = null;
        boolean cancelFlag = true;

        Component ic = ToolList.getInstance().tryGetTitleFromPacket(packet);
        if(ic == null) return false;
        String message = ToolList.getInstance().deleteColorCode(ic.getString());
        if(message.contains("enraged")) {
            turnItToStarRailMsg = new AbstractMap.SimpleEntry<>(message, StarRailNotification.Type.warning);
            currentWeakness = getMaxWeakness();
            if(CrowdinI18nManager.getCurrentLang() == CrowdinI18nManager.LangCode.chinese) {
                if (message.contains("Maxor")) {
                    turnItToStarRailMsg = new AbstractMap.SimpleEntry<>("Maxor陷入了狂暴, 其附近所有玩家将受到大量伤害!", StarRailNotification.Type.warning);
                } else if (message.contains("Storm")) {
                    turnItToStarRailMsg = new AbstractMap.SimpleEntry<>("Storm陷入了狂暴, 其附近所有玩家将受到大量伤害!", StarRailNotification.Type.warning);
                }
            }
        }
        if(
                message.contains("Energy") ||
                        message.contains("activated a terminal") || message.contains("activated a lever") || message.contains("completed a device") || message.contains("The gate") || message.contains("The Core entrance")
        ) {
            turnItToStarRailMsg = new AbstractMap.SimpleEntry<>(message, StarRailNotification.Type.success);
            if(CrowdinI18nManager.getCurrentLang() == CrowdinI18nManager.LangCode.chinese) {
                if(message.contains("1/2 Energy Crystals are now active!")) {
                    turnItToStarRailMsg = new AbstractMap.SimpleEntry<>("一个水晶已激活!", StarRailNotification.Type.success);
                } else if(message.contains("2/2 Energy Crystals are now active!")) {
                    turnItToStarRailMsg = new AbstractMap.SimpleEntry<>("两个水晶已激活!", StarRailNotification.Type.success);
                } else if(message.contains("The Energy Laser is charging up!")) {
                    turnItToStarRailMsg = new AbstractMap.SimpleEntry<>("激光已激活, 将Maxor牵引至红色信标柱处使其眩晕!", StarRailNotification.Type.success);
                } else if(message.contains("The Core entrance")) {
                    turnItToStarRailMsg = new AbstractMap.SimpleEntry<>("Goldor的金色核心入口已开启, 进入以解除Goldor的免疫状态!", StarRailNotification.Type.success);
                } else if(message.contains("The gate will open in 5 seconds!")) {
                    if(stage3BreakGateFlag) {
                        turnItToStarRailMsg = new AbstractMap.SimpleEntry<>("门将在5秒后开启, 可使用Superboom TNT或Archer的爆炸箭技能提前炸开...", StarRailNotification.Type.success);
                    } else {
                        turnItToStarRailMsg = new AbstractMap.SimpleEntry<>("门将在5秒后开启!", StarRailNotification.Type.success);
                    }
                    stage3BreakGateFlag = false;
                    stage3InGateCD = true;
                } else if(message.contains("The gate has been destroyed!")) {
                    if(stage3InGateCD) {
                        turnItToStarRailMsg = new AbstractMap.SimpleEntry<>("通往下一个区域的门已开启!", StarRailNotification.Type.success);
                    } else {
                        turnItToStarRailMsg = new AbstractMap.SimpleEntry<>("通往下一个区域的门已被摧毁, 完成当前区域所有终端来开启大门!", StarRailNotification.Type.success);
                    }
                    stage3BreakGateFlag = false;
                    stage3InGateCD = false;
                }
            }
        }
        if(message.contains("The Core entrance")) {
            ToolList.addThreadedTask(() -> {
                Thread.sleep(100);
                remainTerminal = 0;
                return null;
            });
        }
        if(currentStage == 2) {
            if(message.trim().matches("[2-7]")) {
                if(System.currentTimeMillis() - stormThunderFlagTime >= 1000) {
                    stormThunderFlagTime = System.currentTimeMillis();
                    stormThunderAfterTipTick = 50;
                    turnItToStarRailMsg = new AbstractMap.SimpleEntry<>("Storm正在准备释放致命落雷, 站在完整的柱子下以避免死亡!", StarRailNotification.Type.warning);
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
            if (ToolList.getInstance().deleteColorCode(bossName).contains(NAMES[i].getString())) {
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
