package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.irc.ChatPacket;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public class MineshaftShareListener extends AbstractListener {

    private static final Object inviteLock = new Object();

    private final Map<Long, String> availableShaftPlayerNames = new HashMap<>();
    public Queue<String> playerList = new ConcurrentLinkedDeque<>();

    public Collection<String> getAvailableShaftPlayerNames() {
        return availableShaftPlayerNames.values();
    }

    public boolean inMineshaftDebug;

    private boolean allowShare;
    private boolean mineshaftClosed;

    private boolean inviteThreadRunning;

    @Override
    public String getListenerName() {
        return "MineshaftShareListener";
    }

    @Override
    public void registerListeners() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::worldUnload);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
    }

    public void onIRCMineshaftSharePacket(ChatPacket p) {

        if("0".equals(p.message) && ConfigManager.mineshaftSharing.getValue()) {

            // if(mc.getSession().getUsername().equals(p.sender)) return;

            if(shouldPopMessage()) {
                Style cs = Style.EMPTY
                        .withClickEvent(new ClickEvent.RunCommand("/hhjoinmineshaft " + p.sender))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§a点击后会立即加入§bGlacite Mineshaft§a, 请确保你手里的工作都完成了哦!\n§6注意! 点击后你当前的组队队伍会自动退出!\n§6如果响应后你没能成功进入Mineshaft, 请再点击试一次!\n§c若你没收到组队邀请, 使用/settings检查组队权限设置 (例如可能是禁止非好友和你组队)")));

                ToolList.printChatMessage(Component.literal("").withStyle(cs));
                ToolList.printChatMessage(Component.literal("§b===============[§aXSD§bMS]===============").withStyle(cs));
                ToolList.printChatMessage(Component.literal("§e" + p.sender + "§a的§bGlacite Mineshaft§a可以加入! §e[点击这里]").withStyle(cs));
                ToolList.printChatMessage(Component.literal("§b=====================================").withStyle(cs));
                ToolList.printChatMessage(Component.literal("§b").withStyle(cs));
            } else {
                Style cs = Style.EMPTY
                        .withClickEvent(new ClickEvent.SuggestCommand("/hhjoinmineshaft " + p.sender))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§a点击这条消息快速填充指令")));

                ToolList.printChatMessage(Component.literal("§a[XSD§bMS§a] §e" + p.sender + "§a的§bGlacite Mineshaft§a可以加入, 但你可能不在对应的环境, 为了防止误触, 你可以输入§e/skydiaojoinmineshaft " + p.sender + "§a加入.").withStyle(cs));
            }

            availableShaftPlayerNames.put(System.currentTimeMillis(), p.sender);
        } else {
            String selfName = mc.getUser().getName();
            if(("1_" + selfName).equals(p.message)) {

                if(mineshaftClosed) {
                    sendMineshaftSharePacket("REJECT_" + p.sender + "_" + selfName + ": Mineshaft入口已关闭! 无法加入!");
                } else if(!allowShare) {
                    sendMineshaftSharePacket("REJECT_" + p.sender + "_" + selfName + ": 人家没有发现Mineshaft, 或者不愿意分享给你!");
                } else {
                    ToolList.printChatMessage(Component.literal("§a[XSD§bMS§a] §a玩家§e" + p.sender + "§a请求加入§bMineshaft§a!"));
                    playerList.add(p.sender);
                }

            } else if(p.message.startsWith("REJECT_" + selfName + "_")) {
                String message = p.message.replace("REJECT_" + selfName + "_", "");
                ToolList.printChatMessage(Component.literal("§a[XSD§bMS§a] §c" + message));
            } else if(p.message.equals("100_" + selfName)) {
                if(!inviteThreadRunning) runCommand("/p leave");
                new Thread(() -> {
                    synchronized (inviteLock) {
                        try { Thread.sleep(1000 + ToolList.getInstance().random.nextInt(500)); } catch (InterruptedException e) {}
                        runCommand("/p join " + p.sender);
                        try { Thread.sleep(1500); } catch (InterruptedException e) {}
                    }
                }).start();
            }
        }

    }

    private boolean shouldPopMessage() {
        StatusManager status = StatusManager.get();
        return ("mining_3".equals(status.getMode()) || "mineshaft".equals(status.getMode())) && status.isInSkyblock();
    }

    public void worldUnload(Minecraft mc, ClientLevel clientLevel) {
        mineshaftClosed = false;
        if(isInMineshaft()) allowShare = false;
        inMineshaftDebug = false;
    }

    public void onChat(Component component, boolean b) {
        String message = ToolList.getInstance().deleteColorCode(component.getString());

        if(message.matches("MINESHAFT! A Mineshaft portal spawned nearby!(.*)")) {
            flagFoundShaft();
        } else if(message.equals("The mineshaft entrance has caved in... it doesn't look like anyone else will be able to get in here.")) {
            mineshaftClosed = true;
        } else if(message.contains("[SkyDiao] [ANNOUNCE] ")) {
            ToolList.addThreadedTask(() -> {
                Thread.sleep(4500);
                String announce = message.split("\\[SkyDiao] \\[ANNOUNCE] ")[1];
                if(!announce.isBlank() && isInMineshaft()) {
                    ToolList.printChatMessage(Component.literal("§b"));
                    ToolList.printChatMessage(Component.literal("§b========[§aXSD§bMS §eAnnounce§b]========="));
                    ToolList.printChatMessage(Component.literal("§7此§bShaft§7包含下面的公告信息:"));
                    ToolList.printChatMessage(Component.literal("§e"));
                    ToolList.printChatMessage(Component.literal("§e* " + announce));
                    ToolList.printChatMessage(Component.literal("§b====================================="));
                    ToolList.printChatMessage(Component.literal("§b"));
                    XSDHUD.bigTitle.updateTitleMsg("§c留意§bShaft§c公告!", 2000);
                }
                return null;
            });
        }
    }

    public void flagFoundShaft() {
        if(ConfigManager.mineshaftSharing.getValue()) {
            allowShare = true;
            playerList.clear();
            sendMineshaftSharePacket("0");
            ToolList.printChatMessage(Component.literal("§a[XSD§bMS§a] §a你的§bGlacite Mineshaft§a已被广播!"));
        } else {
            allowShare = false;
            ToolList.printChatMessage(Component.literal("§a[XSD§bMS§a] §c因为你的设置, 你的§bGlacite Mineshaft§c未被广播. 但同时你也看不到其他人的Shaft广播."));
        }
    }

    public void debugFoundShaft() {
        inMineshaftDebug = true;
        flagFoundShaft();
    }

    public void onTick(Minecraft mc) {
        if(!inviteThreadRunning && !mineshaftClosed && isInMineshaft()) {
            interrupt();
        }

        availableShaftPlayerNames.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getKey() > 60000);
    }

    public void run() {
        while (true) {
            try {
                inviteThreadRunning = false;
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignored) {

            }
            try {
                executeThread();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void executeThread() {
        inviteThreadRunning = true;

        while(!mineshaftClosed && isInMineshaft()) {
            if(!playerList.isEmpty()) {

                runCommand("/p leave");
                try { Thread.sleep(1000); } catch (InterruptedException e) {}

                while(!mineshaftClosed && isInMineshaft()) {
                    if(!playerList.isEmpty()) {
                        String player;
                        List<String> inviteList = new ArrayList<>();
                        while(true) {
                            player = playerList.poll();
                            if(player != null) {
                                inviteList.add(player);
                            }
                            if(player == null || inviteList.size() >= 5) {

                                synchronized (inviteLock) {
                                    runCommand("/p invite " + String.join(" ", inviteList));
                                    for(String p : inviteList) {
                                        sendMineshaftSharePacket("100_" + p);
                                    }
                                    inviteList.clear();

                                    if(player != null) {
                                        try { Thread.sleep(1600); } catch (InterruptedException e) {}
                                    } else {
                                        break;
                                    }
                                }

                            }
                        }
                        try { Thread.sleep(3300); } catch (InterruptedException e) {}

                        boolean isLeaveEarly = false;
                        if (isInMineshaft()) {
                            runCommand("/p warp");
                            String announceMsg = ConfigManager.mineshaftShareAnnounce.getValue().trim();
                            if (announceMsg.isEmpty()) {
                                try {
                                    Thread.sleep(5500);
                                } catch (InterruptedException e) {
                                }
                            } else {
                                try {
                                    Thread.sleep(4500);
                                } catch (InterruptedException e) {
                                }
                                runCommand("/pc [SkyDiao] [ANNOUNCE] " + announceMsg);
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                }
                            }
                            if(isInMineshaft()) {
                                runCommand("/p warp");
                                try {
                                    Thread.sleep(1500);
                                } catch (InterruptedException e) {
                                }
                            } else isLeaveEarly = true;
                        } else isLeaveEarly = true;

                        if(isLeaveEarly) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                            }
                            runCommand("/pc [SkyDiao] 由于我不小心提前离开了Mineshaft, 后续的warp无法进行");
                        }
                        runCommand("/p disband");
                    }
                    try { Thread.sleep(500); } catch (InterruptedException e) {}
                }
            }
            try { Thread.sleep(500); } catch (InterruptedException e) {}
        }
        inviteThreadRunning = false;
    }

    private boolean isInMineshaft() {
        return inMineshaftDebug || "mineshaft".equals(StatusManager.get().getMode());
    }

    private void sendMineshaftSharePacket(String customMessage) {
        ChatPacket p = new ChatPacket();
        p.packetType = "glacite_mineshaft_share";
        p.message = customMessage;
        p.initSender();

        ChatClientManager.trySendOrWarning(p);
    }

    public void tryToJoinPlayersMineshaft(String playerName) {
        ChatPacket p = new ChatPacket();

        p.packetType = "glacite_mineshaft_share";
        p.message = "1_" + playerName;
        p.initSender();

        ChatClientManager.trySendOrWarning(p);
    }

    private void runCommand(String s) {
        ToolList.printChatMessage(Component.literal("§a[XSD§bMS§a] 执行命令: §e" + s));
        ToolList.sendChatMessage(s);
    }

}
