package pers.XiaoShadiao.skydiao.eventbuslistener.macro;

import com.madgag.gif.fmsware.AnimatedGifEncoder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.text2speech.Narrator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.io.FileUtils;
import pers.XiaoShadiao.skydiao.appendage.eventbuslistener.macro.mining.ObsidianListener;
import pers.XiaoShadiao.skydiao.appendage.eventbuslistener.macro.mining.ObsidianWRListener;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.customsounds.CustomSounds;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming.EasyFarmingScriptListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.slayer.vs.AutoBloodfiendListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.irc.ChatPacket;
import pers.XiaoShadiao.skydiao.utils.Register;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.WindowsUtils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public class MacroManagerListener extends AbstractListener {

    private List<IMacro> macros = new ArrayList<>();
    private final Set<IMacro> activeMacros = new HashSet<>();
    private final List<Vec3> historyPoses = new ArrayList<>();

    public static final AutoFishListener autoFishListener = new AutoFishListener();
    public static final PathFinderExecutor pathFinderExecutor = new PathFinderExecutor();
    public static final AutoDojo autoDojo = new AutoDojo();
    public static final EasyFarmingScriptListener farmingScript = new EasyFarmingScriptListener();
    public static final AutoBloodfiendListener autoBloodfiendListener = new AutoBloodfiendListener();
    public static final AutoBeachBall autoBeachBall = new AutoBeachBall();
    public static final CarnivalFruitDigger carnvialFruitDigger = new CarnivalFruitDigger();
    public static final CarnivalZombieShoot carnivalZombieShoot = new CarnivalZombieShoot();
    public static final AutoFillBottleOfWater autoFillBottleOfWater = new AutoFillBottleOfWater();

    public static final ObsidianListener obsidianListener = new ObsidianListener();
    public static final ObsidianWRListener obsidianWRListener = new ObsidianWRListener();

    public long lastOpenChatTime = 0;
    public boolean isChatOpen = false;
    public boolean isScreenOpen = false;

    public static final File recordDir = new File(new File(mc.gameDirectory, "screenshots"), "xsd_alert_macro_check");

    private boolean isRecording = false;
    private final AnimatedGifEncoder gifEncoder = new AnimatedGifEncoder();
    private File currentRecordInstance = new File(recordDir, String.valueOf(System.currentTimeMillis()));
    private int recordCycleDelay = 0;
    private int recordInstanceCycle = 0;
    private int recordIndex = 0;
    private int recordMoreFrame = 0;
    private static final BigInteger MAX_RECORD_SIZE = new BigInteger("10737418240");
    private long lastFrameTime = System.currentTimeMillis();

    private int alertSpamTicker = 0;

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

        recordDir.mkdirs();
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
                    if (distance > 0.65 || (deltaYaw > 0.05 && deltaYaw < 360 - 0.05) || Math.abs(before.pitch() - after.pitch()) > 0.05) {
                        boolean flag = true;
                        for (IMacro macro : activeMacros) {
                            flag &= !macro.onMacroCheck(before, after);
                        }
                        if (flag) triggerAlert(before, after);
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
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §c§l请不要离开当前服务器, 否则你会被直接封禁!"));
        WindowsUtils.focusWindows();
        // mc.getNarrator().saySystemNow("Alert! Macro check! 警告! 马口检查!");
        Narrator.getNarrator().say("Alert! Macro check! 警告! 马口检查!", false, 1);
        alertTasks.removeIf(t -> t.future.isDone());
        if(alertTasks.size() < 3) {
            alertTasks.add(ToolList.addThreadedTask(() -> {
                for (int i = 0; basicListener.isAFK() || i < 3; i++) {
                    ToolList.getInstance().playSound(CustomSounds.ALERT_MACRO_CHECK);
                    XSDHUD.bigTitle.updateTitleMsg("§c脱离AFK以解除警报。", 1000, null);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                    }
                }
            }, null));
        }

        alertSpamTicker = Math.clamp(alertSpamTicker + 200, 0, 2000);
        if(ChatClientManager.serverAvailable() && alertSpamTicker < 1000) {
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
        if(alertSpamTicker > 0) alertSpamTicker--;

        boolean temp = mc.screen instanceof ChatScreen;
        if(isChatOpen && !temp) {
            lastOpenChatTime = System.currentTimeMillis();
        }
        isChatOpen = temp;

        if(!activeMacros.isEmpty()) {
            if(WindowsUtils.isWindowsIconfied() && !WindowsUtils.isWindowsFocused()) {
                WindowsUtils.focusWindows();
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §c⚠ 警告: 你不能在Macro工作的时候最小化窗口!"));
            }
        }
        if(!isRecording && enabledRecord()) {
            recordCycleDelay = 0;
            recordMoreFrame = 80;
            recordIndex = 0;
            isRecording = true;
            frameTasks.offer(() -> {
                if (ConfigManager.macroReplaySelfCleaning.getValue()) {
                    currentRecordInstance = new File(recordDir, String.valueOf(recordInstanceCycle));
                    recordInstanceCycle++;
                    if(recordInstanceCycle > 9) {
                        recordInstanceCycle = 0;
                    }
                    try {
                        FileUtils.deleteDirectory(currentRecordInstance);
                    } catch (Throwable e) {
                        logger.catching(e);
                    }
                } else {
                    currentRecordInstance = new File(recordDir, String.valueOf(System.currentTimeMillis()));
                }

                currentRecordInstance.mkdirs();
                try {
                    gifEncoder.start(new FileOutputStream(new File(currentRecordInstance, recordIndex + ".gif")));
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
                gifEncoder.setDelay(500);
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §a任意脚本已启动, 实时回放已开始录制"));
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §e警告: 若客户端发生严重卡顿, 可能你的设备不是很好, 请前往设置的自动类关闭即时回放!"));
            });
        } else if(isRecording && !enabledRecord()) {
            if(recordMoreFrame > 0) {
                recordMoreFrame--;
            } else {
                frameTasks.offer(() -> {
                    gifEncoder.finish();

                    BigInteger size = FileUtils.sizeOfDirectoryAsBigInteger(recordDir);

                    Style style = Style.EMPTY
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("点击这里打开文件夹")))
                            .withClickEvent(new ClickEvent.OpenFile(recordDir));
                    if(isRecording) {
                        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e当前没有脚本在工作, 实时回放已停止录制. 点击这里可打开文件夹").withStyle(style));

                        if (size.compareTo(MAX_RECORD_SIZE) > 0) {
                            String byteString = ToolList.getInstance().numberToByteString(size);
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c当前Macro回放文件夹已超过10GB (当前" + byteString + "), 你可以§e点击这里§c来按照时间顺序清理").withStyle(style));
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c提示: 现在可以在设置打开自动清理, 会按照顺序自动删除回放 (如果回放因为下次脚本启动被误删除, 后果自己承担)").withStyle(style));
                        }
                    }

                    isRecording = false;
                });
            }
        }

        if(isRecording || recordMoreFrame > 0) {
            if(frameTasks.remainingCapacity() > 1) {
                Screenshot.takeScreenshot(mc.getMainRenderTarget(), image0 -> {
                    int delay = Math.clamp(System.currentTimeMillis() - lastFrameTime, 1, 750);
                    if (frameTasks.offer(() -> {
                        try(NativeImage image = image0) {
                            int w = image.getWidth();
                            int h = image.getHeight();
                            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                            for (int y = 0; y < h; y++) {
                                for (int x = 0; x < w; x++) {
                                    bi.setRGB(x, y, image.getPixel(x, y));
                                }
                            }

                            gifEncoder.setDelay(delay);
                            gifEncoder.addFrame(bi);

                            recordCycleDelay += delay;
                            if (recordCycleDelay >= 10000) {
                                recordCycleDelay = 0;
                                recordIndex++;
                                gifEncoder.finish();
                                try {
                                    gifEncoder.start(new FileOutputStream(new File(currentRecordInstance, recordIndex + ".gif")));
                                } catch (FileNotFoundException e) {
                                }
                                gifEncoder.setDelay(100);

                                new File(currentRecordInstance, (recordIndex - 5) + ".gif").delete();
                            }
                        }
                    })) {
                        lastFrameTime = System.currentTimeMillis();
                    } else {
                        image0.close();
                    }
                });
            }
        }
    }

    private boolean enabledRecord() {
        return ConfigManager.macroReplay.getValue() && !activeMacros.isEmpty();
    }

    private final ArrayBlockingQueue<Runnable> frameTasks = new ArrayBlockingQueue<>(3);

    @Override
    public void run() {
        while(true) {
            try {
                if(frameTasks != null) handleFrame(frameTasks.take());
            } catch (Throwable e) {
                logger.catching(e);
            }
        }
    }

    private void handleFrame(Runnable frame) {
        frame.run();
    }

    public void addFrame(Runnable frame) {
        frameTasks.offer(frame);
    }

    public void addActiveMacro(IMacro macro) {
        activeMacros.add(macro);
    }

    public boolean isMacroEnabled(IMacro macro) {
        return activeMacros.contains(macro);
    }

}
