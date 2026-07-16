package pers.XiaoShadiao.skydiao.eventbuslistener.bossbar.dungeon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.hud.CustomBossbar;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class DungeonF2BossbarListener extends AbstractDungeonBossbar {

    public static final Identifier SCARF_ICON = Objects.requireNonNull(Identifier.tryBuild("skydiao", "textures/skyblock/boss/scarf.png"));
    public static final Component NAME = Component.literal("Scarf");
    public static final double HEALTH = 1_000_000;
    public static final double MM_HEALTH = 375_000_000;

    private RemotePlayer f2BossTarget;
    private boolean isImmuneState;

    private final Set<Integer> undeadsEntityIds = new HashSet<>();
    private final Set<Integer> undeadsDiedEntityIds = new HashSet<>();
    private final Set<Integer> undeadsPriestEntityIds = new HashSet<>();

    @Override
    public String getListenerName() {
        return "DungeonF2BossbarListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
    }

    private void onChat(Component component, boolean b) {
        String message = ToolList.getInstance().deleteColorCode(component.getString());
        if(message.equals("[BOSS] Scarf: Did you forget? I was taught by the best! Let's dance.")) {
            isImmuneState = false;
        }
    }

    private void onClientTick(Minecraft mc) {
        if(mc.level == null || !isInCorrectDungeon()) return;

        for (Entity temp : mc.level.entitiesForRendering()) {
            LivingEntity living = temp.asLivingEntity();
            E2AMappingListener.MobInfo mobInfo = e2AMappingListener.getMobInfo(living);
            if(temp instanceof RemotePlayer) {
                if(mobInfo != null) {
                    if(f2BossTarget != temp) {
                        if(temp.getName().getString().equals(NAME.getString()) || mobInfo.armorStand.getName().getString().contains(NAME.getString())) {
                            f2BossTarget = (RemotePlayer) temp;
                            setCurrentStarRailBossBar(this);
                            break;
                        }
                    }
                    if (mobInfo.armorStand.getName().getString().contains("Undead")) {
                        undeadsEntityIds.add(temp.getId());
                        if(mobInfo.armorStand.getName().getString().contains("Priest")) {
                            undeadsPriestEntityIds.add(temp.getId());
                        }
                        if(((RemotePlayer) temp).getHealth() <= 100) {
                            undeadsDiedEntityIds.add(temp.getId());
                        }
                    }
                }
            }
        }

        undeadsEntityIds.removeIf(undead -> mc.level.getEntity(undead) == null);
        undeadsPriestEntityIds.removeIf(undead -> mc.level.getEntity(undead) == null);
        undeadsDiedEntityIds.removeIf(undead -> mc.level.getEntity(undead) == null);

        if(f2BossTarget != null) {
            if(!ToolList.getInstance().isEntityOnWorld(f2BossTarget)) {
                f2BossTarget = null;
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
        return Math.min(f2BossTarget == null ? 0 : f2BossTarget.getHealth() < 50 ? 0 : f2BossTarget.getHealth(), getMaxHealth());
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
        return SCARF_ICON;
    }

    @Override
    public boolean isImmune() {
        return isImmuneState || (isInMasterDungeonFloor() && getPowerUp() > 0);
    }

    @Override
    public boolean isPowerUpAvaliable() {
        return isInMasterDungeonFloor() || isImmuneState;
    }

    @Override
    public boolean isPowerUp() {
        return isInMasterDungeonFloor() && !isImmuneState && getPowerUp() > 0;
    }

    @Override
    public Identifier getPowerUpPotionIcon() {
        return Gui.getMobEffectSprite(!isImmuneState ? MobEffects.REGENERATION : MobEffects.RESISTANCE);
    }

    @Override
    public int getPowerUp() {
        if(mc.level == null) return 0;
        long deadCount;
        if(isImmuneState) {
            deadCount = undeadsDiedEntityIds.size();
        } else {
            deadCount = undeadsPriestEntityIds.size();
        }
        return (int) deadCount;
    }

    @Override
    public CustomBossbar.PowerUpStyle getPowerUpStyle() {
        return !isImmuneState ? CustomBossbar.PowerUpStyle.READY : CustomBossbar.PowerUpStyle.CHARGING;
    }

    @Override
    public int getMaxPowerUp() {
        if(isInMasterDungeonFloor()) {
            if(isImmuneState) return 8; else return 2;
        }
        return 4;
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
    }

    @Override
    public LivingEntity getTargetEntity() {
        return f2BossTarget;
    }

    @Override
    public Component getDisplayName() {
        return NAME;
    }

    @Override
    public void onStageEnter(int stage) {
        if(stage == 1) {
            isImmuneState = true;
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
        return 2;
    }
}
