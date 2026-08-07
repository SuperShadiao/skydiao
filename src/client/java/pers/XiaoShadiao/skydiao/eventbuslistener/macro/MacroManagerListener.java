package pers.XiaoShadiao.skydiao.eventbuslistener.macro;

import com.mojang.text2speech.Narrator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.customsounds.CustomSounds;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.EasyFarmingScriptListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.AutoBloodfiendListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.irc.ChatPacket;
import pers.XiaoShadiao.skydiao.utils.Register;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.WindowsUtils;

import java.util.*;

public class MacroManagerListener extends AbstractListener {

    private List<IMacro> macros = new ArrayList<>();
    private final Set<IMacro> activeMacros = new HashSet<>();
    private final List<Vec3> historyPoses = new ArrayList<>();

    public static final AutoFishListener autoFishListener = new AutoFishListener();
    public static final PathFinderExecutor pathFinderExecutor = new PathFinderExecutor();
    public static final AutoDojo autoDojo = new AutoDojo();
    public static final EasyFarmingScriptListener farmingScript = new EasyFarmingScriptListener();
    public static final AutoBloodfiendListener autoBloodfiendListener = new AutoBloodfiendListener();

    public long lastOpenChatTime = 0;
    public boolean isChatOpen = false;
    private int smallTickFlag;
    public boolean isScreenOpen = false;

    @Override
    public String getListenerName() {
        return "MacroManagerListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);

        Register.execRegister(MacroManagerListener.class, AbstractListener.class, listener -> {
            if(listener instanceof IMacro) {
                macros.add((IMacro) listener);
                listener.registerListeners();
            }
        });
        macros = Collections.unmodifiableList(macros);
    }

    private void onChat(Component component, boolean b) {
        String msg = ToolList.getInstance().deleteColorCode(component.getString());
        if(msg.startsWith("Evacuating")) {
            lastOpenChatTime = System.currentTimeMillis();
        }
    }

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if(mc.player != null) {
            if(packet instanceof ClientboundPlayerPositionPacket tpPacket) {
                if(System.currentTimeMillis() - lastOpenChatTime > 5000 && !activeMacros.isEmpty() && tpsListener.getCurrentTPS() > 11) {
                    ToolList.TPInfo tpInfo = ToolList.getInstance().parseTPPacket(tpPacket);
                    IMacro.PositionInfo before = new IMacro.PositionInfo(tpInfo.from().position(), tpInfo.from().yRot() % 360, tpInfo.from().xRot(), tpInfo.from().deltaMovement());
                    IMacro.PositionInfo after = new IMacro.PositionInfo(tpInfo.to().position(), tpInfo.to().yRot() % 360, tpInfo.to().xRot(), tpInfo.to().deltaMovement());

                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §c收到异常ClientboundPlayerPositionPacket数据包: " + after));

                    double distance = historyPoses.stream().mapToDouble(vec3 -> vec3.distanceTo(after.position())).min().orElse(0.0);
                    float deltaYaw = Math.abs(before.yaw() - after.yaw());
                    if (smallTickFlag > 200 || distance > 0.65 || (deltaYaw > 0.05 && deltaYaw < 360 - 0.05) || Math.abs(before.pitch() - after.pitch()) > 0.05) {
                        boolean flag = true;
                        for (IMacro macro : activeMacros) {
                            flag &= !macro.onMacroCheck(before, after);
                        }
                        if (flag) triggerAlert(before, after);
                    } else {
                        smallTickFlag += 100;
                    }
                }
            } else if(packet instanceof ClientboundSetHeldSlotPacket(int slot)) {
                if(System.currentTimeMillis() - lastOpenChatTime > 5000 && !activeMacros.isEmpty()) {
                    boolean flag = true;
                    int selectedSlot = mc.player.getInventory().getSelectedSlot();
                    if(selectedSlot != slot) {
                        for (IMacro macro : activeMacros) {
                            flag &= !macro.onMacroCheck(selectedSlot, slot);
                        }
                        if (flag) {
                            StringBuilder sb = new StringBuilder("\n物品栏被切换! Info:");
                            for (int i = 0; i < 9; i++) {
                                sb.append("\n").append(i == selectedSlot ? "➜" : i == slot ? "⚠" : "⚔").append(Optional.of(mc.player.getInventory().getItem(i)).filter(item -> !item.isEmpty()).map(ItemStack::getHoverName).map(Component::getString).map(s -> ToolList.getInstance().deleteColorCode(s)).orElse("** nothiing **"));
                            }
                            sb.append("\n");
                            triggerAlert(sb.toString());
                        }
                    }
                }
            }
        }
        return false;
    }

    public void triggerAlert(IMacro.PositionInfo from, IMacro.PositionInfo to) {
        triggerAlert("From: " + from + ", To: " + to);
    }

    public List<ToolList.ThreadedTask<Void>> alertTasks = new ArrayList<>();

    public void triggerAlert(String message) {
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §cAlert! Macro check!"));
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §c请不要慌张, 如果你在当前状态第一次被check, 立即切换为手动并返回继续当前操作 (比如继续钓鱼), 发生第二次check再进行响应!"));
        WindowsUtils.focusWindows();
        // mc.getNarrator().saySystemNow("Alert! Macro check! 警告! 马口检查!");
        Narrator.getNarrator().say("Alert! Macro check! 警告! 马口检查!", false, 1);
        alertTasks.removeIf(t -> t.future.isDone());
        if(alertTasks.size() < 3) {
            alertTasks.add(ToolList.addThreadedTask(() -> {
                for (int i = 0; basicListener.isAFK() || i < 3; i++) {
                    ToolList.getInstance().playSound(CustomSounds.ALERT_MACRO_CHECK);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                    }

                }
            }, null));
        }

        if(ChatClientManager.serverAvailable()) {
            ChatPacket p = new ChatPacket();
            p.initSender();
            p.packetType = "macro_check";
            p.message = "Alert! Macro check! (Info: ActiveMacros: " + activeMacros.stream().map(IMacro::getMacroName).toList() + " " + message + ")";
            ChatClientManager.getChatClient().sender.send(p);
        }
    }

    private void onStartClientTick(Minecraft mc) {
        if(mc.player != null) {
            historyPoses.add(mc.player.position());
            activeMacros.removeIf(m -> {
                boolean b = !m.isMacroActive();
                if(b) {
                    m.onMacroUnload();
                    logger.info("Unloaded macro: {}", m.getMacroName());
                }
                return b;
            });
        } else {
            activeMacros.clear();
        }
        while(historyPoses.size() > 30) historyPoses.removeFirst();

        boolean temp = mc.screen instanceof ChatScreen;
        if(isChatOpen && !temp) {
            lastOpenChatTime = System.currentTimeMillis();
        }
        isChatOpen = temp;

        if(smallTickFlag > 0) smallTickFlag--;

        if(!activeMacros.isEmpty()) {
            if(WindowsUtils.isWindowsIconfied() && !WindowsUtils.isWindowsFocused()) {
                WindowsUtils.focusWindows();
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §c⚠ 警告: 你不能在Macro工作的时候最小化窗口!"));
            }
        }
    }

    public void addActiveMacro(IMacro macro) {
        activeMacros.add(macro);
    }

    public boolean isMacroEnabled(IMacro macro) {
        return activeMacros.contains(macro);
    }

}
