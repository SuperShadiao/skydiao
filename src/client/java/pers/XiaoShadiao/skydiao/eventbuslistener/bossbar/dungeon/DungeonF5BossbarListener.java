package pers.XiaoShadiao.skydiao.eventbuslistener.bossbar.dungeon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import pers.XiaoShadiao.skydiao.hud.CustomBossbar;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.awt.*;
import java.util.*;

public class DungeonF5BossbarListener extends AbstractDungeonBossbar {

    // Data from Skyblocker Mod
    private static final Map<Block, ChatFormatting> WOOL_TO_FORMATTING = Map.of(
            Blocks.RED_WOOL, ChatFormatting.RED,
            Blocks.YELLOW_WOOL, ChatFormatting.YELLOW,
            Blocks.LIME_WOOL, ChatFormatting.GREEN,
            Blocks.GREEN_WOOL, ChatFormatting.DARK_GREEN,
            Blocks.BLUE_WOOL, ChatFormatting.BLUE,
            Blocks.MAGENTA_WOOL, ChatFormatting.LIGHT_PURPLE,
            Blocks.PURPLE_WOOL, ChatFormatting.DARK_PURPLE,
            Blocks.GRAY_WOOL, ChatFormatting.GRAY,
            Blocks.WHITE_WOOL, ChatFormatting.WHITE
    );
    private static final Map<String, ChatFormatting> LIVID_TO_FORMATTING = Map.of(
            "Hockey Livid", ChatFormatting.RED,
            "Arcade Livid", ChatFormatting.YELLOW,
            "Smile Livid", ChatFormatting.GREEN,
            "Frog Livid", ChatFormatting.DARK_GREEN,
            "Scream Livid", ChatFormatting.BLUE,
            "Crossed Livid", ChatFormatting.LIGHT_PURPLE,
            "Purple Livid", ChatFormatting.DARK_PURPLE,
            "Doctor Livid", ChatFormatting.GRAY,
            "Vendetta Livid", ChatFormatting.WHITE
    );
    private static final BlockPos WOOL_POS = new BlockPos(5, 110, 42);
    // Data from Skyblocker Mod End


    public static final ResourceLocation LIVID_ICON = Objects.requireNonNull(ResourceLocation.tryBuild("skydiao", "textures/skyblock/boss/livid.png"));
    public static final Component NAME = Component.literal("Livid");
    public static final double HEALTH = 7_000_000;
    public static final double MM_HEALTH = 600_000_000;

    private int bossEntity;
    private int confirmCounter;

    @Override
    public int getFloor() {
        return 5;
    }

    @Override
    public String getListenerName() {
        return "DungeonF5BossbarListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft mc) {
        if (mc.level == null || !isInCorrectDungeon()) return;

        Block woolBlock = mc.level.getBlockState(WOOL_POS).getBlock();
        ChatFormatting formatting = WOOL_TO_FORMATTING.get(woolBlock);
        if(confirmCounter <= 100) {
            if(formatting != null) {
                for (Entity temp : mc.level.entitiesForRendering()) {
                    if(temp instanceof RemotePlayer) {
                        if(bossEntity != temp.getId()) {
                            if(LIVID_TO_FORMATTING.get(ToolList.getInstance().deleteColorCode(temp.getName().getString())) == formatting) {
                                bossEntity = temp.getId();
                                setCurrentStarRailBossBar(this);
                                confirmCounter = 0;
                                break;
                            }
                        } else {
                            confirmCounter++;
                        }
                    }
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
        Entity entity = mc.level == null ? null : mc.level.getEntity(bossEntity);
        LivingEntity livingEntity = entity == null ? null : entity.asLivingEntity();
        return Math.min(livingEntity == null ? 0 : livingEntity.getHealth() < 50 ? 0 : livingEntity.getHealth(), getMaxHealth());
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
    public ResourceLocation getHeadIcon() {
        return LIVID_ICON;
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
    public ResourceLocation getPowerUpPotionIcon() {
        return null;
    }

    @Override
    public int getPowerUp() {
        return getMaxPowerUp();
    }

    @Override
    public CustomBossbar.PowerUpStyle getPowerUpStyle() {
        return CustomBossbar.PowerUpStyle.READY;
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
        confirmCounter = 0;
        bossEntity = 0;
    }

    @Override
    public LivingEntity getTargetEntity() {
        return Optional.ofNullable(mc.level).map(level -> level.getEntity(bossEntity)).map(Entity::asLivingEntity).orElse(null);
    }

    @Override
    public Component getDisplayName() {
        Component name = NAME;
        LivingEntity living = getTargetEntity();
        if(living == null) return name;
        ChatFormatting c = LIVID_TO_FORMATTING.get(ToolList.getInstance().deleteColorCode(living.getName().getString()));
        if(c != null && c.getColor() != null) {
            name = Component.literal(c.toString()).append(name);
        }
        return name;
    }

    @Override
    public void onStageEnter(int stage) {
    }

    @Override
    public boolean shouldNotRenderOtherBoss(LivingEntity e) {
        return !LIVID_TO_FORMATTING.containsKey(ToolList.getInstance().deleteColorCode(e.getName().getString()));
    }

    @Override
    public boolean shouldXRayBoss() {
        return true;
    }

    @Override
    public Color getRenderBossColor() {
        return Optional.ofNullable(getTargetEntity())
                .map(entity -> LIVID_TO_FORMATTING.get(ToolList.getInstance().deleteColorCode(entity.getName().getString())))
                .map(ChatFormatting::getColor)
                .map(Color::new)
                .orElse(super.getRenderBossColor());
    }
}
