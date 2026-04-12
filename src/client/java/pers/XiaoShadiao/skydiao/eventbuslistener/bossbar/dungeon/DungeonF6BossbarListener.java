package pers.XiaoShadiao.skydiao.eventbuslistener.bossbar.dungeon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.hud.CustomBossbar;
import pers.XiaoShadiao.skydiao.hud.StarRailNotification;
import pers.XiaoShadiao.skydiao.mixin.client.MixinBossbarEventGetter;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.awt.*;
import java.util.*;
import java.util.List;

public class DungeonF6BossbarListener extends AbstractDungeonBossbar {

    public static final Identifier LIVID_ICON = Objects.requireNonNull(Identifier.tryBuild("skydiao", "textures/skyblock/boss/sadan.png"));
    public static final Component NAME = Component.literal("Sadan");
    public static final double HEALTH = 40_000_000;
    public static final double MM_HEALTH = 800_000_000;

    public static final double GIANT_HEALTH = 25_000_000;
    public static final double MM_GIANT_HEALTH = 600_000_000;

    private LerpingBossEvent bossEvent;
    private Giant bossEntity;

    private final Set<Giant> giants = new HashSet<>();

    private int stage = 1;

    @Override
    public int getFloor() {
        return 6;
    }

    @Override
    public String getListenerName() {
        return "DungeonF6BossbarListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
    }

    private void onChat(Component component, boolean b) {
        String message = ToolList.getInstance().deleteColorCode(component.getString());
        if(message.equals("[BOSS] Sadan: I am the bridge between this realm and the world below! You shall not pass!")) {
            addStarRailNotification("持续攻击Sadan的Terracotta造物以激怒Sadan!", StarRailNotification.Type.warning);
        }
    }

    private void onClientTick(Minecraft mc) {
        if (mc.level == null || !isInCorrectDungeon()) return;

        MixinBossbarEventGetter bossbarEventGetter = (MixinBossbarEventGetter) mc.gui.getBossOverlay();

        bossbarEventGetter.getEvents().forEach((uuid, bossEvent) -> {
            String name = ToolList.getInstance().deleteColorCode(bossEvent.getName().getString());

            switch (name) {
                case "Sadan's Interest Level" -> {
                    this.bossEvent = bossEvent;
                    setCurrentStarRailBossBar(this);
                    stage = 1;
                }
                case "Sadan's Giants" -> {
                    this.bossEvent = bossEvent;
                    setCurrentStarRailBossBar(this);
                    stage = 2;
                }
                case "Sadan" -> {
                    this.bossEvent = bossEvent;
                    setCurrentStarRailBossBar(this);
                    stage = 3;
                }
            }
        });

        giants.removeIf(giant -> !ToolList.getInstance().isEntityOnWorld(giant));
        for (Entity temp : mc.level.entitiesForRendering()) {
            if(temp instanceof Giant) {
                if(temp.getY() >= 67 && !temp.isInvisible()) {
                    giants.add((Giant) temp);
                }
                if(stage == 3) {
                    if(temp.getY() >= 67 && temp.getY() <= 73 && !temp.isInvisible() && ((Giant) temp).getHealth() > (isInMasterDungeonFloor() ? MM_GIANT_HEALTH : GIANT_HEALTH)) {
                        bossEntity = (Giant) temp;
                    }
                } else {
                    bossEntity = null;
                }
            }
        }
    }

    @Override
    public int getStage() {
        return 1;
    }

    @Override
    public int getMaxStage() {
        return 1;
    }

    @Override
    public double getHealth() {
        return Math.min(bossEntity == null ? getMaxHealth() : bossEntity.getHealth() < 50 ? 0 : bossEntity.getHealth(), getMaxHealth());
    }

    @Override
    public double getMaxHealth() {
        return isInMasterDungeonFloor() ? MM_HEALTH : HEALTH;
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
        return LIVID_ICON;
    }

    @Override
    public boolean isImmune() {
        return stage != 3;
    }

    @Override
    public boolean isPowerUpAvaliable() {
        return stage != 3;
    }

    @Override
    public boolean isPowerUp() {
        return false;
    }

    @Override
    public Identifier getPowerUpPotionIcon() {
        return Gui.getMobEffectSprite(switch(stage) {
            case 1 -> MobEffects.NIGHT_VISION;
            default -> MobEffects.STRENGTH;
        });
    }

    private double getGiantProgress() {
        double progress = 0;
        for(Giant giant : giants) {
            progress += giant.getHealth() / giant.getMaxHealth();
        }
        return progress / 4;
    }

    @Override
    public int getPowerUp() {
        int max = getMaxPowerUp();
        if(stage == 1) {
            return (int) (max * bossEvent.getProgress());
        } else {
            return (int) (max * getGiantProgress());
        }
    }

    @Override
    public CustomBossbar.PowerUpStyle getPowerUpStyle() {
        return CustomBossbar.PowerUpStyle.CHARGING;
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
        return null;
    }

    @Override
    public Component getDisplayName() {
        return NAME;
    }

    @Override
    public void onStageEnter(int stage) {
        if(stage == 1) {
            bossEntity = null;
            giants.clear();
        }
    }

    @Override
    public boolean shouldNotRenderOtherBoss(LivingEntity e) {
        return e instanceof RemotePlayer;
    }

    @Override
    public boolean shouldXRayBoss() {
        return false;
    }

}
