package pers.XiaoShadiao.skydiao.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import pers.XiaoShadiao.skydiao.ircinner.api.v1.ChatPacket;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

public class OnlinePlayerList {

    public record OnlinePlayer(String name, String uuid, boolean isSkyDiaoUser) {}

    private static Set<OnlinePlayer> players = new CopyOnWriteArraySet<>();
    private static Set<String> playerNames = new CopyOnWriteArraySet<>();
    private static Set<String> playerUUIDs = new CopyOnWriteArraySet<>();

    public static void readFromPacket(ChatPacket packet) {
        JsonArray array = JsonParser.parseString(packet.message).getAsJsonObject().getAsJsonArray("players");
        players = new CopyOnWriteArraySet<>(array.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(jo -> !jo.get("offline").getAsBoolean())
                .filter(jo -> {
                    String version = jo.get("clientVersion").getAsString();
                    return version.startsWith("hh ") || version.startsWith("hhskb ");
                })
                .map(jo -> new OnlinePlayer(jo.get("name").getAsString(), jo.get("uuid").getAsString(), jo.get("clientVersion").getAsString().startsWith("hhskb "))).toList());
        playerNames = new CopyOnWriteArraySet<>(players.stream().map(OnlinePlayer::name).collect(Collectors.toUnmodifiableSet()));
        playerUUIDs = new CopyOnWriteArraySet<>(players.stream().map(OnlinePlayer::uuid).collect(Collectors.toUnmodifiableSet()));
    }

    public static Set<OnlinePlayer> getPlayers() {
        return Collections.unmodifiableSet(players);
    }

    public static Set<String> getPlayerNames() {
        return Collections.unmodifiableSet(playerNames);
    }

    public static Set<String> getPlayerUUIDs() {
        return Collections.unmodifiableSet(playerUUIDs);
    }

    public static boolean isPlayerNameOnline(String name) {
        return playerNames.contains(name);
    }

    public static boolean isPlayerUUIDOnline(String uuid) {
        return playerUUIDs.contains(uuid);
    }

    public static void removePlayerByUUID(String uuid) {
        players.removeIf(player -> {
            if(player.uuid.equals(uuid)) {
                playerNames.remove(player.name);
                playerUUIDs.remove(player.uuid);
                return true;
            }
            return false;
        });
    }

    public static void addPlayer(String name, String uuid, String clientVersion) {
        players.add(new OnlinePlayer(name, uuid, clientVersion.startsWith("hhskb ")));
        playerNames.add(name);
        playerUUIDs.add(uuid);
    }

    public static void clearPlayers() {
        players.clear();
    }

}
