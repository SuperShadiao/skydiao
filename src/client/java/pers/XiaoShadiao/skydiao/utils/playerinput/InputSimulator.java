package pers.XiaoShadiao.skydiao.utils.playerinput;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.mixin.client.MixinMultiPlayerGameModeDestroyBlockDelayAccessor;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class InputSimulator {

    public static final Minecraft mc = ToolList.mc;
    public static int missTime;
    public static int rightClickDelay;

    public static int leftClickCounter;
    public static int rightClickCounter;

    public static boolean isMouseLeftHolding;
    public static boolean isMouseRightHolding;

    private static final Thread leftClickThread;
    private static final Thread rightClickThread;

    private static AimHelper currentAimingInstance;

    static {
        leftClickThread = new Thread(() -> clicker(InputSimulator::pressLeftClick, InputSimulator::releaseLeftClick), "Left Clicker Thread");
        leftClickThread.start();
        rightClickThread = new Thread(() -> clicker(InputSimulator::pressRightClick, InputSimulator::releaseRightClick), "Right Clicker Thread");
        rightClickThread.start();
    }

    private static void clicker(Runnable press, Runnable release) {
        boolean flag = true;
        while (true) {
            if(flag) {
                try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) {}
            }
            flag = true;
            press.run();
            try { Thread.sleep(60); } catch (InterruptedException e) {
                release.run();
                flag = false;
            }
            release.run();
        }
    }

    public static void singleLeftClick() {
        leftClickThread.interrupt();
    }

    public static void singleRightClick() {
        rightClickThread.interrupt();
    }

    public static void pressLeftClick() {
        if(CustomFabricEvents.ON_SIMULATOR_CLICK.invoker().onSimulatorClick(CustomFabricEvents.ClickType.LEFT)) return;

        if(isInventoryOpen()) return;

        if (!isMouseLeftHolding) leftClickCounter++;
        isMouseLeftHolding = true;
    }

    public static void pressRightClick() {
        if(CustomFabricEvents.ON_SIMULATOR_CLICK.invoker().onSimulatorClick(CustomFabricEvents.ClickType.RIGHT)) return;

        if(isInventoryOpen()) return;

        if (!isMouseRightHolding) rightClickCounter++;
        isMouseRightHolding = true;
    }

    public static boolean hasRemainRightClick() {
        return rightClickCounter > 0;
    }

    public static void releaseLeftClick() {
        isMouseLeftHolding = false;
    }

    public static void releaseRightClick() {
        isMouseRightHolding = false;
    }

    public static void updateTick() {
        updateInventoryState();
        if(preTick()) {
            midTick();
            postTick();
        }
    }

    private static int lastBreakDelay;

    private static boolean preTick() {
        if (rightClickDelay > 0) {
            rightClickDelay--;
        }
        int currentDestroyDelay = ((MixinMultiPlayerGameModeDestroyBlockDelayAccessor) mc.gameMode).getDestroyDelay();
        if(lastBreakDelay == 0 && currentDestroyDelay == 5) {
            ((MixinMultiPlayerGameModeDestroyBlockDelayAccessor) mc.gameMode).setDestroyDelay(6);
        }
        lastBreakDelay = currentDestroyDelay;
        if (mc.gui.overlay() != null || isInventoryOpen()) {
            missTime = 999999999;
            isMouseLeftHolding = isMouseRightHolding = false;
            leftClickCounter = rightClickCounter = 0;
            return false;
        }
        return true;
    }

    public static boolean leftClickFlagMinecraft;

    private static void midTick() {
        boolean bl3 = false;
        updateCurrentItemSlotIndex();
        if (mc.player.isUsingItem()) {
            if (!mc.options.keyUse.isDown() && !isMouseRightHolding) {
                mc.gameMode.releaseUsingItem(mc.player);
            }

            leftClickCounter = 0;
            rightClickCounter = 0;
        } else {
            while (leftClickCounter > 0) {
                System.out.println("click");
                leftClickCounter--;
                if(!mc.gameMode.isDestroying() && (((MixinMultiPlayerGameModeDestroyBlockDelayAccessor) mc.gameMode).getDestroyDelay() <= 1 || !(mc.hitResult instanceof BlockHitResult result) || result.getType() != HitResult.Type.BLOCK)) bl3 |= startAttack();
            }

            while (rightClickCounter > 0) {
                rightClickCounter--;
                startUseItem();
            }
        }

        if(mc.gui.screen() != null) {
            if (isMouseRightHolding && rightClickDelay == 0 && !mc.player.isUsingItem()) {
                startUseItem();
            }
        }
        leftClickFlagMinecraft = !isInventoryOpen() && !bl3 && isMouseLeftHolding;
        if(mc.gui.screen() != null) continueAttack(leftClickFlagMinecraft);
    }

    private static void postTick() {
        if (missTime > 0) {
            missTime--;
        }
    }

    public static void continueAttack(final boolean down) {
        if (!down) {
            missTime = 0;
        }

        if (missTime <= 0 && !mc.player.isUsingItem()) {
            ItemStack heldItem = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
            if (!heldItem.has(DataComponents.PIERCING_WEAPON)) {
                SwingAnimation swingAnimation = heldItem.getAttackAnimation();
                if (down && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult)mc.hitResult;
                    BlockPos pos = blockHit.getBlockPos();
                    if (!mc.level.getBlockState(pos).isAir()) {
                        Direction direction = blockHit.getDirection();
                        if (mc.gameMode.continueDestroyBlock(pos, direction)) {
                            mc.player.swing(InteractionHand.MAIN_HAND, swingAnimation, false);
                            mc.player.connection.send(ServerboundPunchPacket.INSTANCE);
                        }
                    }
                } else {
                    mc.gameMode.stopDestroyBlock();
                }
            }
        }
    }

    private static boolean startAttack() {
        if (missTime > 0) {
            return false;
        } else if (mc.hitResult == null) {
            ToolList.getInstance().log.error("Null returned as 'hitResult', mc shouldn't happen!");
            if (mc.gameMode.hasMissTime()) {
                missTime = 10;
            }

            return false;
        } else {
            if (mc.player.isHandsBusy()) {
                return false;
            }

            if (mc.gameMode.isSpectator()) {
                if (mc.hitResult instanceof EntityHitResult entityHitResult) {
                    mc.gameMode.spectate(entityHitResult.getEntity());
                } else {
                    mc.gameMode.spectatorNoAction();
                }

                return true;
            } else {
                ItemStack heldItem = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
                if (!heldItem.isItemEnabled(mc.level.enabledFeatures())) {
                    return false;
                }

                if (mc.player.cannotAttackWithItem(heldItem, 0)) {
                    return false;
                }

                SwingAnimation attackAnimation = heldItem.getAttackAnimation();
                boolean endAttack = false;
                PiercingWeapon piercingWeapon = (PiercingWeapon)heldItem.get(DataComponents.PIERCING_WEAPON);
                if (piercingWeapon != null) {
                    mc.gameMode.piercingAttack(attackAnimation, piercingWeapon);
                    return true;
                }

                switch (mc.hitResult.getType()) {
                    case ENTITY:
                        AttackRange customItemRange = (AttackRange)heldItem.get(DataComponents.ATTACK_RANGE);
                        if (customItemRange == null || customItemRange.isInRange(mc.player, mc.hitResult.getLocation())) {
                            mc.gameMode.attack(mc.player, ((EntityHitResult)mc.hitResult).getEntity());
                        }
                        break;
                    case BLOCK:
                        BlockHitResult blockHit = (BlockHitResult)mc.hitResult;
                        BlockPos pos = blockHit.getBlockPos();
                        if (!mc.level.getBlockState(pos).isAir()) {
                            mc.gameMode.startDestroyBlock(pos, blockHit.getDirection());
                            if (mc.level.getBlockState(pos).isAir()) {
                                endAttack = true;
                            }
                            break;
                        }
                    case MISS:
                        if (mc.gameMode.hasMissTime()) {
                            missTime = 10;
                        }

                        mc.player.resetAttackStrengthTicker();
                }

                mc.player.swing(InteractionHand.MAIN_HAND, attackAnimation, false);
                mc.player.connection.send(ServerboundPunchPacket.INSTANCE);
                return endAttack;
            }
        }
    }

    private static void startUseItem() {
        if (!mc.gameMode.isDestroying()) {
            rightClickDelay = 4;
            if (!mc.player.isHandsBusy()) {
                if (mc.hitResult == null) {
                    ToolList.getInstance().log.warn("Null returned as 'hitResult', mc shouldn't happen!");
                }

                for (InteractionHand hand : InteractionHand.values()) {
                    ItemStack heldItem = mc.player.getItemInHand(hand);
                    if (!heldItem.isItemEnabled(mc.level.enabledFeatures())) {
                        return;
                    }

                    SwingAnimation swingAnimation = heldItem.getInteractAnimation();
                    if (mc.hitResult != null) {
                        switch (mc.hitResult.getType()) {
                            case ENTITY:
                                EntityHitResult entityHit = (EntityHitResult)mc.hitResult;
                                Entity entity = entityHit.getEntity();
                                if (!mc.level.getWorldBorder().isWithinBounds(entity.blockPosition())) {
                                    return;
                                }

                                if (mc.player.isWithinEntityInteractionRange(entity, 0.0) && mc.gameMode.interact(mc.player, entity, entityHit, hand) instanceof InteractionResult.Success success) {
                                    if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
                                        mc.player.swing(hand, swingAnimation, false);
                                    }

                                    return;
                                }
                                break;
                            case BLOCK:
                                BlockHitResult blockHit = (BlockHitResult)mc.hitResult;
                                int oldCount = heldItem.getCount();
                                InteractionResult useResult = mc.gameMode.useItemOn(mc.player, hand, blockHit);
                                if (useResult instanceof InteractionResult.Success success) {
                                    if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
                                        mc.player.swing(hand, swingAnimation, false);
                                        if (!heldItem.isEmpty() && (heldItem.getCount() != oldCount || mc.player.hasInfiniteMaterials())) {
                                            mc.player.itemUsed(hand);
                                        }
                                    }

                                    return;
                                }

                                if (useResult instanceof InteractionResult.Fail) {
                                    return;
                                }
                        }
                    }

                    if (!heldItem.isEmpty() && mc.gameMode.useItem(mc.player, hand) instanceof InteractionResult.Success success) {
                        if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
                            mc.player.swing(hand, swingAnimation, false);
                        }

                        mc.player.itemUsed(hand);
                        return;
                    }
                }
            }
        }
    }

    private static boolean isInventoryOpen;

    public static boolean isInventoryOpen() {
        return isInventoryOpen;
    }

    private static void updateInventoryState() {
        if (mc.gui.screen() instanceof AbstractContainerScreen || mc.gui.screen() instanceof BookEditScreen || mc.gui.screen() instanceof BookSignScreen || mc.gui.screen() instanceof BookViewScreen) {
            isInventoryOpen = true;
        } else if(mc.gui.screen() == null) {
            isInventoryOpen = false;
        }
    }

    private static float prepareYaw;
    private static boolean hasYawValue;
    private static float preparePitch;
    private static boolean hasPitchValue;

    public static void setPlayerYaw(float yaw) {
        prepareYaw = yaw;
        hasYawValue = true;
    }

    public static void setPlayerPitch(float pitch) {
        preparePitch = pitch;
        hasPitchValue = true;
    }

    public static void stopAiming() {
        currentAimingInstance = null;
    }

    public static void updatePlayerRotation() {
        if (currentAimingInstance != null) {
            if(currentAimingInstance.shouldUpdate()) {
                currentAimingInstance.updateRotation();
            } else {
                currentAimingInstance = null;
            }
        }
        if (mc.gui.overlay() != null || isInventoryOpen()) {
            hasYawValue = false;
            hasPitchValue = false;
            return;
        }
        if(mc.player != null) {
            boolean flag = getPlayerYaw() != prepareYaw;
            if(hasYawValue) {
                mc.player.setYRot(fun(getPlayerYaw(), prepareYaw));
                hasYawValue = false;
            }
            if(hasPitchValue) {
                float from = getPlayerPitch();
                float to = preparePitch;

                float delta = to - from;
                if(flag) delta += (ToolList.getInstance().random.nextFloat() - 0.5f) / 5;

                float f1 = (float) (0.8 / 7);

                int moveCount = (int) (delta / f1);
                to = from + (moveCount + ToolList.getInstance().random.nextInt(3) - 1) * f1;
                if(Math.abs(delta) > 0.1) mc.player.setXRot(to);
                hasPitchValue = false;
            }
        }
        actuallyYaw = getPlayerYaw0();
        actuallyPitch = getPlayerPitch0();
    }

    private static float actuallyYaw;
    private static float actuallyPitch;

    public static void updateAimHelper(AimHelper aimHelper) {
        currentAimingInstance = aimHelper;
    }

    private static float fun(float start, float target) {
        // 计算 target 与 start 的差值
        float delta = target - start;

        // 将差值调整到 [-180, 180] 范围
        delta = normalizeAngle(delta);

        // 返回与 target 等价且最接近 start 的值
        float result = start + delta;
        return result;
    }

    /**
     * 将角度归一化到 [-180, 180] 范围。
     */
    private static float normalizeAngle(float angle) {
        // 将角度调整到 [-360, 360] 范围
        angle = angle % 360;

        // 进一步调整到 [-180, 180] 范围
        if (angle > 180) {
            angle -= 360;
        } else if (angle < -180) {
            angle += 360;
        }

        return angle;
    }

    public static float getPlayerYaw() {
        return actuallyYaw;
    }

    public static float getPlayerPitch() {
        return actuallyPitch;
    }

    private static float getPlayerYaw0() {
        return mc.player == null ? 0 : mc.player.getYRot();
    }

    private static float getPlayerPitch0() {
        return mc.player == null ? 0 : mc.player.getXRot();
    }

    public static boolean left;
    public static boolean right;
    public static boolean forward;
    public static boolean backward;
    public static boolean jump;
    public static boolean shift;
    public static boolean sprint;

    public static void setLeft(boolean b) {
        left = b;
    }

    public static void setRight(boolean b) {
        right = b;
    }

    public static void setForward(boolean b) {
        forward = b;
    }

    public static void setBackward(boolean b) {
        backward = b;
    }

    public static void setJump(boolean b) {
        jump = b;
    }

    public static void setShift(boolean b) {
        shift = b;
    }

    public static void setSprint(boolean b) {
        sprint = b;
    }

    public static int currentItemSlotIndex = -1;
    public static boolean hasCurrentItemSlotIndex = false;

    public static int getCurrentItemSlotIndex() {
        return mc.player != null ? mc.player.getInventory().getSelectedSlot() : -1;
    }

    public static void switchItem(int index) {
        currentItemSlotIndex = index;
        hasCurrentItemSlotIndex = true;
    }

    private static void updateCurrentItemSlotIndex() {
        if(hasCurrentItemSlotIndex) {
            hasCurrentItemSlotIndex = false;
            if(mc.player != null) mc.player.getInventory().setSelectedSlot(currentItemSlotIndex);
        }
    }

    public static void releaseAllKey() {
        unpressAllKey();
    }

    public static void unpressAllKey() {
        left = false;
        right = false;
        forward = false;
        backward = false;
        jump = false;
        shift = false;
        sprint = false;
        isMouseLeftHolding = false;
        isMouseRightHolding = false;
    }

    public static List<Direction> tryNoViewChangeControlMoveTo(BlockPos pos, double requireDist) {
        Vec3 playerPos = mc.player.position();
        Direction forward = mc.player.getDirection();
        Direction right = forward.getClockWise();
        Direction backward = right.getClockWise();
        Direction left = backward.getClockWise();
        List<Direction> activeDirections = new ArrayList<>();

        Map<Direction, Consumer<Boolean>> directionInput = Map.of(
                forward,
                (Consumer<Boolean>) InputSimulator::setForward,
                right,
                (Consumer<Boolean>) InputSimulator::setRight,
                backward,
                (Consumer<Boolean>) InputSimulator::setBackward,
                left,
                (Consumer<Boolean>) InputSimulator::setLeft
        );

        Vec3 target = Vec3.atBottomCenterOf(pos);

        for (Map.Entry<Direction, Consumer<Boolean>> entry : directionInput.entrySet()) {
            Vec3 original = playerPos;
            Vec3 to = playerPos.relative(entry.getKey(), requireDist);

            double distFrom = target.distanceTo(original);
            double distTo = target.distanceTo(to);
            boolean active = distTo < distFrom;
            entry.getValue().accept(active);
            if(active) {
                activeDirections.add(entry.getKey());
            }
        }
        return activeDirections;
    }

}
