package pers.XiaoShadiao.skydiao.eventbuslistener.macro;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.customsounds.CustomSounds;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.utils.Register;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.*;

public class MacroManagerListener extends AbstractListener {

    private List<IMacro> macros = new ArrayList<>();
    private final Set<IMacro> activeMacros = new HashSet<>();
    private final List<Vec3> historyPoses = new ArrayList<>();

    public static final AutoFishListener autoFishListener = new AutoFishListener();
    public static final PathFinderExecutor pathFinderExecutor = new PathFinderExecutor();
    public static final AutoDojo autoDojo = new AutoDojo();

    @Override
    public String getListenerName() {
        return "MacroManagerListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);

        Register.execRegister(MacroManagerListener.class, AbstractListener.class, listener -> {
            if(listener instanceof IMacro) {
                macros.add((IMacro) listener);
                listener.registerListeners();
            }
        });
        macros = Collections.unmodifiableList(macros);
    }

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {

        if(packet instanceof ClientboundPlayerPositionPacket tpPacket) {
            if(!activeMacros.isEmpty()) {
                ToolList.TPInfo tpInfo = ToolList.getInstance().parseTPPacket(tpPacket);
                IMacro.PositionInfo before = new IMacro.PositionInfo(tpInfo.from().position(), tpInfo.from().yRot() % 360, tpInfo.from().xRot(), tpInfo.from().deltaMovement());
                IMacro.PositionInfo after = new IMacro.PositionInfo(tpInfo.to().position(), tpInfo.to().yRot() % 360, tpInfo.to().xRot(), tpInfo.to().deltaMovement());

                ToolList.printChatMessage(Component.literal("§a[小沙雕] §c收到异常ClientboundPlayerPositionPacket数据包: " + after));

                double distance = historyPoses.stream().mapToDouble(vec3 -> vec3.distanceTo(after.position())).min().orElse(0.0);
                float deltaYaw = Math.abs(before.yaw() - after.yaw());
                if (distance > 0.1 || deltaYaw > 0.05 && deltaYaw < 360 - 0.05 || Math.abs(before.pitch() - after.pitch()) > 0.05) {
                    boolean flag = true;
                    for (IMacro macro : activeMacros) {
                        flag &= !macro.onMacroCheck(before, after);
                    }
                    if (flag) triggerAlert(before, after);
                }
            }
        }
        return false;
    }

    private void triggerAlert(IMacro.PositionInfo from, IMacro.PositionInfo to) {
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §cAlert! Macro check!"));
        ToolList.addThreadedTask(() -> {
            for (int i = 0; i < 3; i++) {
                ToolList.getInstance().playSound(CustomSounds.ALERT_MACRO_CHECK);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                }
            }
        }, null);

        if(ChatClientManager.serverAvailable()) {
            ChatClientManager.getChatClient().sender.sendMessage("Alert! Macro check! (Info: ActiveMacros: " + activeMacros.stream().map(IMacro::getMacroName).toList() + " From: " + from + ", To: " + to + ")");
        }
    }

    private void onStartClientTick(Minecraft mc) {
        if(mc.player != null) {
            historyPoses.add(mc.player.position());
            activeMacros.removeIf(m -> !m.isMacroActive());
        } else {
            activeMacros.clear();
        }
        while(historyPoses.size() > 30) historyPoses.removeFirst();
    }

    public void addActiveMacro(IMacro macro) {
        activeMacros.add(macro);
    }

}
