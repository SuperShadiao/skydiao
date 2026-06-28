package pers.XiaoShadiao.skydiao.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.hypixel.data.type.ServerType;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class StatusManager extends Thread {

    private static ClientboundLocationPacket hypLocation = null;
    private static StatusManager SM;
    private static StatusManager lastInstance;
    private String lobbyName = "";
    private boolean isInLobby;
    private String loadBy;
    private int errtime = 3;
    private String gameType,gameMode;
    private String serverID, smallServerID;

    private final int tokenInstance;
    private static int token;

    private static String lastGameType;
    private static String lastGameMode;
    private final long startTime = System.currentTimeMillis();

    private boolean hasStatus,online,statusIsNull;

    public static void updateStatus() {
        SM = new StatusManager();
    }

    public static StatusManager get() {
        if(SM == null) updateStatus();
        return SM;
    }

    private StatusManager() {
        hasStatus = false;
        this.setName("StatusManager");

        tokenInstance = token = ToolList.getInstance().random.nextInt();
        this.start();
    }

    public static void updateStatusByHypixelPacket(ClientboundLocationPacket hypPacket) {
        hypLocation = hypPacket;
        updateStatus();
    }
    
    public static void cleanHypixelPacket() {
        hypLocation = null;
        updateStatus();
        ToolList.getInstance().log.info("Cleaned Hypixel Packet Instance");
    }

    public void run() {

        if(tokenInstance != token) return;

        try {

            if(hypLocation != null) {

                loadBy = "Hyp Packet";
                gameMode = hypLocation.getMode().orElse("");
                gameType = hypLocation.getServerType().map(ServerType::getName).orElse("");
                serverID = hypLocation.getServerName();
                smallServerID = serverID.replace("mega", "M").replace("mini", "m");

                online = true;
                hasStatus = true;

                Optional<String> lobbyName1 = hypLocation.getLobbyName();
                lobbyName1.ifPresent(s -> lobbyName = s);
                isInLobby = lobbyName1.isPresent();
            }

        } finally {
            Map<String, String> map = new HashMap<>();
            map.put("loadBy", loadBy);
            map.put("serverID", serverID);
            map.put("hasStatus", String.valueOf(hasStatus));
            map.put("online", String.valueOf(online));
            map.put("gameType", gameType);
            map.put("gameMode", gameMode);
            map.put("isInLobby", String.valueOf(isInLobby));
            map.put("lobbyName", lobbyName);
            ToolList.getInstance().log.info("已加载玩家在线信息: " + map);

            if(hasStatus && online) if(ChatClientManager.serverAvailable()) ChatClientManager.getChatClient().sender.sendOnlineInfo();
        }

    }

    public boolean hasStatus() {
        return hasStatus;
    }

    public boolean isStatusNull() {
        return statusIsNull;
    }

    public boolean isOnline() {
        return online;
    }

    public static void destory() {
        SM = null;
    }

    public String getType() {
        return gameType;
    }

    public String getMode() {
        return gameMode;
    }
    public boolean isInLobby() {
        return isInLobby;
    }
    public String getLobbyName() {
        return lobbyName;
    }
    public String getServerID() {
        return serverID;
    }
    public String getSmallServerID() {
        return smallServerID;
    }
    public boolean isMiniServer() {
        return serverID != null && serverID.startsWith("mini");
    }
    public boolean isMegaServer() {
        return serverID != null && serverID.startsWith("mega");
    }
    public boolean isInDungeon() {
        return "DUNGEON".equalsIgnoreCase(gameMode);
    }
    public boolean isInSkyblock() {
        return "SkyBlock".equals(gameType);
    }

}
