package pers.XiaoShadiao.skydiao.utils.musicplayer;

import com.google.gson.*;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.commons.io.FileUtils;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.musicplayer.musicgetter.impl.MusicPlatform;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MusicListManager {

    private static List<MusicInfo> musics = new ArrayList<>();
    private static List<SharingData> sharingDatas = new ArrayList<>();

    public static List<MusicInfo> getMusics() {
        return musics;
    }

    public static boolean musicInList(MusicInfo music) {
        return musics.contains(music);
    }

    public static MusicInfo jsonToMI(JsonObject jo) {
        MusicInfo mi = new MusicInfo();
        try {
            Objects.requireNonNull(mi.name = jo.get("name").getAsString());
        } catch(Exception e) {
            mi.name = "";
        }
        try {
            Objects.requireNonNull(mi.singer = jo.get("singer").getAsString());
        } catch(Exception e) {
            mi.singer = "";
        }
        try {
            Objects.requireNonNull(mi.hashOrID = jo.get("hashorid").getAsString());
        } catch(Exception e) {
            mi.hashOrID = "";
        }
        try {
            Objects.requireNonNull(mi.imgURL = jo.get("imgurl").getAsString());
        } catch(Exception e) {
            mi.imgURL = "";
        }
        try {
            Objects.requireNonNull(mi.lyricURL = jo.get("lyricurl").getAsString());
        } catch(Exception e) {
            mi.lyricURL = "";
        }
        try {
            Objects.requireNonNull(mi.URL = jo.get("url").getAsString());
        } catch(Exception e) {
            mi.URL = "";
        }
        try {
            Objects.requireNonNull(mi.albumID = jo.get("albumid").getAsString());
        } catch(Exception e) {
            mi.albumID = "";
        }
        try {
            Objects.requireNonNull(mi.type = jo.get("type").getAsString());
        } catch(Exception e) {
            mi.type = "CUSTOM";
        }
        try {
            mi.isDisabled = jo.get("disabled").getAsBoolean();
        } catch(Exception e) {
            e.printStackTrace();
        }

        PlayerThread.fixMIFileVeriable(mi);
        return mi;
    }

    public static JsonObject MIToJson(MusicInfo mi) {
        JsonObject jo = new JsonObject();
        if(mi.singer != null) jo.addProperty("singer", mi.singer);
        if(mi.name != null) jo.addProperty("name", mi.name);
        if(mi.hashOrID != null) jo.addProperty("hashorid", mi.hashOrID);
        if(mi.imgURL != null) jo.addProperty("imgurl", mi.imgURL);
        if(mi.lyricURL != null) jo.addProperty("lyricurl", mi.lyricURL);
        if(mi.URL != null) jo.addProperty("url", mi.URL);
        if(mi.type != null) jo.addProperty("type", mi.type);
        if(mi.albumID != null) jo.addProperty("albumid", mi.albumID);
        jo.addProperty("disabled", mi.isDisabled);
        return jo;
    }

    public static List<MusicInfo> loadMusicFromFolder() {
        List<MusicInfo> list = new ArrayList<>();
        try {
            JsonArray ja = JsonParser.parseString(FileUtils.readFileToString(new File(ConfigManager.hypixelhelpermusicfolder.getValue(), "musicList.json"), StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement je : ja) {
                JsonObject jo = je.getAsJsonObject();
                list.add(jsonToMI(jo));
            }
        } catch(Throwable e) {
            list.clear();
        }

        if(musics != null) {
            list.replaceAll((newMI) -> {
                int index = musics.indexOf(newMI);
                if(index != -1) {
                    return musics.get(index).clone();
                }
                return newMI;
            });
            list.forEach(MusicInfo::getTexture);
        }
        for (MusicInfo musicInfo : list) {
            if(!musicInfo.canPlay()) {
                addFixQueue(musicInfo);
            }
        }

        return musics = list;
    }

    public static void save() {
        JsonArray ja = new JsonArray();

        for(MusicInfo mi : musics) {
            ja.add(MIToJson(mi));
        }
        Gson g = new GsonBuilder().setPrettyPrinting().create();
        try {
            FileUtils.writeStringToFile(new File(ConfigManager.hypixelhelpermusicfolder.getValue(), "musicList.json"), g.toJson(ja), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getDisplayName(MusicInfo musicInfo, boolean showInQueue) {
        return (musicInfo.isBroken() ? "§c" : musicInfo.isDownloading ? "§b" : musicInfo.isDisabled ? ChatFormatting.GRAY + "" + ChatFormatting.STRIKETHROUGH + "(禁用) " : musicInfo.equals(PlayerThread.currentMusic) ? "§a" : "") + musicInfo.name + " - " + musicInfo.singer + (musicInfo.isBroken() ? (" §c(文件损坏: " + musicInfo.brokenReason + ")") : "") + (showInQueue && musicInList(musicInfo) ? " §e(在队列中)" : "");
    }

    public static void add(MusicInfo musicInfo) {
        musics.add(musicInfo);
        musicInfo.fixMIFileVeriable();
        if(!musicInfo.canPlay()) addFixQueue(musicInfo);
        save();
    }

    private static final Executor musicFileFixer = Executors.newWorkStealingPool();

    public static void addFixQueue(MusicInfo mi) {
        addFixQueue(mi, null);
    }

    public static void addFixQueue(MusicInfo mi, BooleanConsumer callback) {
        if(mi.isBroken()) {
            if(callback != null) callback.accept(false);
            return;
        }
        musicFileFixer.execute(() -> {
            MusicPlatform platform = MusicPlatform.getMusicPlatform(mi.type);
            if(platform != null) {
                try {
                    mi.fixMIFileVeriable();
                    mi.isDownloading = true;
                    platform.downloadMusic(mi);
                    if(callback != null) callback.accept(true);
                } catch (Exception e) {
                    e.printStackTrace();
                    mi.musicLyric.delete();
                    mi.musicFile.delete();
                    mi.flagBroken(e.toString());
                    addFixQueue(mi);
                } finally {
                    mi.isDownloading = false;
                }
            }
        });
    }

    public static void removeMusic(MusicInfo musicInfo) {
        boolean removed = false;
        Iterator<MusicInfo> it = musics.iterator();
        while(it.hasNext()) {
            MusicInfo mi = it.next();
            if(mi == musicInfo) {
                it.remove();
                removed = true;
                break;
            }
        }
        if(removed) save();
    }

    public static boolean isMusicFolderVaild() {
        return ConfigManager.hypixelhelpermusicfolder.getValue().isBlank() || !new File(ConfigManager.hypixelhelpermusicfolder.getValue()).exists();
    }

    public static void ensureMusicFolderVaildAndRun(Runnable run) {
        Screen screen = ToolList.mc.screen;
        ensureMusicFolderVaildAndRun(run, () -> ToolList.mc.setScreen(screen));
    }

    public static void ensureMusicFolderVaildAndRun(Runnable run, Runnable cancelled) {
        if(MusicListManager.isMusicFolderVaild()) {
            ToolList.mc.schedule(() -> ToolList.mc.setScreen(new ConfirmScreen(flag -> {
                if(flag) {
                    File folder = new File(ToolList.mc.gameDirectory, "XSDKGMusic");
                    if(folder.mkdirs() || folder.isDirectory()) {
                        ConfigManager.hypixelhelpermusicfolder.setValue(folder.getAbsolutePath());
                        MusicListManager.loadMusicFromFolder();
                        ToolList.mc.setScreen(null);
                        run.run();
                    } else {
                        cancelled.run();
                    }
                } else {
                    cancelled.run();
                }
            }, Component.literal("指定的文件夹目录不存在"), Component.literal("你想要设置为默认文件夹并创建吗?"))));
        } else {
            run.run();
        }
    }

    public record SharingData(MusicInfo musicInfo, int randomId, long time) {

    }

    public static int storeTempSharingMusic(MusicInfo musicInfo) {
        sharingDatas.removeIf(sd -> System.currentTimeMillis() - sd.time > 120000);
        int randomId = ToolList.getInstance().random.nextInt();
        sharingDatas.add(new SharingData(musicInfo, randomId, System.currentTimeMillis()));
        return randomId;
    }

    public static MusicInfo getSharingMusic(int randomId) {
        for(SharingData sd : sharingDatas) {
            if(sd.randomId == randomId) {
                return sd.musicInfo;
            }
        }
        return null;
    }

}
