package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.jspecify.annotations.Nullable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.Deployable;
import pers.XiaoShadiao.skydiao.utils.DeployableType;

import java.util.*;
import java.util.regex.Matcher;

public class DeployableHUD extends XSDHUD {

    private final Set<Deployable> activeDeployables = new HashSet<>();

    private final Map<DeployableType, Deployable> higherTierActiveDeployables = new HashMap<>();

    @Override
    public void runRegister() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("skydiao", "deployable_hud"), this);
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onUnload);
    }

    private void onUnload(Minecraft mc, ClientLevel level) {
        activeDeployables.clear();
        higherTierActiveDeployables.clear();
        Arrays.stream(Deployable.values()).forEach(Deployable::reset);
    }

    private int tick = 0;

    private void onTick(Minecraft mc) {
        if(mc.player == null || mc.level == null) return;

        if(tick++ % 20 != 0) return;

        a:for (Deployable value : Deployable.values()) {
            for (ArmorStand armorStand : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(value.getRange() - 1))) {
                if(armorStand.hasCustomName()) {
                    String name = armorStand.getName().getString();
                    Matcher matcher = value.getPattern().matcher(name);
                    if(matcher.find()) {
                        boolean isActive = value.isActive();
                        int time = Integer.parseInt(matcher.group("time"));
                        if(!value.isInRange()) {
                            value.setEntity(armorStand);
                            // value.setExpiryTime(System.currentTimeMillis() + 1000L * Integer.parseInt(matcher.group("time")));
                            value.setRemainSecond(time);
                            isActive = true;
                        } else {
                            if(time > value.getRemainSecond()) {
                                isActive = true;
                                value.setEntity(armorStand);
                                value.setRemainSecond(time);
                            }
                        }
                        if(isActive) {
                            activeDeployables.add(value);
                            continue a;
                        }
                    }
                }
            }
        }
        activeDeployables.removeIf(Deployable::isExpired);

        for (Deployable deployable : activeDeployables) {
            higherTierActiveDeployables.compute(deployable.getType(), (k, old) -> {
                if(old == null || !old.isActive() || !old.isInRange()) return deployable;
                if(old.isActive() && old.isInRange() && deployable.isActive() && deployable.isInRange()) {
                    if(old.getTier() < deployable.getTier()) {
                        return deployable;
                    }
                    if(old.getTier() == deployable.getTier() && old.getRemainSecond() < deployable.getRemainSecond()) {
                        return deployable;
                    }
                }
                return old;
            });
        }
        higherTierActiveDeployables.values().removeIf(d -> {
            if(d.isExpired()) {
                if(d.isInRange() && ConfigManager.deployableExpiredTitle.getValue()) bigTitle.updateTitleMsg(d.getDisplayName() + "§c消失了!", 5000, SoundEvents.BLAZE_DEATH);
                return true;
            }
            return false;
        });
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force) {
        if(mc.player == null || mc.level == null) return;

        if(!force && !ConfigManager.deployableHUD.getValue()) return;

        int i = 0;
        for (Deployable deployable : higherTierActiveDeployables.values()) {
            if(deployable.isInRange()) context.centeredText(mc.font, deployable.getDisplayName() + " §e" + deployable.getRemainSecond() + "s", context.guiWidth() / 2, context.guiHeight() / 2 + 60 + i * 10, 0xFFFFFFFF);
            i++;
        }
    }

    @Override
    public void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force, HudOffsetAndScale settings) {

    }

    @Override
    public @Nullable String getHudName() {
        return "DeployableHUD";
    }

    public int getRemainSecondsByType(DeployableType type) {
        Deployable deployable = higherTierActiveDeployables.get(type);
        if(deployable == null) return 0;
        if(!deployable.isInRange() || deployable.isExpired()) return 0;
        return deployable.getRemainSecond();
    }
}
