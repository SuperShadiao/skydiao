package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

public class RiftAutoDanceRoomListener extends AbstractListener {

    private DanceCommand 操你妈的指令 = DanceCommand.BASE;
    public boolean isDancing;
    private boolean hasGlass;
    private int doDanceTick;
    private int actionDelay;

    private int tickCounter;

    public BlockPos[] poses = new BlockPos[] {
            new BlockPos(-265, 32, -108),
            new BlockPos(-265, 32, -106),
            new BlockPos(-263, 32, -106),
            new BlockPos(-263, 32, -108)
    };

    @Override
    public String getListenerName() {
        return "RiftAutoDanceRoomListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientStartTick);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
    }

    public void onClientStartTick(Minecraft mc) {
        if(mc.player == null || mc.level == null) return;

        tickCounter++;
        if(ConfigManager.skyblockriftautodanceroom.getValue()) {
            if (!"rift".equals(StatusManager.get().getMode()) || mc.player.getY() < 32 || mc.player.getY() > 35) {
                isDancing = false;
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §c哎呀, 你似乎不在跳舞房里! 先进去再开启吧!"));
                ConfigManager.skyblockriftautodanceroom.setValue(false);
            } else if (!isDancing) {
                if (tickCounter % 200 == 5) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] 站在玻璃上开始..."));
                }
            }
        }

        if(isDancing) {
            boolean b = mc.level.getBlockState(BlockPos.containing(mc.player.getX(), 32, mc.player.getZ())).getBlock() instanceof StainedGlassBlock;
            if(!hasGlass && b) {
                hasGlass = true;
                doDanceTick = 20;
            } else if(hasGlass && !b) {
                hasGlass = false;
            }
            if(!hasGlass) {
                if(!MacroManagerListener.pathFinderExecutor.isRunning()) {
                    for (BlockPos pose : poses) {
                        if(mc.level.getBlockState(pose).getBlock() instanceof StainedGlassBlock) {
                            MacroManagerListener.pathFinderExecutor.startExecution(new Vec3(pose.getX(), pose.getY() + 1, pose.getZ()), false);
                            break;
                        }
                    }
                }
            }

            if(!MacroManagerListener.pathFinderExecutor.isSleeping() || InputSimulator.forward) {
                actionDelay = 5;
            }

            if (actionDelay <= 0) {
                InputSimulator.setJump(doDanceTick > 0 && 操你妈的指令.jump && !InputSimulator.forward);
                InputSimulator.setShift(doDanceTick > 0 && 操你妈的指令.sneak);
                if(doDanceTick > 0) {
                    doDanceTick--;
                    if(doDanceTick == 16 && 操你妈的指令.punch) InputSimulator.singleLeftClick();
                }
            }

            if(actionDelay > 0) actionDelay--;
        }
    }

    private void onChat(Component component, boolean b) {
        if(ToolList.getInstance().deleteColorCode(component.getString()).toLowerCase().contains("full dance!")) {
            isDancing = false;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] 任务已完成, 功能已自动关闭"));
            ConfigManager.skyblockriftautodanceroom.setValue(false);
        }
    }

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if(!ConfigManager.skyblockriftautodanceroom.getValue()) return false;
        Component component = ToolList.getInstance().tryGetTitleFromPacket(packet);
        if (component != null) {
            String string = ToolList.getInstance().deleteColorCode(component.getString());

            if("Move!".equalsIgnoreCase(string)) {
                isDancing = true;
                操你妈的指令 = DanceCommand.BASE;
            }
            if(string != null) {
                String string1 = string.toLowerCase();
                boolean jump = string1.contains("jump!") && !string1.contains("don't");
                boolean sneak = string1.contains("sneak!") && !string1.contains("stand!");
                boolean doPunch = string1.contains("punch!");
//                if(string1.contains("punch!")) {
//                    if(isDancing) 工具列表.getInstance().模拟左键();
//                }
                if(doDanceTick < 10) {
                    操你妈的指令 = new DanceCommand(sneak, jump, doPunch);
                }
            }
        }
        return false;
    }

    public record DanceCommand(boolean sneak, boolean jump, boolean punch) {

        public static final DanceCommand BASE = new DanceCommand(false, false, false);

    }

}
