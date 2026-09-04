package pers.XiaoShadiao.skydiao.eventbuslistener.bossbar.dungeon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.core.BlockPos;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.hud.CustomBossbar;
import pers.XiaoShadiao.skydiao.hud.StarRailNotification;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DungeonF4BossbarListener extends AbstractDungeonBossbar {

    public static final Identifier SCARF_ICON = Objects.requireNonNull(Identifier.tryBuild("skydiao", "textures/skyblock/boss/thorn.png"));
    public static final Component NAME = Component.literal("Thorn");
    public static final double HEALTH = 4;
    public static final double MM_HEALTH = 6;

    private Ghast bossEntity;

    private double bearCharging;

    @Override
    public int getFloor() {
        return 4;
    }

    @Override
    public String getListenerName() {
        return "DungeonF4BossbarListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
    }

    private void onChat(Component component, boolean b) {
        String msg = ToolList.getInstance().deleteColorCode(component.getString());
        if ("[BOSS] Thorn: Today you'll be our spectacle.".equals(msg)) {
            addStarRailNotification("持续击杀Spirit造物来增加灵魂熊的生成进度!", StarRailNotification.Type.warning);
        }
    }

    private void onLastRender(LevelRenderContext context) {
        if (mc.level == null || !isInCorrectDungeon()) return;
        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if(entity instanceof ArmorStand) {
                if(ToolList.getInstance().deleteColorCode(entity.getName().getString()).contains("Spirit Bow")) {
                    RenderUtils.renderESP(wr, entity, 1, 0, 1, 1, false);
                    RenderUtils.renderTrace(wr, entity, 1, 0, 1, 1);
                }
            }
        }
        wr.finishDraw();
    }

    private void onClientTick(Minecraft mc) {
        if (mc.level == null || !isInCorrectDungeon()) return;

        for (Entity temp : mc.level.entitiesForRendering()) {
            LivingEntity living = temp.asLivingEntity();
            E2AMappingListener.MobInfo mobInfo = e2AMappingListener.getMobInfo(living);
            if(temp instanceof Ghast) {
                if(mobInfo != null) {
                    String armorStandName = mobInfo.armorStand.getName().getString();
                    if(bossEntity != temp) {
                        if(temp.getName().getString().equals(NAME.getString()) || armorStandName.contains(NAME.getString())) {
                            bossEntity = (Ghast) temp;
                            setCurrentStarRailBossBar(this);
                            break;
                        }
                    }
                }
            }
        }

        /*
        34 77 7
        3 77 34
        -24 77  9
        1 77 -24
        */

        int mapBoundX1 = 34 + 1;
        int mapBoundX2 = -24 - 1;
        int mapBoundZ1 = 34 + 1;
        int mapBoundZ2 = -24 - 1;
        int y = 77;

        List<BlockPos> pos = new ArrayList<>();
        for (BlockPos blockPos : BlockPos.betweenClosed(mapBoundX2, y, mapBoundZ2, mapBoundX1, y, mapBoundZ1)) {
            Block block = mc.level.getBlockState(blockPos).getBlock();
            if(
                    mc.level.getBlockState(blockPos.east()).getBlock() != Blocks.AIR &&
                    mc.level.getBlockState(blockPos.west()).getBlock() != Blocks.AIR &&
                    mc.level.getBlockState(blockPos.north()).getBlock() != Blocks.AIR &&
                    mc.level.getBlockState(blockPos.south()).getBlock() != Blocks.AIR
            ) continue;
            if(block == Blocks.SEA_LANTERN) pos.add(blockPos.immutable());
        }
        if(pos.isEmpty()) {
            bearCharging = 0;
        } else {
            int centerX = (mapBoundX1 + mapBoundX2) / 2;
            int centerZ = (mapBoundZ1 + mapBoundZ2) / 2;

            // 圆从坐标 7 77 34 开始, bearCharging为"旋转"度数, 一半圈为0.5, 回到原点为1
            pos.stream().max(Comparator.comparingDouble(blockPos -> getProgess(blockPos, centerZ, centerX, false))).ifPresent(blockPos -> {
                double temp = getProgess(blockPos, centerZ, centerX, true);
                if(temp >= 1 && temp != bearCharging) {
                    addStarRailNotification("灵魂熊的生成进度已满, 击杀灵魂熊来获取Spirit Bow!", StarRailNotification.Type.tip);
                }
                bearCharging = temp;
            });
        }
    }

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        Map.Entry<String, StarRailNotification.Type> turnItToStarRailMsg = null;
        boolean cancelFlag = true;

        Component ic = ToolList.getInstance().tryGetTitleFromPacket(packet);
        if(ic == null) return false;
        String message = ToolList.getInstance().deleteColorCode(ic.getString());
        if(message.contains("Spirit Bow")) {
            turnItToStarRailMsg = new AbstractMap.SimpleEntry<>(message, message.contains("didn't") ? StarRailNotification.Type.warning : StarRailNotification.Type.success);
            if(CrowdinI18nManager.getCurrentLang() == CrowdinI18nManager.LangCode.chinese) {
                if("The Spirit Bow has dropped!".equals(message)) {
                    turnItToStarRailMsg = new AbstractMap.SimpleEntry<>("一把灵魂弓已掉落!", StarRailNotification.Type.success);
                } else {
                    Matcher matcher = Pattern.compile("(\\w+) picked up the Spirit Bow!").matcher(message);
                    if (matcher.find()) {
                        String playerName = matcher.group(1);
                        if(mc.getUser().getName().equals(playerName)) {
                            turnItToStarRailMsg = new AbstractMap.SimpleEntry<>("你捡起了灵魂弓! 通过物品栏第9格的灵魂弓射击Thorn来消减其生命值!", StarRailNotification.Type.success);
                        } else {
                            turnItToStarRailMsg = new AbstractMap.SimpleEntry<>(playerName + "捡起了灵魂弓!", StarRailNotification.Type.success);
                        }
                    } else {
                        matcher = Pattern.compile("(\\w+) didn't shoot the Spirit Bow fast enough!").matcher(message);
                        if (matcher.find()) {
                            String playerName = matcher.group(1);
                            if(mc.getUser().getName().equals(playerName)) {
                                turnItToStarRailMsg = new AbstractMap.SimpleEntry<>("由于你长时间未射击, 你手中的灵魂弓消失了!", StarRailNotification.Type.warning);
                            } else {
                                turnItToStarRailMsg = new AbstractMap.SimpleEntry<>(playerName + "由于长时间未射击, 其手中的灵魂弓消失了!", StarRailNotification.Type.warning);
                            }
                        }
                    }
                }
            }
        }

        if(turnItToStarRailMsg != null) {
            if (addStarRailNotification(turnItToStarRailMsg.getKey(), turnItToStarRailMsg.getValue())) {
                return cancelFlag;
            }
        }

        return false;
    }

    private static double getProgess(BlockPos blockPos, int centerZ, int centerX, boolean flag) {
        /*if(flag){
            System.out.println(blockPos);
            System.out.println("" + (Math.atan2(blockPos.getZ() - centerZ, blockPos.getX() - centerX) + Math.PI) / (Math.PI * 2));
        }*/
        double v = (Math.atan2(blockPos.getX() - centerX, -(blockPos.getZ() - centerZ)) + Math.PI) / (Math.PI * 2);
        if(v > 0.98) v = 1;
        return v;
    }

    @Override
    public int getStage() {
        return 1;
    }

    @Override
    public Color getPwoerUpColor() {
        return new Color(0, 175, 0);
    }

    @Override
    public int getMaxStage() {
        return 1;
    }

    @Override
    public double getHealth() {
        if(bossEntity == null) return 0;
        double maxHealth = getMaxHealth();
        float health = bossEntity.getHealth();
        double scale = (health < 10 ? 0 : health) / bossEntity.getMaxHealth();

        int ret = (int) Math.min(maxHealth, maxHealth * scale * (isInMasterDungeonFloor() ? 1.2 : 1.3333));
        if(scale > 0 && ret == 0) ret = 1;
        return ret;
    }

    @Override
    public double getMaxHealth() {
        return (int) (isInMasterDungeonFloor() ? MM_HEALTH : HEALTH);
    }

    @Override
    public boolean hasWeakness() {
        return false;
    }

    @Override
    public int getWeakness() {
        return 0;
    }

    @Override
    public int getMaxWeakness() {
        return 0;
    }

    @Override
    public Identifier getHeadIcon() {
        return SCARF_ICON;
    }

    @Override
    public boolean isImmune() {
        return false;
    }

    @Override
    public boolean isPowerUpAvaliable() {
        return true;
    }

    @Override
    public boolean isPowerUp() {
        return bearCharging >= 1;
    }

    @Override
    public Identifier getPowerUpPotionIcon() {
        return Gui.getMobEffectSprite(MobEffects.LUCK);
    }

    @Override
    public int getPowerUp() {
        return (int) (getMaxPowerUp() * bearCharging);
    }

    @Override
    public CustomBossbar.PowerUpStyle getPowerUpStyle() {
        return bearCharging >= 1 ? CustomBossbar.PowerUpStyle.READY : CustomBossbar.PowerUpStyle.CHARGING;
    }

    @Override
    public int getMaxPowerUp() {
        return 10000;
    }

    @Override
    public CustomBossbar.PowerUpTextState getPowerUpTextState() {
        return CustomBossbar.PowerUpTextState.PERCENT;
    }

    @Override
    public boolean isBattleOver() {
        return false;
    }

    @Override
    public void onBattleOver() {

    }

    @Override
    public LivingEntity getTargetEntity() {
        return bossEntity;
    }

    @Override
    public Component getDisplayName() {
        return NAME;
    }

    @Override
    public void onStageEnter(int stage) {
    }

    @Override
    public boolean shouldNotRenderOtherBoss(LivingEntity e) {
        return false;
    }

    @Override
    public boolean shouldXRayBoss() {
        return true;
    }
}
