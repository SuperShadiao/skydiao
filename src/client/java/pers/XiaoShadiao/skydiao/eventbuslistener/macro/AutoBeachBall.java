package pers.XiaoShadiao.skydiao.eventbuslistener.macro;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class AutoBeachBall extends AbstractListener implements IMacro {

    private boolean hasRightClickedBeachBall;
    private int clickExpireTick;
    private ArmorStand currentTarget;

    @Override
    public String getListenerName() {
        return "AutoBeachBall";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onUnload);
        CustomFabricEvents.MOUSE_BUTTON_EVENT.register(this::onMouseClick);
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
    }

    private void onStartTick(Minecraft mc) {
        if(hasRightClickedBeachBall && clickExpireTick > 0) {
            clickExpireTick--;
            if (clickExpireTick == 0) {
                hasRightClickedBeachBall = false;
            }
        }

        if(mc.player == null || mc.level == null || currentTarget == null || !ConfigManager.autoPlayBeachBall.getValue()) return;

        Vec3 playerPos = mc.player.position();
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
        Vec3 target = currentTarget.position();

        boolean shouldJump = false;
        for (Map.Entry<Direction, Consumer<Boolean>> entry : directionInput.entrySet()) {
            Vec3 original = playerPos;

            Vec3 to = playerPos.relative(entry.getKey(), Mth.clampedLerp((mc.player.getSpeed() - 0.2) / 0.2, 0.2, 0.4));

            double distFrom = target.distanceTo(original);
            double distTo = target.distanceTo(to);
            boolean active = distTo < distFrom;
            entry.getValue().accept(active);
            if(active) {
                activeDirections.add(entry.getKey());
                if(ToolList.getInstance().isFullBlock(mc.level.getBlockState(BlockPos.containing(playerPos).relative(entry.getKey())))) {
                    shouldJump = true;
                }
            }
        }
        InputSimulator.setJump(shouldJump);
        InputSimulator.setShift(!(mc.level.getBlockState(BlockPos.containing(playerPos).below()).isAir() && mc.player.onGround()) && Math.abs(playerPos.horizontalDistance() - target.horizontalDistance()) < 1 || target.y() - playerPos.y() < 2.5);

        if(!ToolList.getInstance().isEntityOnWorld(currentTarget)) {
            stopScript();
        }
    }

    private boolean onMouseClick(long hwnd, MouseButtonInfo mouseButtonInfo, int state) {
        if(mc.player == null || mc.level == null || mc.screen != null || !ConfigManager.autoPlayBeachBall.getValue()) return false;
        if("BOUNCY_BEACH_BALL".equals(ToolList.getInstance().tryGetSkyblockItemId(mc.player.getMainHandItem()))) {
            hasRightClickedBeachBall = true;
            clickExpireTick = 100;
        }
        return false;
    }

    private void onUnload(Minecraft mc, ClientLevel clientLevel) {
        hasRightClickedBeachBall = false;
    }

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if(!ConfigManager.autoPlayBeachBall.getValue()) return false;
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if(level != null && player != null) {
            if(packet instanceof ClientboundAddEntityPacket addEntityPacket) {
                if (addEntityPacket.getType() == EntityType.ARMOR_STAND) {
                    delayTickExecutor.delayExec(() -> {
                        Entity entity = level.getEntity(addEntityPacket.getId());
                        if(entity instanceof ArmorStand armorStand && player.distanceTo(armorStand) < 6) {
                            if(hasRightClickedBeachBall && armorStand.getItemBySlot(EquipmentSlot.HEAD).getItem() == Items.PLAYER_HEAD) {
                                hasRightClickedBeachBall = false;
                                currentTarget = armorStand;
                                activeThisMacro();
                                ToolList.printChatMessage(Component.literal("§a[小沙雕] 记得抬头看球!"));
                            }
                        }
                    }, 2);
                }
            } else if(packet instanceof ClientboundSystemChatPacket(Component content, boolean overlay)) {
                if (overlay && currentTarget != null && ConfigManager.autoPlayBeachBallAutoStopAt40.getValue()) {
                    for (int i = 41; i < 45; i++) {
                        if (ToolList.getInstance().deleteColorCode(content.getString()).contains("Bounces: " + i)) {
                            stopScript();
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] 已到达40次数, 自动停止"));
                            break;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean isMacroActive() {
        return currentTarget != null;
    }

    @Override
    public boolean onMacroCheck(PositionInfo beforeTP, PositionInfo afterTP) {
        stopScript();
        return false;
    }

    @Override
    public boolean onMacroCheck(int beforeSlot, int afterSlot) {
        stopScript();
        return false;
    }

    @Override
    public String getMacroName() {
        return "Auto Beach Ball";
    }

    private void stopScript() {
        currentTarget = null;
        mc.schedule(InputSimulator::releaseAllKey);
    }

}
