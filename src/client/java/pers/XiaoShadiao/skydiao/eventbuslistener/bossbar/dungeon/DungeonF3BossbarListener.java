package pers.XiaoShadiao.skydiao.eventbuslistener.bossbar.dungeon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.ElderGuardian;
import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.hud.CustomBossbar;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.Arrays;
import java.util.Objects;

public class DungeonF3BossbarListener extends AbstractDungeonBossbar {

    public static final Identifier SCARF_ICON = Objects.requireNonNull(Identifier.tryBuild("skydiao", "textures/skyblock/boss/professor.png"));
    public static final Component NAME = Component.literal("The Professor");
    public static final double HEALTH = 3_000_000;
    public static final double MM_HEALTH = 600_000_000;

    private int currentStage;

    private boolean stage3Flag;
    private boolean immuneFlag;

    private ElderGuardian entityLaser;
    private ElderGuardian entityReinforced;
    private ElderGuardian entityHealthy;
    private ElderGuardian entityChaos;
    private long entityLaserLastAlertTime;
    private long entityReinforcedLastAlertTime;
    private long entityHealthyLastAlertTime;
    private long entityChaosLastAlertTime;

    private LivingEntity bossEntity;

    private boolean passWatcherFlag;

    private boolean masterFloorFlag;

    @Override
    public int getFloor() {
        return 3;
    }

