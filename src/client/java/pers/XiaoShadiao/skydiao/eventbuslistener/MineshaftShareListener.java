package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
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
import pers.XiaoShadiao.skydiao.utils.tab.TabReader;

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

    private final List<CorpseType> corpses = new ArrayList<>();
    private MineshaftType currentShaftType;
    private boolean isShaftTypeCorpseInited;

    private boolean allowShare;
    private boolean mineshaftClosed;

    private boolean inviteThreadRunning;

    enum CorpseType {
        UMBER("§6"),
        TUNGSTEN("§7"),
        LAPIS("§9"),
        ;
        public final String color;

        CorpseType(String color) {
            this.color = color;
        }
    }

    enum MineshaftType {
        TOPA_1("§e", "Topaz 1"),
        TOPA_2("§e", "Topaz 2"),
        SAPP_1("§b", "Sapphire 1"),
        SAPP_2("§b", "Sapphire 2"),
        AMET_1("§5", "Amethyst 1"),
        AMET_2("§5", "Amethyst 2"),
        AMBE_1("§6", "Amber 1"),
        AMBE_2("§6", "Amber 2"),
        JADE_1("§a", "Jade 1"),
        JADE_2("§a", "Jade 2"),
        TITA_1("§7", "Titanium 1"),
        UMBE_1("§6", "Umber 1"),
        TUNG_1("§7", "Tungsten 1"),
        FAIR_1("§f", "Vanguard"),
        RUBY_1("§c", "Ruby 1"),
        RUBY_2("§c", "Ruby 2"),
        RUBY_C("§c", "Ruby Crystal"),
        ONYX_1("§0", "Onyx 1"),
        ONYX_2("§0", "Onyx 2"),
        ONYX_C("§0", "Onyx Crystal"),
        AQUA_1("§9", "Aquamarine 1"),
        AQUA_2("§9", "Aquamarine 2"),
        AQUA_C("§9", "Aquamarine Crystal"),
        CITR_1("§4", "Citrine 1"),
        CITR_2("§4", "Citrine 2"),
        CITR_C("§4", "Citrine Crystal"),
        PERI_1("§2", "Peridot 1"),
        PERI_2("§2", "Peridot 2"),
        PERI_C("§2", "Peridot Crystal"),
        JASP_1("§d", "Jasper 1"),
        JASP_C("§d", "Jasper Crystal"),
        OPAL_1("§f", "Opal 1"),
        OPAL_C("§f", "Opal Crystal"),
        LITT_L("§d", "Littlefoot's Den"),
                ;

        public final String color;
        public final String rawName;
        MineshaftType(String s, String s1) {
            color = s;
            rawName = s1;
        }
        public String displayName() {
            return color + rawName;
        }
        @Override
        public String toString() {
            return displayName();
        }
    }

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
        } else if(p.message.startsWith("2_") && ConfigManager.mineshaftSharing.getValue()) {

            StringBuilder extraShaftInfo = new StringBuilder();
            MineshaftType type = null;
            try {
                JsonObject jo = JsonParser.parseString(p.message.substring(2)).getAsJsonObject();
                type = MineshaftType.valueOf(jo.get("shaft").getAsString());

                JsonArray ja = jo.get("corpses").getAsJsonArray();
                Object2IntMap<CorpseType> count = new Object2IntLinkedOpenHashMap<>();
                for (JsonElement jsonElement : ja) {
                    String string = jsonElement.getAsString();
                    CorpseType corpseType = CorpseType.valueOf(string);
                    count.put(corpseType, count.getOrDefault(corpseType, 0) + 1);
                }
                if(!count.isEmpty()) {
                    extraShaftInfo.append("§a尸体: ");
                    for (Object2IntMap.Entry<CorpseType> entry : count.object2IntEntrySet()) {
                        extraShaftInfo.append(entry.getKey().color).append(entry.getIntValue()).append(" ").append(entry.getKey().name()).append(" ");
                    }
                    extraShaftInfo.append("\n\n");
                }
            } catch (Exception e) {
                logger.error("Fail to parse shaft extra info");
                logger.catching(e);
                type = null;
                extraShaftInfo = new StringBuilder();
            }

            if(shouldPopMessage()) {
                Style cs = Style.EMPTY
                        .withClickEvent(new ClickEvent.RunCommand("/hhjoinmineshaft " + p.sender))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(extraShaftInfo + "§a点击后会立即加入§bGlacite Mineshaft§a, 请确保你手里的工作都完成了哦!\n§6注意! 点击后你当前的组队队伍会自动退出!\n§6如果响应后你没能成功进入Mineshaft, 请再点击试一次!\n§c若你没收到组队邀请, 使用/settings检查组队权限设置 (例如可能是禁止非好友和你组队)")));

                ToolList.printChatMessage(Component.literal("").withStyle(cs));
                ToolList.printChatMessage(Component.literal("§b===============[§aXSD§bMS]===============").withStyle(cs));
                ToolList.printChatMessage(Component.literal("§e" + p.sender + "§a的" + (type == null ? "§bGlacite" : type.displayName()) + " §bMineshaft§a可以加入! §e[点击这里]").withStyle(cs));
                ToolList.printChatMessage(Component.literal("§b=====================================").withStyle(cs));
                ToolList.printChatMessage(Component.literal("§b").withStyle(cs));
            } else {
                Style cs = Style.EMPTY
                        .withClickEvent(new ClickEvent.SuggestCommand("/hhjoinmineshaft " + p.sender))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(extraShaftInfo + "§a点击这条消息快速填充指令")));

                ToolList.printChatMessage(Component.literal("§a[XSD§bMS§a] §e" + p.sender + "§a的" + (type == null ? "§bGlacite" : type.displayName()) + " §bMineshaft§a可以加入, 但你可能不在对应的环境, 为了防止误触, 你可以输入§e/skydiaojoinmineshaft " + p.sender + "§a加入.").withStyle(cs));
            }
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
        isShaftTypeCorpseInited = false;
        currentShaftType = null;
        corpses.clear();
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
            // sendMineshaftSharePacket("0");
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

        if(isInMineshaft() && !isShaftTypeCorpseInited) {
            if(currentShaftType == null) {
                for (MineshaftType value : MineshaftType.values()) {
                    if (ToolList.getInstance().fetchScoreboardLinesNoColor().stream().anyMatch(line -> line.contains(value.name()))) {
                        currentShaftType = value;
                        break;
                    }
                }
            }
            List<MineshaftShareListener.CorpseType> tempCorpses = new ArrayList<>();

                for (String line : TabReader.getLines()) {
                    if(line.contains("Tungsten:")) {
                        tempCorpses.add(CorpseType.TUNGSTEN);
                    } else if(line.contains("Lapis:")) {
                        tempCorpses.add(CorpseType.LAPIS);
                    } else if(line.contains("Umber:")) {
                        tempCorpses.add(CorpseType.UMBER);
                    }
                }

            if(currentShaftType != null && ((!corpses.isEmpty() && tempCorpses.equals(corpses)) || currentShaftType == MineshaftType.FAIR_1)) {
                isShaftTypeCorpseInited = true;
                if(allowShare) {
                    JsonObject object = new JsonObject();
                    object.addProperty("shaft", currentShaftType.name());
                    JsonArray cs = new JsonArray();
                    for (CorpseType corps : corpses) {
                        cs.add(corps.name());
                    }
                    object.add("corpses", cs);
                    sendMineshaftSharePacket("2_" + object);
                }
            }
            corpses.clear();
            corpses.addAll(tempCorpses);
        }
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
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                            }
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
