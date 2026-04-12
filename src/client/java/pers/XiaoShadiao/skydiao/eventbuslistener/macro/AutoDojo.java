package pers.XiaoShadiao.skydiao.eventbuslistener.macro;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.E2AMappingListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.AimHelper;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class AutoDojo extends AbstractListener implements IMacro {

    private DojoType currentDojo = DojoType.NONE;
    private final BlockPos swiftStartPos = new BlockPos(-207, 0, -598);
    private BlockPos currentSwiftBlockPos = swiftStartPos;
    private BlockPos currentTargetSwiftBlockPos = swiftStartPos;
    private BlockPos lastSwiftBlockPos = swiftStartPos;

    private final AimHelper aimHelper = new AimHelper(1.5);

    private int leftClickCD = 0;

    private BlockPos startControlPos = null;
    private WitherSkeleton currentControlTargetEntity = null;
    private double controlLastX = 0;
    private double controlLastY = 0;
    private double controlLastZ = 0;
    private double controlCurrentDeltaX = 0;
    private double controlCurrentDeltaY = 0;
    private double controlCurrentDeltaZ = 0;
    private int controlAverageTick = 0;

    @Override
    public String getListenerName() {
        return "AutoDojo";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::onWorldUnload);
        CustomFabricEvents.MOUSE_BUTTON_EVENT.register(this::onMouseEvent);
    }

    private boolean onMouseEvent(long windowsHandle, MouseButtonInfo mouseButtonInfo, int pressState) {
        int button = mouseButtonInfo.button();
        switch(currentDojo) {
            case DISCIPLINE -> {
                int targetItemIndex = getDisciplineItemSlotIndex();
                if (targetItemIndex == -1) return true;
                if (button == 0 && pressState == 1 && InputSimulator.getCurrentItemSlotIndex() != targetItemIndex) {
                    InputSimulator.switchItem(targetItemIndex);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private int getDisciplineItemSlotIndex() {
        if(!(mc.hitResult instanceof EntityHitResult entityResult)) return -1;
        E2AMappingListener.MobInfo mobInfo = switch(entityResult.getEntity()) {
            case ArmorStand armorStand -> e2AMappingListener.getMobInfo(e2AMappingListener.getLivingEntity(armorStand));
            default -> e2AMappingListener.getMobInfo(entityResult.getEntity().asLivingEntity());
        };

        if(mobInfo == null) return -1;
        Map<String, Item> map = Map.of(
                "Wood", Items.WOODEN_SWORD,
                "Iron", Items.IRON_SWORD,
                "Diamond", Items.DIAMOND_SWORD,
                "Gold", Items.GOLDEN_SWORD
        );
        for (Map.Entry<String, Item> entry : map.entrySet()) {
            if (mobInfo.armorStand.getName().getString().contains(entry.getKey())) {
                for (int i = 0; i < 9; i++) {
                    if(mc.player.getInventory().getItem(i).getItem() == entry.getValue()) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private void onWorldUnload(Minecraft mc, ClientLevel level) {
        currentDojo = DojoType.NONE;
    }

    private void onStartTick(Minecraft mc) {
        if(mc.player == null || mc.level == null) return;

        if (mc.player.getY() > 107 && currentDojo != DojoType.NONE) {
            currentDojo = DojoType.NONE;
            currentSwiftBlockPos = swiftStartPos;
            lastSwiftBlockPos = swiftStartPos;
            currentTargetSwiftBlockPos = swiftStartPos;

            startControlPos = null;
            currentControlTargetEntity = null;
            InputSimulator.releaseAllKey();
        }

        switch (currentDojo) {
            case SWIFT -> {
                if(mc.player.onGround()) {
                    currentTargetSwiftBlockPos = currentSwiftBlockPos;
                }
                BlockPos playerPos = BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                Direction forward = mc.player.getDirection();
                Direction right = forward.getClockWise();
                Direction backward = right.getClockWise();
                Direction left = backward.getClockWise();

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

                List<Direction> activeDirections = new ArrayList<>();
                BlockPos target = currentTargetSwiftBlockPos;

                for (Map.Entry<Direction, Consumer<Boolean>> entry : directionInput.entrySet()) {
                    BlockPos original = playerPos;
                    BlockPos to = playerPos.relative(entry.getKey());

                    int distManhattanFrom = target.distManhattan(original);
                    int distManhattanTo = target.distManhattan(to);
                    boolean active = distManhattanTo < distManhattanFrom;
                    entry.getValue().accept(active);
                    if(active) activeDirections.add(entry.getKey());
                }
                int distManhattan = lastSwiftBlockPos.distManhattan(target);
                double distManhattanPlayer = Math.abs(mc.player.getX() - 0.5 - target.getX()) + Math.abs(mc.player.getZ() - 0.5 - target.getZ());
                if(distManhattan > 3 && !activeDirections.isEmpty()) {
                    InputSimulator.setPlayerYaw(activeDirections.getFirst().toYRot());
                    InputSimulator.setSprint(true);
                } else {
                    InputSimulator.setSprint(false);
                }
                InputSimulator.setJump(mc.level.getBlockState(playerPos.below()).getBlock() == Blocks.AIR && distManhattanPlayer <= distManhattan && distManhattanPlayer > 0.75);
            }
            case CONTROL -> {
                BlockPos playerPos = BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                if(startControlPos == null) {
                    startControlPos = playerPos;
                } else if(currentControlTargetEntity == null) {
                    for (Entity entity : mc.level.entitiesForRendering()) {
                        double deltaY = entity.getY() + 3 - mc.player.getY();
                        if (deltaY > 0 && deltaY < 14) {
                            if(entity instanceof WitherSkeleton ws) {
                                if(ws.getItemBySlot(EquipmentSlot.HEAD).getItem() == Items.COAL_BLOCK) {
                                    currentControlTargetEntity = ws;
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    ItemStack itemStack = currentControlTargetEntity.getItemBySlot(EquipmentSlot.HEAD);
                    if(!ToolList.getInstance().isEntityOnWorld(currentControlTargetEntity) || itemStack.isEmpty() || itemStack.getItem() == Items.REDSTONE_BLOCK) {
                        currentControlTargetEntity = null;
                        return;
                    }
                    Direction forward = mc.player.getDirection();
                    Direction right = forward.getClockWise();
                    Direction backward = right.getClockWise();
                    Direction left = backward.getClockWise();

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

                    if(currentControlTargetEntity != null) {

                        double currentX = currentControlTargetEntity.getX();
                        double currentY = currentControlTargetEntity.getY();
                        double currentZ = currentControlTargetEntity.getZ();
                        if(controlAverageTick++ % 4 == 0) {
                            controlCurrentDeltaX = currentX - controlLastX;
                            controlCurrentDeltaY = currentY - controlLastY;
                            controlCurrentDeltaZ = currentZ - controlLastZ;

                            controlLastX = currentX;
                            controlLastY = currentY;
                            controlLastZ = currentZ;
                        }
                        double expandValue = ConfigManager.autoDojoControlPredictDist.getValue() / 4d;
                        double x = currentX + controlCurrentDeltaX * expandValue;
                        double y = currentY + currentControlTargetEntity.getEyeHeight() + controlCurrentDeltaY * expandValue / 1.8;
                        double z = currentZ + controlCurrentDeltaZ * expandValue;

                        AimHelper.getYawPitchByDoublePos(x, y, z).updateToAimHelper(aimHelper);
                    }

                    BlockPos target = startControlPos;
                    target.distManhattan(playerPos);
                    for (Map.Entry<Direction, Consumer<Boolean>> entry : directionInput.entrySet()) {
                        BlockPos original = playerPos;
                        BlockPos to = playerPos.relative(entry.getKey());

                        int distManhattanFrom = target.distManhattan(original);
                        int distManhattanTo = target.distManhattan(to);
                        boolean active = distManhattanTo < distManhattanFrom;
                        entry.getValue().accept(active);
                    }

                    if (mc.player.getMainHandItem().getItem() == Items.WOODEN_SWORD) {
                        tryLeftClick();
                    }
                }
            }
            case DISCIPLINE -> {
                int targetItemIndex = getDisciplineItemSlotIndex();
                if (targetItemIndex == -1) return;
                if (InputSimulator.getCurrentItemSlotIndex() != targetItemIndex) {
                    InputSimulator.switchItem(targetItemIndex);
                }
            }
        }
    }

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if(currentDojo == DojoType.SWIFT) {
            if(packet instanceof ClientboundBlockUpdatePacket blockPacket) {
                BlockPos blockPos = blockPacket.getPos();
                BlockState blockState = blockPacket.getBlockState();
                if(blockState.getBlock().equals(Blocks.LIME_WOOL)) System.out.println(blockPos);
                Block lastBlock = mc.level.getBlockState(blockPos).getBlock();
                if((lastBlock == Blocks.AIR || lastBlock == Blocks.BARRIER) && Math.abs(blockPos.getY() - mc.player.getY()) <= 5 && blockState.getBlock().equals(Blocks.LIME_WOOL)) {
                    lastSwiftBlockPos = currentSwiftBlockPos;
                    currentSwiftBlockPos = blockPos.immutable();
                }
            }
            if(packet instanceof ClientboundSectionBlocksUpdatePacket blockPacket) {
                blockPacket.runUpdates((blockPos, blockState) -> {
                    if(blockState.getBlock().equals(Blocks.LIME_WOOL)) System.out.println(blockPos);
                    Block lastBlock = mc.level.getBlockState(blockPos).getBlock();
                    if((lastBlock == Blocks.AIR || lastBlock == Blocks.BARRIER) && Math.abs(blockPos.getY() - mc.player.getY()) <= 5 && blockState.getBlock().equals(Blocks.LIME_WOOL)) {
                        lastSwiftBlockPos = currentSwiftBlockPos;
                        currentSwiftBlockPos = blockPos.immutable(); // TRAP! 这玩意是mutable的
                    }
                });
            }
        }
        return false;
    }

    private void onChat(Component component, boolean b) {
        if(!ConfigManager.autoDojo.getValue()) return;

        String message = ToolList.getInstance().deleteColorCode(component.getString()).trim();
        Stream.of(DojoType.values()).filter(t -> t.chatMessage.equals(message)).findFirst().ifPresent(dojoType -> {

            ToolList.addThreadedTask(() -> {
                Thread.sleep(1000);
                ToolList.printChatMessage(Component.literal("§a[小沙雕] 小沙雕的Auto Dojo在该项目中可以" + (dojoType.fullAuto ? "§e全自动" : "§6半自动") + "§a工作"));
                if(dojoType == DojoType.SWIFT) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §e请注意, 请在本项目中关闭强制疾跑!"));
                }
                return null;
            });

            currentDojo = dojoType;
        });
    }

    private void tryLeftClick() {
        if(leftClickCD > 0) {
            leftClickCD--;
        } else {
            leftClickCD = ToolList.getInstance().random.nextInt(3) + 2;
            InputSimulator.singleLeftClick();
        }
    }

    @Override
    public boolean isMacroActive() {
        return currentDojo == DojoType.NONE || currentDojo == null;
    }

    @Override
    public boolean onMacroCheck(PositionInfo beforeTP, PositionInfo afterTP) {
        return true; // 马口检查了, 不会报警, 如果真发生了, 等亖吧 (bushi
    }

    @Override
    public String getMacroName() {
        return "Auto Dojo";
    }

    public enum DojoType {
        NONE("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", false),
        SWIFT("Test of Swiftness OBJECTIVES", true),
        DISCIPLINE("Test of Discipline OBJECTIVES", false),
        CONTROL("Test of Control OBJECTIVES", true)
        ;

        public final String chatMessage;
        public final boolean fullAuto;

        DojoType(String chatMessage, boolean fullAuto) {
            this.chatMessage = chatMessage;
            this.fullAuto = fullAuto;
        }
    }
}
