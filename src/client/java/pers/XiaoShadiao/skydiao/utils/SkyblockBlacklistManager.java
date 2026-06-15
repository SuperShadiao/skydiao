package pers.XiaoShadiao.skydiao.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SkyblockBlacklistManager extends Thread {

    private static SkyblockBlacklistManager instance;

    public static SkyblockBlacklistManager getInstance() {
        if(instance == null) updateBlacklist();
        return instance;
    }

    public static void updateBlacklist() {
        instance = new SkyblockBlacklistManager();
    }

    private List<SkyblockBlacklistEntry> blacklist = new ArrayList<>();
    private Map<SkyblockBlacklistType, List<SkyblockBlacklistEntry>> groupedByType = new HashMap<>();

    private boolean isDone;
    private boolean isErrored;

    public SkyblockBlacklistManager() {
        setName("XSD Skyblock Blacklist Manager");
        start();
    }

    @Override
    public void run() {
        try(InputStream is = ToolList.getInstance().makeReqToURL("https://xiaoshadiao.club/skyblock_blp.json")) {
            JsonObject jo = JsonParser.parseReader(new JsonReader(new InputStreamReader(is))).getAsJsonObject();
            List<CompletableFuture<SkyblockBlacklistEntry>> tasks = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : jo.entrySet()) {
                String type = entry.getKey();
                SkyblockBlacklistType type1 = SkyblockBlacklistType.lookup(type);

                JsonArray jsonArray = entry.getValue().getAsJsonArray();
                for(JsonElement jsonElement : jsonArray) {
                    JsonObject jsonObj = jsonElement.getAsJsonObject();
                    String uuid = jsonObj.get("uuid").getAsString();
                    String reason = jsonObj.get("reason").getAsString();
                    tasks.add(UUIDLookup.getNameByUUID(uuid).thenCompose(name -> CompletableFuture.completedFuture(new SkyblockBlacklistEntry(type1, uuid, name, reason))));
                }
            }
            for (CompletableFuture<SkyblockBlacklistEntry> task : tasks) {
                blacklist.add(task.get());
            }
        } catch (Exception e) {
            e.printStackTrace();
            isErrored = true;
        }
        blacklist = Collections.unmodifiableList(blacklist);
        isDone = true;
    }

    public List<SkyblockBlacklistEntry> getBlacklist() {
        if(!isDone) return List.of();
        return blacklist;
    }

    private final Map<String, SkyblockBlacklistEntry> byName = new HashMap<>();

    public SkyblockBlacklistEntry tryGetEntry(Player player) {
        if(!isDone || player == null) return null;
        return byName.computeIfAbsent(ToolList.getInstance().deleteColorCode(player.getName().getString()), k -> {
            for (SkyblockBlacklistEntry entry : getBlacklist()) {
                if(entry.playername.equals(k)) return entry;
            }
            return null;
        });
    }

    public List<SkyblockBlacklistEntry> getGroupedByType(SkyblockBlacklistType type) {
        if(!isDone || type == null) return List.of();

        return groupedByType.computeIfAbsent(type, k -> {
            List<SkyblockBlacklistEntry> list = new ArrayList<>();
            for (SkyblockBlacklistEntry entry : getBlacklist()) {
                if(entry.type == k) {
                    list.add(entry);
                }
            }
            return Collections.unmodifiableList(list);
        });
    }

    public record SkyblockBlacklistEntry(@Nullable SkyblockBlacklistType type, String uuid, String playername, String reason) {

    }

    public enum SkyblockBlacklistType {
        ALL("all", Color.WHITE),
        SCAMMER("scammers", Color.RED),
        DUNGEON("dungeon", Color.YELLOW),
        MONKEY("monkeys", Color.CYAN),
        ;

        public final String type;
        public final Color color;

        SkyblockBlacklistType(String type, Color color) {
            this.type = type;
            this.color = color;
        }

        public static SkyblockBlacklistType lookup(String type) {
            for(SkyblockBlacklistType t : values()) {
                if(t.type.equals(type)) return t;
            }
            return null;
        }

    }

    public boolean isDone() {
        return isDone;
    }

    public boolean isErrored() {
        return isErrored;
    }

    public boolean isAvaliable() {
        return isDone && !isErrored;
    }
}
