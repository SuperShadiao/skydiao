package pers.XiaoShadiao.skydiao.utils.pathfinder;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class EntityFollower extends Thread {

    private volatile boolean stopped;

    public void stop0() {
        stopped = true;
    }

    public record NameInfo(String name, boolean isEqual) {
        public @NotNull String toString() {
            return name;
        }
    }

    private int blacklistCounter;
    private final List<Entity> blacklistEntities = new ArrayList<>();
    private Vec3 lastPlayerPos = new Vec3(0, 0, 0);
    private final int swordSlot;
    private final List<NameInfo> names;
    private final List<NameInfo> avoidNames;
    public final boolean tryAttack;
    public volatile Entity currentEntity;
    public volatile boolean isAimingToEntity;
    private double distanceToEntity;
    private boolean blacklistUnreachable;
    private EntityFollowerConfig customConfig;

    public EntityFollower(List<NameInfo> names, boolean tryAttack, boolean blacklistUnreachable, EntityFollowerConfig customConfig) {
        this(names, Collections.emptyList(), tryAttack, blacklistUnreachable, customConfig);
    }

    public EntityFollower(List<NameInfo> names, List<NameInfo> avoidNames, boolean tryAttack, boolean blacklistUnreachable, EntityFollowerConfig customConfig) {
        this.names = names;
        this.avoidNames = avoidNames;
        this.tryAttack = tryAttack;
        this.swordSlot = ToolList.mc.player.getInventory().getSelectedSlot();
        this.blacklistUnreachable = blacklistUnreachable;
        this.customConfig = customConfig;

        setName("EntityFollower");
    }

    @Override
    public void run() {

        PathFinder.registerConfig(new PathFinder.ICustomPathfinderConfig() {
            @Override
            public boolean isAllowBreak() {
                return PathFinder.isConfigAllowBreak();
            }

            @Override
            public boolean isAllowPlace() {
                return PathFinder.isConfigAllowPlace();
            }

            @Override
            public boolean shouldUnregister() {
                return !EntityFollower.this.isAlive();
            }

            @Override
            public int getDepth() {
                return 10000;
            }

            @Override
            public long getTimeout() {
                int i = ((int)(distanceToEntity / 7) + 1) * 100;
                if(distanceToEntity < 5) return 5;
                return Math.max(Math.min(PathFinder.getConfigTimeout(), i), 500);
            }

            @Override
            public boolean shouldPause() {
                return false;
            }

            @Override
            public boolean shouldStopWhenRecieveS08() {
                return false;
            }
        });
        int giveMissHit = 0;
        long keepTime;
        a:while(!stopped) {
            if(InputSimulator.isInventoryOpen()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {

                }
                continue;
            }
            try {
                if(distanceToEntity < 1) blacklistCounter = 0;
                blacklistEntities.removeIf(a -> !ToolList.getInstance().isEntityOnWorld(a) || a.distanceTo(ToolList.mc.player) < 1);
            } catch (ConcurrentModificationException e) {

            }

            if(ToolList.mc.level == null) throw new NullPointerException("World is null");

            Optional<Entity> entity = Optional.empty();
            try {

                List<Entity> shouldAvoidEntities = StreamSupport.stream(ToolList.mc.level.entitiesForRendering().spliterator(), false)
                        .filter(a -> avoidNames.stream().anyMatch(b -> (b.isEqual ? a.getName().getString().equalsIgnoreCase(b.name) : a.getName().getString().toLowerCase().contains(b.name.toLowerCase()))))
                        .toList();
                // boolean hasChildWolf = ToolList.mc.theWorld.loadedEntityList.stream().anyMatch(e -> e instanceof EntityWolf && ToolList.mc.player.getDistanceSqToEntity(e) < 15*15 && ((EntityWolf) e).isChild());
                entity = StreamSupport.stream(ToolList.mc.level.entitiesForRendering().spliterator(), false)
                        .filter(a -> !(a instanceof ArmorStand && (a.getName().getString().contains("❤"))) && names.stream().anyMatch(b -> (b.isEqual ? a.getName().getString().equalsIgnoreCase(b.name) : a.getName().getString().toLowerCase().contains(b.name.toLowerCase())) && !blacklistEntities.contains(a)) && a != ToolList.mc.player && (!(a instanceof LivingEntity e) || e.getHealth() > 0))
                        .filter(a -> shouldAvoidEntities.stream().noneMatch(b -> b.equals(a) || a.distanceTo(b) < 10))
                        .min(Comparator.comparingDouble(new DistanceComparator()));
            } catch (Exception e) {
                e.printStackTrace();
            }
            // ToolList.getInstance().log.info("Following: " + entity);

            if(entity.isPresent()) {
                Entity e = entity.get();
                if(Optional.ofNullable(customConfig).map(c -> c.shouldIgnore).map(p -> p.test(e)).orElse(false)) {
                    blacklistEntities.add(e);
                    continue;
                }

                if((tryAttack || e.distanceToSqr(ToolList.mc.player) > 2) && (/*!tryAttack || */!isAimingToEntity || !MacroManagerListener.pathFinderExecutor.isRunning())) {

                    Vec3 newVec = new Vec3(ToolList.mc.player.getX(), ToolList.mc.player.getY(), ToolList.mc.player.getZ());
                    if(tryAttack && blacklistUnreachable && e == currentEntity && !(e instanceof AbstractClientPlayer) && lastPlayerPos.horizontal().distanceTo(newVec.horizontal()) < 0.25 && lastPlayerPos.distanceToSqr(e.getX(), e.getY(), e.getZ()) > 2) {
                        if(blacklistCounter > 3) {
                            blacklistEntities.add(e);
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c无法跟踪实体" + entity + ", 已加入黑名单"));
                        }
                        blacklistCounter++;
                    } else blacklistCounter = 0;
                    lastPlayerPos = newVec;
                    MacroManagerListener.pathFinderExecutor.startExecution(new Vec3(e.getX(), e.getY(), e.getZ()), false);
                }
                currentEntity = e;
                distanceToEntity = e.distanceTo(ToolList.mc.player);
                keepTime = System.currentTimeMillis();
                do {
                    try {
                        if(giveMissHit > 0 && ToolList.getInstance().random.nextBoolean() && ToolList.getInstance().random.nextBoolean()) {
                            InputSimulator.switchItem(swordSlot);
                            InputSimulator.singleLeftClick();
                            giveMissHit--;
                        }

                        if(giveMissHit < 5 && ToolList.getInstance().random.nextBoolean() && ToolList.mc.hitResult.getType() == HitResult.Type.ENTITY) {
                            if (tryAttack) {
                                InputSimulator.switchItem(swordSlot);
                                InputSimulator.singleLeftClick();
                                giveMissHit = 10;
                            }
                        }
                        Thread.sleep(10 + ToolList.getInstance().random.nextInt(30));
                        try {
                            List<Entity> shouldAvoidEntities = StreamSupport.stream(ToolList.mc.level.entitiesForRendering().spliterator(), false)
                                    .filter(a -> avoidNames.stream().anyMatch(b -> (b.isEqual ? a.getName().getString().equalsIgnoreCase(b.name) : a.getName().getString().toLowerCase().contains(b.name.toLowerCase()))))
                                    .toList();
                            if(
                                    (currentEntity instanceof LivingEntity livingEntity && (livingEntity.getHealth() <= 0) || !ToolList.getInstance().isEntityOnWorld(e)) ||
                                            (
                                                    (!tryAttack || !isAimingToEntity) &&
                                                            System.currentTimeMillis() - keepTime > (tryAttack ? 3000 : 800) &&
                                                            (
                                                                    !e.equals(StreamSupport.stream(ToolList.mc.level.entitiesForRendering().spliterator(), false)
                                                                            .filter(a -> names.stream().anyMatch(b -> b.isEqual ? a.getName().getString().equalsIgnoreCase(b.name) : a.getName().getString().toLowerCase().contains(b.name.toLowerCase())))
                                                                            .filter(a -> shouldAvoidEntities.stream().noneMatch(b -> b.equals(a) || a.distanceToSqr(b) < 100))
                                                                            .min(Comparator.comparingDouble(new DistanceComparator())
                                                                            ).orElse(e)) ||
                                                                            (e.distanceToSqr(new Vec3(MacroManagerListener.pathFinderExecutor.getGoalBlock())) > (MacroManagerListener.pathFinderExecutor.pathblocks.size() < 6 ? 2 : 16) &&
                                                                                    MacroManagerListener.pathFinderExecutor.pathblocks.size() < 50)
                                                            )
                                            )
                            ) {
                                MacroManagerListener.pathFinderExecutor.finishTaskQuickly();
                                continue a;
                            }
                        } catch (NullPointerException ex) {
                            ex.printStackTrace();
                        }
                    } catch (InterruptedException | ConcurrentModificationException ee) {

                    }
                } while(!MacroManagerListener.pathFinderExecutor.isSleeping() && !stopped);

                if(ToolList.getInstance().random.nextBoolean() && (ToolList.mc.hitResult.getType() == HitResult.Type.BLOCK))  {
                    if (tryAttack) InputSimulator.singleLeftClick();
                }
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {

            }
        }

    }

    private static double getSpecialDistanceSq(Entity e1, Entity e2, boolean expandY) {
        double dx = e1.getX() - e2.getX();
        double dy = e1.getY() - e2.getY();
        double dz = e1.getZ() - e2.getZ();
        if(expandY) dy *= 4.2;
        return dx * dx + dy * dy + dz * dz;
    }

    private class DistanceComparator implements ToDoubleFunction<Entity> {

        @Override
        public double applyAsDouble(Entity value) {

            Double transfer = Optional.ofNullable(customConfig).map(EntityFollowerConfig::getDistanceTransfer).map(f -> f.apply(value)).orElse(null);
            if(transfer != null) return transfer;

            boolean flag = true;
            if(value.getY() < ToolList.mc.player.getY()) {
                if(ToolList.getInstance().predictPlayerAimBlock(value) == null) flag = false;
            }

            double dst = getSpecialDistanceSq(value, ToolList.mc.player, flag); // e2.getDistanceSqToEntity(ToolList.mc.player);

            boolean hasChildWolf = StreamSupport.stream(ToolList.mc.level.entitiesForRendering().spliterator(), false)
                    .anyMatch(e -> e instanceof Wolf wolf && ToolList.mc.player.distanceTo(e) < 15 && wolf.isBaby());

            if(hasChildWolf) {
                if(value instanceof Wolf wolf && !wolf.isBaby()) dst += 9999999;
            }

            return dst;
        }

    }

    public static class EntityFollowerConfig {

        private Predicate<Entity> shouldIgnore = null;
        public EntityFollowerConfig setShouldIgnore(Predicate<Entity> shouldIgnore) {
            this.shouldIgnore = shouldIgnore;
            return this;
        }
        public Predicate<Entity> getShouldIgnore() {
            return shouldIgnore;
        }

        // return null to continue using default distance
        private Function<Entity, Double> distanceTransfer = null;
        public EntityFollowerConfig setDistanceTransfer(Function<Entity, Double> distanceTransfer) {
            this.distanceTransfer = distanceTransfer;
            return this;
        }
        public Function<Entity, Double> getDistanceTransfer() {
            return distanceTransfer;
        }

    }
}
