package pers.XiaoShadiao.skydiao.appendage.utils.hitresult;

import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

import java.util.Collection;
import java.util.function.Predicate;

public class PUtil {

    /**
     * 只是将minecraft获取选中方块/实体逻辑拷贝并稍作修改：将玩家与其头部旋转角分离
     * @param player 玩家
     * @param xRot 竖直角
     * @param yHeadRot 水平角
     * @return HitResult
     */
    public static HitResult hitResultByAngle(LocalPlayer player, float xRot, float yHeadRot){
        return raycastHitResult(
                1.0f, Minecraft.getInstance().getCameraEntity(), new FakeEntity(xRot, yHeadRot), player
        );
    }

    public static HitResult raycastHitResult(final float a, final Entity cameraEntity, final FakeEntity fakeCamera, LocalPlayer player) {
        ItemStack itemStack = player.getActiveItem();
        AttackRange itemAttackRange = (AttackRange)itemStack.get(DataComponents.ATTACK_RANGE);
        double blockInteractionRange = player.blockInteractionRange();
        HitResult hitResult = null;
        if (itemAttackRange != null) {
            hitResult = getClosesetHit(cameraEntity, fakeCamera, a, EntitySelector.CAN_BE_PICKED, itemAttackRange);
            if (hitResult instanceof BlockHitResult) {
                hitResult = filterHitResult(hitResult, cameraEntity.getEyePosition(a), blockInteractionRange);
            }
        }

        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            double entityInteractionRange = player.entityInteractionRange();
            hitResult = pick(cameraEntity, blockInteractionRange, entityInteractionRange, a, fakeCamera);
        }

        return hitResult;
    }

    private static HitResult pick(final Entity cameraEntity, final double blockInteractionRange, final double entityInteractionRange, final float partialTicks, final FakeEntity fakeCamera) {
        double maxDistance = Math.max(blockInteractionRange, entityInteractionRange);
        double maxDistanceSq = Mth.square(maxDistance);
        Vec3 from = cameraEntity.getEyePosition(partialTicks);
        HitResult blockHitResult = pick2(maxDistance, partialTicks, false, cameraEntity, fakeCamera);
        double blockDistanceSq = blockHitResult.getLocation().distanceToSqr(from);
        if (blockHitResult.getType() != HitResult.Type.MISS) {
            maxDistanceSq = blockDistanceSq;
            maxDistance = Math.sqrt(blockDistanceSq);
        }

        Vec3 direction = cameraEntity.getViewVector(partialTicks);
        Vec3 to = from.add(direction.x * maxDistance, direction.y * maxDistance, direction.z * maxDistance);
        float overlap = 1.0F;
        AABB box = cameraEntity.getBoundingBox().expandTowards(direction.scale(maxDistance)).inflate(1.0, 1.0, 1.0);
        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(cameraEntity, from, to, box, EntitySelector.CAN_BE_PICKED, maxDistanceSq);
        return entityHitResult != null && entityHitResult.getLocation().distanceToSqr(from) < blockDistanceSq
                ? filterHitResult(entityHitResult, from, entityInteractionRange)
                : filterHitResult(blockHitResult, from, blockInteractionRange);
    }

    public static HitResult pick2(final double range, final float a, final boolean withLiquids, final Entity cameraEntity, final FakeEntity fakeCamera) {
        Vec3 from = cameraEntity.getEyePosition(a);
        Vec3 viewVector = fakeCamera.getViewVector(a);
        Vec3 to = from.add(viewVector.x * range, viewVector.y * range, viewVector.z * range);
        return cameraEntity.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, withLiquids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, cameraEntity));
    }


    private static HitResult filterHitResult(final HitResult hitResult, final Vec3 from, final double maxRange) {
        Vec3 hitLocation = hitResult.getLocation();
        if (!hitLocation.closerThan(from, maxRange)) {
            Vec3 location = hitResult.getLocation();
            Direction direction = Direction.getApproximateNearest(location.x - from.x, location.y - from.y, location.z - from.z);
            return BlockHitResult.miss(location, direction, BlockPos.containing(location));
        } else {
            return hitResult;
        }
    }


    public static HitResult getClosesetHit(final Entity attacker, final FakeEntity fakeAttacker, final float partial, final Predicate<Entity> matching, AttackRange attackRange) {
        Either<BlockHitResult, Collection<EntityHitResult>> result = ProjectileUtil.getHitEntitiesAlong(attacker, attackRange, matching, ClipContext.Block.OUTLINE);
        if (result.left().isPresent()) {
            return (HitResult)result.left().get();
        } else {
            Collection<EntityHitResult> targets = (Collection<EntityHitResult>)result.right().get();
            EntityHitResult entity = null;
            Vec3 attackerPos = attacker.getEyePosition(partial);
            double closestDistance = Double.MAX_VALUE;

            for (EntityHitResult target : targets) {
                double distance = attackerPos.distanceToSqr(target.getLocation());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    entity = target;
                }
            }

            if (entity != null) {
                return entity;
            } else {
                Vec3 eyeGaze = attacker.getHeadLookAngle();
                Vec3 missPosition = attacker.getEyePosition(partial).add(eyeGaze);
                return BlockHitResult.miss(missPosition, Direction.getApproximateNearest(eyeGaze), BlockPos.containing(missPosition));
            }
        }
    }

    public static Either<BlockHitResult, Collection<EntityHitResult>> getHitEntitiesAlong(
            final Entity attacker, final FakeEntity fakeAttacker, final AttackRange attackRange, final Predicate<Entity> matching, final ClipContext.Block blockClipType
    ) {
        //FAKE attacker
        Vec3 look = fakeAttacker.getHeadLookAngle();

        Vec3 eyePosition = attacker.getEyePosition();
        Vec3 from = eyePosition.add(look.scale(attackRange.effectiveMinRange(attacker)));
        double movementComponent = attacker.getKnownMovement().dot(look);
        Vec3 to = eyePosition.add(look.scale(attackRange.effectiveMaxRange(attacker) + Math.max(0.0, movementComponent)));
        return getHitEntitiesAlong(attacker, eyePosition, from, matching, to, attackRange.hitboxMargin(), blockClipType);
    }


    private static Either<BlockHitResult, Collection<EntityHitResult>> getHitEntitiesAlong(
            final Entity source,
            final Vec3 origin,
            final Vec3 from,
            final Predicate<Entity> matching,
            Vec3 to,
            final float entityMargin,
            final ClipContext.Block clipType
    ) {
        Level level = source.level();
        BlockHitResult hitResult = level.clipIncludingBorder(new ClipContext(origin, to, clipType, ClipContext.Fluid.NONE, source));
        if (hitResult.getType() != HitResult.Type.MISS) {
            to = hitResult.getLocation();
            if (origin.distanceToSqr(to) < origin.distanceToSqr(from)) {
                return Either.left(hitResult);
            }
        }

        AABB searchArea = AABB.ofSize(from, entityMargin, entityMargin, entityMargin).expandTowards(to.subtract(from)).inflate(1.0);
        Collection<EntityHitResult> entityHit = ProjectileUtil.getManyEntityHitResult(level, source, from, to, searchArea, matching, entityMargin, clipType, true);
        return !entityHit.isEmpty() ? Either.right(entityHit) : Either.left(hitResult);
    }



}