    @Override
    public String getListenerName() {
        return "DungeonF3BossbarListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        WorldRenderEvents.END_MAIN.register(this::onLastRender);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((a,b) -> {
            passWatcherFlag = false;
            masterFloorFlag = false;
        });
    }

    private void onLastRender(WorldRenderContext context) {
        RenderUtils.WorldRender wr1 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wr2 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);
        if(System.currentTimeMillis() - entityLaserLastAlertTime < 10000 && entityLaser != null) {
            RenderUtils.renderESP(wr1, entityLaser, 1.0F, 1.0F, 0.0F, 1.0F, false);
            RenderUtils.renderESP(wr2, entityLaser, 1.0F, 1.0F, 0.0F, 0.5F, true);
            RenderUtils.renderTrace(wr1, entityLaser, 1.0F, 1.0F, 0.0F, 1.0F);
        }
        if(System.currentTimeMillis() - entityReinforcedLastAlertTime < 10000 && entityReinforced != null) {
            RenderUtils.renderESP(wr1, entityReinforced, 1.0F, 1.0F, 0.0F, 1.0F, false);
            RenderUtils.renderESP(wr2, entityReinforced, 1.0F, 1.0F, 0.0F, 0.5F, true);
            RenderUtils.renderTrace(wr1, entityReinforced, 1.0F, 1.0F, 0.0F, 1.0F);
        }
        if(System.currentTimeMillis() - entityHealthyLastAlertTime < 10000 && entityHealthy != null) {
            RenderUtils.renderESP(wr1, entityHealthy, 0.0F, 1.0F, 0.0F, 1.0F, false);
            RenderUtils.renderESP(wr2, entityHealthy, 0.0F, 1.0F, 0.0F, 0.5F, true);
            RenderUtils.renderTrace(wr1, entityHealthy, 0.0F, 1.0F, 0.0F, 1.0F);
        }
        if(System.currentTimeMillis() - entityChaosLastAlertTime < 10000 && entityChaos != null) {
            RenderUtils.renderESP(wr1, entityChaos, 1.0F, 0.0F, 0.0F, 1.0F, false);
            RenderUtils.renderESP(wr2, entityChaos, 1.0F, 0.0F, 0.0F, 0.5F, true);
            RenderUtils.renderTrace(wr1, entityChaos, 1.0F, 0.0F, 0.0F, 1.0F);
        }
        wr1.finishDraw();
        wr2.finishDraw();
    }

    private void onChat(Component component, boolean b) {
        String message = ToolList.getInstance().deleteColorCode(component.getString());
        switch (message) {
            case "[BOSS] The Professor: The process is irreversible, but I'll be stronger than a Wither now!" -> {
                stage3Flag = true;
                immuneFlag = false;
            }
            case "[BOSS] The Professor: This time I'll be your opponent!" -> immuneFlag = false;
            case "[BOSS] The Watcher: You have proven yourself. You may pass." -> passWatcherFlag = true;
        }

        if(currentStage == 1 && !message.contains("damage")) {
            if(message.contains("Laser Guardian")) {
                entityLaserLastAlertTime = System.currentTimeMillis();
            } else if(message.contains("Chaos Guardian")) {
                entityChaosLastAlertTime = System.currentTimeMillis();
            } else if(message.contains("Healthy Guardian")) {
                entityHealthyLastAlertTime = System.currentTimeMillis();
            } else if(message.contains("Reinforced Guardian")) {
                entityReinforcedLastAlertTime = System.currentTimeMillis();
            }
        }
        if(stage3Flag && currentStage == 1 && getHealth() / getMaxHealth() > 0.9) {
            stage3Flag = false;
            currentStage = 2;
        }
    }

    private void onClientTick(Minecraft mc) {
        if (mc.level == null || (!passWatcherFlag && !isInCorrectDungeon())) return;

        masterFloorFlag |= isInMasterDungeonFloor();

        for (Entity temp : mc.level.entitiesForRendering()) {
            LivingEntity living = temp.asLivingEntity();
            E2AMappingListener.MobInfo mobInfo = e2AMappingListener.getMobInfo(living);
            if(temp instanceof RemotePlayer || temp instanceof ElderGuardian) {
                if(mobInfo != null) {
                    String armorStandName = mobInfo.armorStand.getName().getString();
                    if(bossEntity != temp) {
                        if(temp.getName().getString().equals(NAME.getString()) || armorStandName.contains(NAME.getString())) {
                            bossEntity = (LivingEntity) temp;
                            setCurrentStarRailBossBar(this);
                            break;
                        }
                    }
                    if(temp instanceof ElderGuardian) {
                        if (armorStandName.contains("Laser")) {
                            entityLaser = (ElderGuardian) temp;
                        } else if (armorStandName.contains("Reinforced")) {
                            entityReinforced = (ElderGuardian) temp;
                        } else if (armorStandName.contains("Healthy")) {
                            entityHealthy = (ElderGuardian) temp;
                        } else if (armorStandName.contains("Chaos")) {
                            entityChaos = (ElderGuardian) temp;
                        }
                    }
                }
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
        return Math.min(bossEntity == null ? 0 : bossEntity.getHealth() < 50 ? 0 : bossEntity.getHealth(), getMaxHealth());
    }

    @Override
    public double getMaxHealth() {
        return masterFloorFlag ? MM_HEALTH : HEALTH;
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
        return immuneFlag;
    }

    @Override
    public boolean isPowerUpAvaliable() {
        return immuneFlag;
    }

    @Override
    public boolean isPowerUp() {
        return false;
    }

    @Override
    public Identifier getPowerUpPotionIcon() {
        return Gui.getMobEffectSprite(MobEffects.RESISTANCE);
    }

    @Override
    public int getPowerUp() {
        int i = getMaxPowerUp();

        double sum = 0;

        for (ElderGuardian entity : Arrays.asList(entityLaser, entityChaos, entityHealthy, entityReinforced)) {
            if(entity != null) {
                float percent = entity.getHealth() / entity.getMaxHealth();
                if(percent < 0.001) percent = 0;
                sum += percent;
            } else {
                sum += 1;
            }
        }

        return (int) (i * sum / 4);
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
        return immuneFlag ? null : bossEntity;
    }

    @Override
    public Component getDisplayName() {
        return NAME;
    }

    @Override
    public void onStageEnter(int stage) {
        if(stage == 1) {
            currentStage = 1;
            immuneFlag = true;
            stage3Flag = false;
        }
    }

    @Override
    public boolean shouldNotRenderOtherBoss(LivingEntity e) {
        return !(e instanceof ElderGuardian);
    }

    @Override
    public boolean shouldXRayBoss() {
        return true;
    }
}
