package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.mixin.client.MixinBossbarEventGetter;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomBossbar extends XSDHUD {

    private final Map<UUID, AnimationManager> animationMap = new HashMap<>();

    public static final Pattern HIT_PATTERN = Pattern.compile("\\d+(?= Hit)");

    private List<UUID> eventBossbars = new ArrayList<>();

    @Override
    public void runRegister() {
        HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, (bossBar) -> ConfigManager.bossbar.getValue() ? this : bossBar);
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        AttackEntityCallback.EVENT.register(this::onAttackEntity);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::onWorldUnload);
        WorldRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(WorldRenderContext context) {
        if(starRailBossBar != null) {
            LivingEntity entity = starRailBossBar.getTargetEntity();
            RenderUtils.WorldRender worldRender = RenderUtils.createWorldRenderInstance(context, starRailBossBar.shouldXRayBoss() ? CustomRenderPipeline.THROUGH_WALLS_LINE : CustomRenderPipeline.NO_THROUGH_WALLS_LINE);
            if(entity != null) {
                Color c = starRailBossBar.getRenderBossColor();
                RenderUtils.renderESP(worldRender, entity, c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 1,  false);
                if(starRailBossBar.shouldXRayBoss()) RenderUtils.renderTrace(worldRender, entity, c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 1);
            }
            worldRender.finishDraw();
        }
    }

    private void onWorldUnload(Minecraft mc, ClientLevel level) {
        animationMap.clear();
        unloadBossBar();
    }

    private void unloadBossBar() {
        if(starRailBossBar != null) starRailBossBar.onBattleOver();
        starRailBossBar = null;
    }

    private InteractionResult onAttackEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult entityHitResult) {
        if(ConfigManager.bossbarAddTargetEntity.getValue()) {
            LivingEntity living = entity.asLivingEntity();
            if (living != null) addEntityToBossbar(living);
        }
        return InteractionResult.PASS;
    }

    private void onClientTick(Minecraft mc) {
        animationMap.values().forEach(AnimationManager::updateTick);

        if (ConfigManager.bossbarAddTargetEntity.getValue() && mc.level != null) {
            List<Arrow> arrowList = new ArrayList<>();
            for (Entity entity : mc.level.entitiesForRendering()) {
                if(entity instanceof Arrow arrow) {
                    if(arrow.getOwner() == mc.player) {
                        arrowList.add(arrow);
                    }
                }
            }
            for (Entity entity : mc.level.entitiesForRendering()) {
                if(!(entity instanceof LivingEntity) || entity instanceof ArmorStand || entity == mc.player) continue;
                AABB aabb = entity.getBoundingBox().inflate(2);
                for (Arrow arrow : arrowList) {
                    if(aabb.contains(arrow.position())) {
                        addEntityToBossbar(entity.asLivingEntity());
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void render(@NotNull GuiGraphics context, @NotNull DeltaTracker tickCounter) {
        if(mc.level == null) return;
        MixinBossbarEventGetter bossbarEventGetter = (MixinBossbarEventGetter) mc.gui.getBossOverlay();
        List<UUID> temp = new ArrayList<>(bossbarEventGetter.getEvents().size());
        bossbarEventGetter.getEvents().forEach((uuid, lerpingBossEvent) -> {
            // System.out.println(lerpingBossEvent);
            // System.out.println(lerpingBossEvent.getName());
            UUID actuallyUUID = uuid;
            Optional<HoverEvent> hoverEvent = Optional.of(lerpingBossEvent.getName().getStyle()).map(Style::getHoverEvent);
            if (hoverEvent.isPresent()) {
                HoverEvent event = hoverEvent.get();
                if (event instanceof HoverEvent.ShowEntity(HoverEvent.EntityTooltipInfo entity)) {
                    actuallyUUID = entity.uuid;
                }
            }
            temp.add(actuallyUUID);
            UUID finalActuallyUUID = actuallyUUID;
            LivingEntity entity = Optional.ofNullable(mc.level).map(l -> l.getEntity(finalActuallyUUID)).map(ItemOwner::asLivingEntity).orElse(null);
            E2AMappingListener.MobInfo mobInfo = AbstractListener.e2AMappingListener.getMobInfo(entity);

            AnimationManager am;
            if (mobInfo != null) {
                am = animationMap.computeIfAbsent(finalActuallyUUID, k -> new AnimationManager(mobInfo, mobInfo.theEntity.getId()));
            } else if (entity != null) {
                am = animationMap.computeIfAbsent(finalActuallyUUID, k -> new AnimationManager(null, entity.getId()));
            } else {
                am = animationMap.computeIfAbsent(finalActuallyUUID, k -> new AnimationManager(null, null));
                am.setHealthUpdater((am2) -> {
                    am2.maxHealth = 1000;
                    am2.currentHealth = 1000 * lerpingBossEvent.getProgress();
                    am2.bossName = lerpingBossEvent.getName();
                });
            }
            am.setMobInfo(mobInfo);
            am.flagActive();
        });
        eventBossbars = temp;

        int i = 0;

        float particalTick = tickCounter.getGameTimeDeltaTicks();
        boolean hasSRBB = starRailBossBar != null;

        if(hasSRBB && animationFade < 20) {
            animationFade = Math.min(20, animationFade + particalTick * 3);
        } else if(!hasSRBB && animationFade > 0) {
            animationFade = Math.max(0, animationFade - particalTick * 3);
        }
        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        if(hasSRBB) {
            pose.translate(0, animationFade * 2);
            drawStarRailBossBar(context, starRailBossBar, particalTick);
            pose.translate(0, animationFade * 2);
            if(starRailBossBar.isBattleOver()) {
                unloadBossBar();
            }
        }

        boolean clearFlag = false;

        for (Iterator<Map.Entry<UUID, AnimationManager>> it = animationMap.entrySet().stream().sorted(Comparator.comparingInt((e) -> {
            AnimationManager am = e.getValue();
            return am == null ? 0 : am.currentIndex;
        })).iterator(); it.hasNext(); ) {
            Map.Entry<UUID, AnimationManager> entry = it.next();

            AnimationManager am = entry.getValue();
            LivingEntity entity = Optional.ofNullable(mc.level).filter(a -> am.entityId != null).map(l -> l.getEntity(am.entityId)).map(ItemOwner::asLivingEntity).orElse(null);
            am.updateAnimation(particalTick);

            int width = mc.getWindow().getGuiScaledWidth();
            int length = 182;
            int x = width / 2 - length / 2;

            int yOffset = i * (mc.font.lineHeight + 10);

            if (!am.isActive()) continue;
            if(starRailBossBar != null && (entity == null || entity.isInvisible() || entity == starRailBossBar.getTargetEntity() || starRailBossBar.shouldNotRenderOtherBoss(entity))) continue;

            if (i < ConfigManager.bossbarDisplayLimit.getValue() - (starRailBossBar != null ? 3 : 0)) {
                MutableComponent s = am.bossName == null ? Component.empty() : am.bossName.copy();
                StringBuilder shealth = new StringBuilder("      ");
                if(entity != null) {
                    if (ConfigManager.bossbarShowHealth.getValue() && !s.getString().contains("❤") && !entity.isInvisible())
                        shealth.append("§a").append(ToolList.getInstance().numberToEZString(am.currentHealth)).append("§c❤");
                    if (ConfigManager.bossbarShowHealth.getValue() && !entity.isInvisible())
                        shealth.append(" §a").append(Math.floor((am.currentHealth / am.maxHealth * 100 + 0.05) * 10) / 10).append("%§c❤");
                }
                if (am.mobInfo != null && am.mobInfo.armorStandForBoss != null)
                    s.append(" §f| ").append(am.mobInfo.armorStandForBoss.getName());
                context.drawString(mc.font, s.copy().append(shealth.toString()), (int) (width / 2 - mc.font.width(s) / 2), (int) (12 - 10 + yOffset), 0xFFFFFFFF, true);

                context.fill(x, 12 + yOffset, x + length, 15 + yOffset, AnimationManager.healthBgColor.getRGB());
                context.fill(x - 1, 13 + yOffset, x + length + 1, 14 + yOffset, AnimationManager.healthBgColor.getRGB());

                Color color = am.isDamaged ? AnimationManager.dmgHealthColor : AnimationManager.healingHealthColor;
                context.fill(x, 12 + yOffset, (int) (x + length * am.trueScale), 15 + yOffset, color.getRGB());
                context.fill(x - (am.trueScale != 0 ? 1 : 0), 13 + yOffset, (int) (x + length * am.trueScale + (am.trueScale == 1 ? 1 : 0)), 14 + yOffset, color.getRGB());

                Color healthColor = AnimationManager.commonHealthColor;
                LerpingBossEvent bossEvent = bossbarEventGetter.getEvents().get(entry.getKey());
                if(bossEvent != null) {
                    BossEvent.BossBarColor color1 = bossEvent.getColor();
                    if (!color1.equals(BossEvent.BossBarColor.RED)) {
                        Integer color2 = color1.getFormatting().getColor();
                        if(null != color2) healthColor = new Color(color2);
                    }
                }

                Color immuneStateHealthColor = AnimationManager.immuneStateHealthColor;
                Color immuneStateHealthColorSwitch = AnimationManager.immuneStateHealthColorSwitch;

                Color actualColor;

                if (am.skyblockIsImmuneDmg) {
                    if (am.renderFlagImmuneDmg <= 20) {
                        actualColor = new Color(
                                (int) Mth.clampedLerp(am.renderFlagImmuneDmg / 20d, healthColor.getRed(), immuneStateHealthColorSwitch.getRed()),
                                (int) Mth.clampedLerp(am.renderFlagImmuneDmg / 20d, healthColor.getGreen(), immuneStateHealthColorSwitch.getGreen()),
                                (int) Mth.clampedLerp(am.renderFlagImmuneDmg / 20d, healthColor.getBlue(), immuneStateHealthColorSwitch.getBlue())
                        );
                    } else {
                        actualColor = new Color(
                                (int) Mth.clampedLerp((am.renderFlagImmuneDmg - 20) / 80d, immuneStateHealthColorSwitch.getRed(), immuneStateHealthColor.getRed()),
                                (int) Mth.clampedLerp((am.renderFlagImmuneDmg - 20) / 80d, immuneStateHealthColorSwitch.getGreen(), immuneStateHealthColor.getGreen()),
                                (int) Mth.clampedLerp((am.renderFlagImmuneDmg - 20) / 80d, immuneStateHealthColorSwitch.getBlue(), immuneStateHealthColor.getBlue())
                        );
                    }
                } else {
                    if (am.renderFlagImmuneDmg >= 80) {
                        actualColor = new Color(
                                (int) Mth.clampedLerp((am.renderFlagImmuneDmg - 80) / 20d, immuneStateHealthColorSwitch.getRed(), immuneStateHealthColor.getRed()),
                                (int) Mth.clampedLerp((am.renderFlagImmuneDmg - 80) / 20d, immuneStateHealthColorSwitch.getGreen(), immuneStateHealthColor.getGreen()),
                                (int) Mth.clampedLerp((am.renderFlagImmuneDmg - 80) / 20d, immuneStateHealthColorSwitch.getBlue(), immuneStateHealthColor.getBlue())
                        );
                    } else {
                        actualColor = new Color(
                                (int) Mth.clampedLerp(am.renderFlagImmuneDmg / 80d, healthColor.getRed(), immuneStateHealthColorSwitch.getRed()),
                                (int) Mth.clampedLerp(am.renderFlagImmuneDmg / 80d, healthColor.getGreen(), immuneStateHealthColorSwitch.getGreen()),
                                (int) Mth.clampedLerp(am.renderFlagImmuneDmg / 80d, healthColor.getBlue(), immuneStateHealthColorSwitch.getBlue())
                        );
                    }
                }

                context.fill(x, 12 + yOffset, (int) (x + length * am.trueHealthScale), 15 + yOffset, actualColor.getRGB());
                context.fill(x - (am.trueHealthScale != 0 ? 1 : 0), 13 + yOffset, (int) (x + length * am.trueHealthScale + (am.trueHealthScale == 1 ? 1 : 0)), 14 + yOffset, actualColor.getRGB());


                if (am.isHitProtection && am.hits > 0) {
                    // mc.mcProfiler.endStartSection("9");
                    context.fill(x + 1, 16 + yOffset, x + length - 1, 19 + yOffset, new Color(73, 45, 114, 255).getRGB());
                    context.fill(x, 17 + yOffset, x + length, 18 + yOffset, new Color(73, 45, 114, 255).getRGB());

                    float scale = 1 - (float) am.hits / am.maxHits;

                    float midLength = (float) length / 2;

                    context.fill((int) (x + midLength - midLength * scale + 1f), 16 + yOffset, (int) (x + midLength + midLength * scale - 1f), 19 + yOffset, new Color((int) Math.max(209, 209 + am.hitsChanges * 46), (int) Math.max(193, 193 + am.hitsChanges * 62), (int) Math.max(239, 239 + am.hitsChanges * 16), 255).getRGB());
                    context.fill((int) (x + midLength - midLength * scale), 17 + yOffset, (int) (x + midLength + midLength * scale), 18 + yOffset, new Color((int) Math.max(209, 209 + am.hitsChanges * 46), (int) Math.max(193, 193 + am.hitsChanges * 62), (int) Math.max(239, 239 + am.hitsChanges * 16), 255).getRGB());

                    context.fill((int) (x + midLength - midLength * scale - 1f), 15 + yOffset, (int) (x + midLength - midLength * scale + 1f), 20 + yOffset, new Color(255, 255, 255, 255).getRGB());
                    context.fill((int) (x + midLength + midLength * scale - 1f), 15 + yOffset, (int) (x + midLength + midLength * scale + 1f), 20 + yOffset, new Color(255, 255, 255, 255).getRGB());

                    context.fill((int) (x + midLength - midLength * scale - 1f), 14 + yOffset, (int) (x + midLength - midLength * scale + 1f), 21 + yOffset, new Color(255, 255, 255, (int) (255 * am.hitsChanges)).getRGB());
                    context.fill((int) (x + midLength + midLength * scale - 1f), 14 + yOffset, (int) (x + midLength + midLength * scale + 1f), 21 + yOffset, new Color(255, 255, 255, (int) (255 * am.hitsChanges)).getRGB());
                }

            } else {
                clearFlag = true;
            }

            i++;
        }
        pose.popMatrix();
        boolean finalClearFlag = clearFlag;
        animationMap.entrySet().removeIf(entry -> ((finalClearFlag && !eventBossbars.contains(entry.getKey())) || !entry.getValue().isActive()) && mc.level.getEntity(entry.getKey()) == null);
    }

    public void addEntityToBossbar(LivingEntity entity) {
        if(entity == null) return;
        E2AMappingListener.MobInfo mobInfo = AbstractListener.e2AMappingListener.getMobInfo(entity);

        AnimationManager am = animationMap.computeIfAbsent(entity.getUUID(), k -> new AnimationManager(mobInfo, entity.getId()));

        am.setMobInfo(mobInfo);
        am.flagActive();
    }

    public static class AnimationManager {

        public static final Color
                commonHealthColor = new Color(0xEF, 0x2A, 0x25),
                healthBgColor = new Color(71, 50, 71, 255),
                immuneStateHealthColor = new Color(137, 137, 137),
                immuneStateHealthColorSwitch = new Color(255, 255, 255),
                dmgHealthColor = new Color(0xCF, 0xCA, 0x11),
                healingHealthColor = new Color(0x0, 0xFF, 0x0);


        public long lastActiveTime;

        public E2AMappingListener.MobInfo mobInfo;
        public final Integer entityId;
        public float healthScale, trueScale, trueHealthScale;
        public Component bossName;
        public float maxHealth;
        public float currentHealth;
        public boolean isDamaged;
        public boolean skyblockIsBoss;
        public boolean skyblockIsImmuneDmg;
        public boolean isHitProtection;
        public double hitsChanges;
        public int hits;
        public int lastHits;
        public int maxHits;
        public String armorstandpartName;
        public float armorstandpartHealth;
        public float armorstandpartMaxHealth;
        public double renderFlagImmuneDmg;
        public Color color = new Color(0xFFEF2A25);

        private static int indexId = Integer.MIN_VALUE;

        public int currentIndex = indexId++;

        public boolean useCustomHealthUpdater;
        public Consumer<AnimationManager> healthUpdater;
        public static Consumer<AnimationManager> mobInfoHealthUpdater = (animationManager) -> {
            animationManager.currentHealth = (float) animationManager.mobInfo.health;
            animationManager.maxHealth = (float) animationManager.mobInfo.maxHealth;
        };
        public static Consumer<AnimationManager> entityHealthUpdater = (animationManager) -> {
            Optional.ofNullable(mc.level).map(level -> level.getEntity(animationManager.entityId)).map(ItemOwner::asLivingEntity).ifPresent(livingEntity -> {
                animationManager.currentHealth = livingEntity.getHealth();
                animationManager.maxHealth = Math.max(livingEntity.getMaxHealth(), animationManager.currentHealth);
            });
        };
        public static Consumer<AnimationManager> unsupportedHealthUpdater = (animationManager) -> {
            throw new UnsupportedOperationException("MobInfo为null, 且未设置healthUpdater");
        };


        public AnimationManager(E2AMappingListener.MobInfo mobInfo, Integer entityId) {
            // this.mobInfo = mobInfo;
            this.entityId = entityId;

            healthUpdater = unsupportedHealthUpdater;

            setMobInfo(mobInfo);

            useCustomHealthUpdater = healthUpdater == unsupportedHealthUpdater;

            flagActive();
            updateTick();
        }

        public void setHealthUpdater(Consumer<AnimationManager> healthUpdater) {
            this.healthUpdater = healthUpdater;
        }

        public void updateAnimation(float animationDeltaTick) {
            // System.out.println(animationDeltaTick);
            animationDeltaTick *= 3;

            healthUpdater.accept(this);

            healthScale = Mth.clampedLerp(currentHealth / maxHealth, 0, 1);

            if (trueScale < 0) trueScale = 0;
            if (trueScale < healthScale) trueScale = healthScale;

            if (trueHealthScale > healthScale || trueHealthScale == 0) {
                trueHealthScale = healthScale;
            } else if (trueHealthScale < healthScale) {
                trueHealthScale += 0.0018F * animationDeltaTick;
                isDamaged = false;
            }

            if (trueScale > healthScale) {
                trueScale -= 0.0018F * animationDeltaTick;
                isDamaged = true;
                if(isActive()) flagActive();
            }

            if (skyblockIsImmuneDmg) {
                if (renderFlagImmuneDmg < 100) {
                    renderFlagImmuneDmg += 2 * animationDeltaTick;
                    renderFlagImmuneDmg = (float) Math.min(100, renderFlagImmuneDmg);
                }
            } else {
                if (renderFlagImmuneDmg > 0) {
                    renderFlagImmuneDmg -= 2 * animationDeltaTick;
                    renderFlagImmuneDmg = (float) Math.max(0, renderFlagImmuneDmg);
                }
            }

            if (hitsChanges > 0) {
                hitsChanges = Math.max(hitsChanges - 0.04 * animationDeltaTick, 0);
            }
        }

        public void updateTick() {
            if (mc.level == null) return;
            Entity entity;
            if (entityId != null && (entity = mc.level.getEntity(entityId)) != null) {
                bossName = entity.getName();

                if(entity instanceof Pig && ToolList.getInstance().isDevEnvironment()) {
                    isHitProtection = true;
                    hits = (int) currentHealth;
                    if (lastHits != hits) {
                        hitsChanges = 1;
                    }
                    lastHits = hits;
                    maxHits = Math.max(hits, maxHits);
                }
            }
            if (mobInfo != null) {
                if (mobInfo.armorStand != null) {
                    String name = mobInfo.armorStand.getDisplayName().getString();
                    skyblockIsBoss = !name.contains("✧") && name.contains("☠");

                    StringBuilder sb = new StringBuilder();
                    StringBuilder sb2 = new StringBuilder();
                    boolean skipOne = false;
                    boolean isHealthEnd = false;
                    for (char c : mobInfo.armorStand.getDisplayName().getString().toCharArray()) {
                        if (skipOne) {
                            skipOne = false;
                            continue;
                        }
                        if (c == '§') {
                            skipOne = true;
                            continue;
                        }
                        switch (c) {
                            case ']':
                                sb.setLength(0);
                                continue;
                            case ' ':
                                sb2.append(sb);
                                sb.setLength(0);
                                break;
                            case '❤':
                                isHealthEnd = true;
                                armorstandpartName = sb2.toString().trim();
                                try {
                                    String[] healthPart = sb.toString().trim().split("/");
                                    armorstandpartHealth = (float) ToolList.getInstance().ezStringToNumber(healthPart[0]);
                                    armorstandpartMaxHealth = (float) ToolList.getInstance().ezStringToNumber(healthPart[1]);
                                } catch (Exception ignored) {

                                }
                        }
                        sb.append(c);
                    }


                    if (!isHealthEnd) armorstandpartName = String.valueOf(sb2) + sb;
                    Matcher m = HIT_PATTERN.matcher(armorstandpartName);
                    if (m.find()) {
                        isHitProtection = true;
                        hits = Integer.parseInt(m.group());
                        if (lastHits != hits) {
                            hitsChanges = 1;
                            if(isActive()) flagActive();
                        }
                        lastHits = hits;
                        maxHits = Math.max(hits, maxHits);
                    } else isHitProtection = false;
                } else {
                    skyblockIsBoss = false;
                    isHitProtection = false;
                }

                if (mobInfo.armorStandForBoss != null) {
                    String name = mobInfo.armorStandForBoss.getDisplayName().getString();
                    skyblockIsImmuneDmg = name.contains("Protected") || name.contains("MANIA");
                } else skyblockIsImmuneDmg = false;

                // System.out.println(ComponentRenderUtilss.wrapComponents((mobInfo.armorStand != null ? mobInfo.armorStand : mobInfo.theEntity).getName(), Integer.MAX_VALUE, mc.font).getFirst().getResultOrEmpty());
                bossName = (ToolList.getInstance().isEntityOnWorld(mobInfo.theEntity) && mobInfo.armorStand != null ? mobInfo.armorStand : mobInfo.theEntity).getName();
            }
        }

        public void setColor(Color color) {
            this.color = color;
        }

        public void setMobInfo(E2AMappingListener.MobInfo mobInfo) {
            if (useCustomHealthUpdater) return;

            this.mobInfo = mobInfo;

            if (mobInfo != null) {
                setHealthUpdater(mobInfoHealthUpdater);
            } else if (entityId != null) {
                setHealthUpdater(entityHealthUpdater);
            } else {
                setHealthUpdater(unsupportedHealthUpdater);
            }
        }

        public void flagActive() {
            if(!isActive()) currentIndex = indexId++;
            lastActiveTime = System.currentTimeMillis();
        }

        public boolean isActive() {
            return System.currentTimeMillis() - lastActiveTime < 5000;
        }
    }

    public interface IStarRailBossBar {

        public static final Color DEFAULT_BOSS_COLOR = new Color(255, 0, 0);

        public int getStage();
        public int getMaxStage();
        public double getHealth();
        public double getMaxHealth();
        public boolean hasWeakness();
        public int getWeakness();
        public int getMaxWeakness();

        public Identifier getHeadIcon();

        public boolean isImmune();

        public boolean isPowerUpAvaliable();
        public boolean isPowerUp();
        public Identifier getPowerUpPotionIcon();
        public int getPowerUp();
        public default Color getPwoerUpColor() { return POWERUP; };
        public PowerUpStyle getPowerUpStyle();
        public int getMaxPowerUp();
        public PowerUpTextState getPowerUpTextState();
        public boolean isBattleOver();
        public void onBattleOver();

        public LivingEntity getTargetEntity();

        public Component getDisplayName();

        public void onStageEnter(int stage);

        public boolean shouldNotRenderOtherBoss(LivingEntity e);
        public boolean shouldXRayBoss();

        public default Color getRenderBossColor() {
            return DEFAULT_BOSS_COLOR;
        };

    }

    public enum PowerUpTextState {
        NUMBER,
        NUMBER_WITH_MAX,
        PERCENT,
        NONE
    }

    public enum PowerUpStyle {
        CHARGING,
        READY
    }

    private IStarRailBossBar starRailBossBar = null;
    private float animationFade = 0;

    public void loadStarRailBossBar(IStarRailBossBar bossBar) {
        if(starRailBossBar == null) {
            currentHealthScale = 1;
            currentHealScale = 1;
            currentDamagedScale = 1;
            currentWeaknessScale = 1;
            currentDamagedWeaknessScale = 1;
            healScaleMoveSpeed = 0;
            weaknessScaleMoveSpeed = 0;
            bossBar.onStageEnter(1);
        }
        starRailBossBar = bossBar;
    }
    public IStarRailBossBar getStarRailBossBar() {
        return starRailBossBar;
    }

    private static final Color
            HEALTH_DAMAGED = new Color(0xF9, 0xCE, 0xCE, 255),
            HEALTH_JUST_DAMAGED = new Color(255, 255, 255, 255),
            HEALTH_HEAL = new Color(0x0E, 0xA7, 0x39, 255),
            STAGE = new Color(255, 115, 115, 255),
            STAGE_PASSED = new Color(0, 0, 0, 150),
            IMMUNE = new Color(255, 242, 100, 255),
            POWERUP = new Color(255, 80, 80, 255),
            NO_POWERUP = new Color(119, 119, 119, 255),
            ICON_ROUNDED = new Color(255, 255, 255, 100),
            ICON_BACKGROUND = new Color(0, 0, 0, 100);

    private double lastRecordHealth;
    private double lastHealth;
    private float animationGetDamaged;
    private float animationGetDamagedWeakness;
    private double currentHealthScale = 1;
    private double currentHealScale = 1;
    private double currentDamagedScale = 1;
    private double healScaleMoveSpeed = 0;

    private double currentWeaknessScale = 1;
    private double currentDamagedWeaknessScale = 1;
    private double weaknessScaleMoveSpeed = 0;

    private float powerupAnimation;

    private int lastRecordStage = 1;
    private int lastRecordStage2 = 1;
    private float stageAnimation = 1;

    private int lastRecordWeakness = 0;
    private double bossbarBobbing = 0;

    private void drawStarRailBossBar(GuiGraphics context, IStarRailBossBar bossBar, float partialTick) {
        if(bossBar == null) return;

        partialTick *= 3;
        if(animationGetDamaged > 0) animationGetDamaged = Math.max(0, animationGetDamaged - partialTick);
        if(animationGetDamagedWeakness > 0) animationGetDamagedWeakness = Math.max(0, animationGetDamagedWeakness - partialTick);
        if(lastHealth != bossBar.getHealth()) {
            if(animationGetDamaged == 0) {
                lastRecordHealth = lastHealth;
            }
            if(bossBar.getHealth() < lastHealth) {
                animationGetDamaged = 20;
                bossbarBobbing = Math.max(bossbarBobbing, 2);
            }
            lastHealth = bossBar.getHealth();
        }
        // currentHealthScale = bossBar.getHealth() / bossBar.getMaxHealth();
        currentHealScale = bossBar.getHealth() / bossBar.getMaxHealth();
        if(currentHealthScale < currentHealScale) {
            currentHealthScale += (healScaleMoveSpeed += 0.0006 * partialTick);
        }
        if(currentHealScale < currentHealthScale) {
            healScaleMoveSpeed = 0;
            currentHealthScale = currentHealScale;
        }

        double current = (double) bossBar.getWeakness() / bossBar.getMaxWeakness();
        if(lastRecordWeakness != bossBar.getWeakness()) {
            if(bossBar.getWeakness() < lastRecordWeakness) {
                animationGetDamagedWeakness = 20;
                bossbarBobbing = Math.max(bossbarBobbing, bossBar.getWeakness() == 0 ? 5 : 2);
            }
            lastRecordWeakness = bossBar.getWeakness();
        }
        if(animationGetDamagedWeakness == 0) {
            currentDamagedWeaknessScale -= (currentDamagedWeaknessScale - currentWeaknessScale) * 0.2 * partialTick;
        }
        if(currentWeaknessScale < current) {
            currentWeaknessScale += (weaknessScaleMoveSpeed += 0.0006 * partialTick);
        }
        if(current < currentWeaknessScale) {
            weaknessScaleMoveSpeed = 0;
            currentWeaknessScale = current;
        }

        double justDamagedEndScale = lastRecordHealth / bossBar.getMaxHealth();

        if(animationGetDamaged == 0) {
            currentDamagedScale -= (currentDamagedScale - currentHealthScale) * 0.2 * partialTick;
        }
        if(currentDamagedScale < currentHealthScale) currentDamagedScale = currentHealthScale;

        if(stageAnimation > 0) stageAnimation = Math.max(0, stageAnimation - partialTick);
        if(lastRecordStage != bossBar.getStage()) {
            lastRecordStage = bossBar.getStage();
            bossBar.onStageEnter(bossBar.getStage());
        }

        boolean hasWeakness = bossBar.hasWeakness();

        int width = mc.getWindow().getGuiScaledWidth();
        int length = 250;
        int x = width / 2 - length / 2;

        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        boolean bobbingFlag = bossbarBobbing > 0;
        if(bobbingFlag) {
            // 弹性效果：频率逐渐降低
            long time = System.currentTimeMillis();
            double frequency = 1.0f + bossbarBobbing;  // 频率随振幅减小
            double offsetX = (Math.sin(time / (80f * frequency)) * bossbarBobbing);
            double offsetY = (Math.cos(time / (100f * frequency)) * bossbarBobbing);

            // 使用缓动函数使效果更自然
            double easeFactor = Math.sin(bossbarBobbing * Math.PI);
            // GL11.glTranslated(offsetX * easeFactor, offsetY * easeFactor, 0);
            pose.translate((float) (offsetX * easeFactor), (float) (offsetY * easeFactor));
            bossbarBobbing *= 0.94;
        }

        icon:{
            int iconRadius = 12;
            RenderUtils.drawCircle(context, x - 3 - iconRadius, -16, 0, 360, iconRadius + 1, ICON_ROUNDED.getRGB());
            RenderUtils.drawCircle(context, x - 3 - iconRadius, -16, 0, 360, iconRadius, ICON_BACKGROUND.getRGB());
            if(bossBar.getHeadIcon() != null) {
                RenderUtils.drawCircleWithTexture(context, bossBar.getHeadIcon(), x - 3 - iconRadius, -16, iconRadius - 1);
            }
        }

        health_bar:{
            background:{
                context.fill(x - 1, -5, x + length + 1, hasWeakness ? -17 : -11, new Color(0x3D, 0x3D, 0x3D, 255).getRGB());
                if(bossBar.isImmune()) RenderUtils.drawOutlineRect(context, x, -5, x + length, -11, IMMUNE.getRGB());
            }

            bar:{
                context.fill(x, -10, x + length, -6, new Color(8, 8, 8, 255).getRGB());
                context.fill(x, -10, (int) (x + length * currentHealScale), -6, new Color(0x0E, 0xA7, 0x39, 255).getRGB());
                context.fill(x, -10, (int) (x + length * currentDamagedScale), -6, new Color(0xF9, 0xCE, 0xCE, 255).getRGB());
                context.fill((int) (x + length * currentHealthScale), -10, (int) (x + length * justDamagedEndScale), -6, new Color(255, 255, 255, (int) (255f * animationGetDamaged / 20f)).getRGB());
                context.fill(x, -10, (int) (x + length * currentHealthScale), -6, new Color(0xC1, 0x48, 0x30, 255).getRGB());
                if(hasWeakness) {
                    context.fill(x, -16, x + length, -12, new Color(8, 8, 8, 255).getRGB());
                    context.fill(x, -16, (int) (x + length * currentDamagedWeaknessScale), -12, new Color(0xD3, 0x91, 0x12, 255).getRGB());
                    context.fill(x, -16, (int) (x + length * currentWeaknessScale), -12, new Color(0xF0, 0xF4, 0xF3, 255).getRGB());
                }
            }

            text: {
                context.drawString(mc.font, bossBar.getDisplayName().getString(), x + (length - mc.font.width(bossBar.getDisplayName())) / 2, -17 - mc.font.lineHeight + (hasWeakness ? 0 : 4), 0xFFFFFFFF, true);
                String text1 = (int)(Math.floor((bossBar.getHealth() / bossBar.getMaxHealth() * 100 + 0.05) * 10) / 10) + "%";
                context.drawString(mc.font, text1, x + (length - mc.font.width(text1)) / 2, -8 - mc.font.lineHeight / 2, 0xFFFFFFFF, true);

                String text = "§a" + ToolList.getInstance().numberToEZString(bossBar.getHealth()) + "§c❤";
                context.drawString(mc.font, text, x + 50 + (length + mc.font.width(bossBar.getDisplayName())) / 2, -17 - mc.font.lineHeight + (hasWeakness ? 0 : 4), 0xFFFFFFFF, true);
            }
        }

        stage: if(bossBar.getMaxStage() > 1) {
            float offset = 3;
            for(int i = 0; i < bossBar.getMaxStage(); i++) {
                int stage = bossBar.getStage();
                if(bossBar.getHealth() <= 0) stage++;
                if(lastRecordStage2 != stage) {
                    if(stage > lastRecordStage2) stageAnimation = 20;
                    lastRecordStage2 = stage;
                }
                float x1 = x + offset;
                float y1 = -17 - mc.font.lineHeight / 2f + (hasWeakness ? 0 : 4);
                RenderUtils.drawCircle(context, x1, y1, 0, 360, 2, (i + stage > bossBar.getMaxStage() ? STAGE_PASSED : STAGE).getRGB());
                if(i + stage == bossBar.getMaxStage() + 1) {
                    RenderUtils.drawRoundedCircle(context, x1, y1, 0, 360, 21 - stageAnimation, 4, new Color(1, 1, 1, stageAnimation / 20f).getRGB());
                }
                offset += 8;
            }
        }

        powerup:{
            if(bossBar.isPowerUpAvaliable()) {
                float radius = 10;
                float x1 = x + length - radius;
                float y1 = -4 + radius;
                float potionWidth;
                float potionHeight = potionWidth = 20 / 1.414f;
                Runnable textDraw = () -> {
                    String text;
                    switch (bossBar.getPowerUpTextState()) {
                        case NUMBER_WITH_MAX:
                            text = bossBar.getPowerUp() + "/" + bossBar.getMaxPowerUp();
                            break;
                        case NUMBER:
                            text = String.valueOf(bossBar.getPowerUp());
                            break;
                        case PERCENT:
                            text = (bossBar.getPowerUp() * 100 / bossBar.getMaxPowerUp()) + "%";
                            break;
                        case NONE:
                        default:
                            return;
                    }
                    context.drawString(mc.font, text, (int) (x1 - (float) mc.font.width(text) / 2), (int) (y1 + radius + 3), 0xFFFFFFFF, true);
                };
                if(bossBar.isPowerUp()) {
                    powerupAnimation -= 6 * partialTick;
                    RenderUtils.drawCircle(context, x1, y1, 0, 360, radius, bossBar.getPwoerUpColor().getRGB());
                    context.blitSprite(RenderPipelines.GUI_TEXTURED, bossBar.getPowerUpPotionIcon(), (int) (x1 - potionWidth / 2), (int) (y1 - potionHeight / 2), (int) potionWidth + 2, (int) potionHeight + 2);
                    RenderUtils.drawRoundedCircleFadeToNoAlpha(context, x1, y1, (int) powerupAnimation, (int) (150 + powerupAnimation), radius - 2, 2, Color.white.getRGB(), true);
                } else {
                    RenderUtils.drawCircle(context, x1, y1, 0, 360, radius, NO_POWERUP.getRGB());
                    context.blitSprite(RenderPipelines.GUI_TEXTURED, bossBar.getPowerUpPotionIcon(), (int) (x1 - potionWidth / 2), (int) (y1 - potionHeight / 2), (int) potionWidth + 2, (int) potionHeight + 2);
                    if(bossBar.getPowerUpStyle() == PowerUpStyle.CHARGING) {
                        RenderUtils.drawRoundedCircle(context, x1, y1, 0, 360, radius - 2, 2, ICON_BACKGROUND.getRGB());
                        RenderUtils.drawRoundedCircle(context, x1, y1, -90, -91 + 361 * bossBar.getPowerUp() / bossBar.getMaxPowerUp(), radius - 2, 2, bossBar.getPwoerUpColor().getRGB());
                    }
                }
                textDraw.run();
            }
        }
        pose.popMatrix();
    }

}