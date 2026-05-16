package pers.XiaoShadiao.skydiao.eventbuslistener.bossbar.dungeon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.hud.CustomBossbar;
import pers.XiaoShadiao.skydiao.hud.StarRailNotification;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.Objects;

public class DungeonF1BossbarListener extends AbstractDungeonBossbar {

    public static final Identifier BONZO_ICON = Objects.requireNonNull(Identifier.tryBuild("skydiao", "textures/skyblock/boss/bonzo.png"));
    public static final Component NAME = Component.literal("Bonzo");
    public static final double HEALTH = 250_000;
    public static final double MM_HEALTH = 200_000_000;
    public int currentStage;
    private boolean stage2EnterFlag;

    private RemotePlayer f1BossTarget;

    @Override
    public String getListenerName() {
        return "DungeonF1BossbarListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
    }

    private void onChat(Component component, boolean b) {
        String message = ToolList.getInstance().deleteColorCode(component.getString());
        if(message.contains("[BOSS] Bonzo: Oh I'm dead!")) {
            stage2EnterFlag = true;
        }
    }

    private void onClientTick(Minecraft mc) {
        if(mc.level == null || !isInCorrectDungeon()) return;

        for (Entity temp : mc.level.entitiesForRendering()) {
            LivingEntity living = temp.asLivingEntity();
            E2AMappingListener.MobInfo mobInfo = e2AMappingListener.getMobInfo(living);
            if(temp instanceof RemotePlayer) {
                if(f1BossTarget != temp && mobInfo != null) {
                    if(temp.getName().getString().equals(NAME.getString()) || mobInfo.armorStand.getName().getString().contains(NAME.getString())) {
                        f1BossTarget = (RemotePlayer) temp;
                        setCurrentStarRailBossBar(this);
                        break;
                    }
                }
            }
        }
        if(f1BossTarget != null) {
            if(stage2EnterFlag && currentStage == 1 && getHealth() / getMaxHealth() > 0.9) {
                stage2EnterFlag = false;
                currentStage = 2;
            }

            if(!ToolList.getInstance().isEntityOnWorld(f1BossTarget)) {
                f1BossTarget = null;
            }
        }
    }

    @Override
    public int getStage() {
        return currentStage;
    }

    @Override
    public int getMaxStage() {
        return 2;
    }

    @Override
    public double getHealth() {
        return Math.min(f1BossTarget == null ? 0 : f1BossTarget.getHealth() < 50 ? 0 : f1BossTarget.getHealth(), getMaxHealth());
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
        return BONZO_ICON;
    }

    @Override
    public boolean isImmune() {
        return false;
    }

    @Override
    public boolean isPowerUpAvaliable() {
        return false;
    }

    @Override
    public boolean isPowerUp() {
        return false;
    }

    @Override
    public Identifier getPowerUpPotionIcon() {
        return null;
    }

    @Override
    public int getPowerUp() {
        return 0;
    }

    @Override
    public CustomBossbar.PowerUpStyle getPowerUpStyle() {
        return null;
    }

    @Override
    public int getMaxPowerUp() {
        return 0;
    }

    @Override
    public CustomBossbar.PowerUpTextState getPowerUpTextState() {
        return CustomBossbar.PowerUpTextState.NUMBER_WITH_MAX;
    }

    @Override
    public boolean isBattleOver() {
        return false;
    }

    @Override
    public void onBattleOver() {
        currentStage = 0;
    }

    @Override
    public LivingEntity getTargetEntity() {
        return f1BossTarget;
    }

    @Override
    public Component getDisplayName() {
        return NAME;
    }

    @Override
    public void onStageEnter(int stage) {
        if(stage == 1) {
            currentStage = 1;
            stage2EnterFlag = false;
        }

        if(currentStage == 2) {
            addStarRailNotification("第二阶段", StarRailNotification.Type.warning);
        }
    }

    @Override
    public boolean shouldNotRenderOtherBoss(LivingEntity e) {
        return false;
    }

    @Override
    public boolean shouldXRayBoss() {
        return true;
    }

    @Override
    public int getFloor() {
        return 1;
    }
}
