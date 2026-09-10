package pers.XiaoShadiao.skydiao.utils.musicplayer.musicgetter.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class OVOOA2 extends MusicPlatform {

    private List<MusicInfo> search(String song) throws Exception {
        String name = URLEncoder.encode(song, StandardCharsets.UTF_8);
        String result;
        try (InputStream is = fetchMusic("https://oiapi.net/API/Music_163?name=" + name + "&n=1&key=oiapi-27e1b651-bc89-5a56-b840-1a738fb3baf8")) {
            result = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        System.out.println(result);

        MusicInfo mi = new MusicInfo();
        JsonObject joo = JsonParser.parseString(result).getAsJsonObject().getAsJsonObject("data");

        mi.name = joo.get("name").getAsString();
        mi.singer = Collections.list(new Enumeration<JsonElement>() {
            private final Iterator<JsonElement> iterator = joo.get("singers").getAsJsonArray().iterator();

            @Override
            public boolean hasMoreElements() {
                return iterator.hasNext();
            }

            @Override
            public JsonElement nextElement() {
                return iterator.next();
            }
        }).stream().map(a -> a.getAsJsonObject().get("name").getAsString()).collect(Collectors.joining(", "));

        mi.URL = joo.get("url").getAsString();
        mi.type = "OVOOA2";
        mi.albumID = joo.toString();
        mi.hashOrID = joo.get("id").getAsString();
        mi.imgURL = joo.get("picurl").getAsString() + "?param=400y400";

        return Collections.singletonList(mi);
    }

    private List<MusicInfo> regenByID(String id0) throws Exception {
        String id = URLEncoder.encode(id0, StandardCharsets.UTF_8);
        String result;
        try (InputStream is = fetchMusic("https://oiapi.net/API/Music_163?id=" + id + "&n=1&key=oiapi-27e1b651-bc89-5a56-b840-1a738fb3baf8")) {
            result = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        System.out.println(result);

        MusicInfo mi = new MusicInfo();
        JsonObject joo = JsonParser.parseString(result).getAsJsonObject().getAsJsonArray("data").get(0).getAsJsonObject();

        mi.name = joo.get("name").getAsString();
        mi.singer = Collections.list(new Enumeration<JsonElement>() {
            private final Iterator<JsonElement> iterator = joo.get("singers").getAsJsonArray().iterator();

            @Override
            public boolean hasMoreElements() {
                return iterator.hasNext();
            }

            @Override
            public JsonElement nextElement() {
                return iterator.next();
            }
        }).stream().map(a -> a.getAsJsonObject().get("name").getAsString()).collect(Collectors.joining(", "));

        mi.URL = joo.get("url").getAsString();
        mi.type = "OVOOA2";
        mi.albumID = joo.toString();
        mi.hashOrID = joo.get("id").getAsString();
        mi.imgURL = joo.get("picurl").getAsString() + "?param=400y400";

        return Collections.singletonList(mi);
    }

    @Override
    public void downloadMusic(MusicInfo mi) throws Exception {
        downloadMusic(mi, null);
    }

    private void downloadMusic(MusicInfo mi, JsonObject jooInfo) throws Exception {
        File musicFile, musicLyric;

        mi.fixMIFileVeriable();
        if ((musicFile = mi.musicFile).exists() & (musicLyric = mi.musicLyric).exists()) {
            ToolList.getInstance().log.info("文件存在, 跳过下载");
        } else {
            if (!ToolList.getInstance().stringHasContext(mi.URL) || mi.albumID == null) {
                // String name = URLEncoder.encode(mi.name + " " + mi.singer, StandardCharsets.UTF_8);

                mi.replace(regenByID(mi.hashOrID).getFirst());

                downloadMusic(mi, jooInfo);
                return;
            }

            try (InputStream is = fetchMusic(mi.URL)) {
                FileUtils.copyInputStreamToFile(is, musicFile);
            } catch (MalformedURLException e) {
                mi.URL = mi.albumID = "";
                throw new RuntimeException(e);
            } catch (Exception e) {
                Throwable e1 = e;
                boolean timeout = false;
                while ((e1 = e1.getCause()) != null) {
                    if (e1 instanceof java.net.SocketTimeoutException) {
                        timeout = true;
                        break;
                    }
                }
                if (timeout) {
                    try (InputStream is = fetchMusic(mi.URL)) {
                        FileUtils.copyInputStreamToFile(is, musicFile);
                    }
                } else {
                    throw new RuntimeException(e);
                }
            }

            try (InputStream is = new FileInputStream(musicFile)) {
                if (is.available() < 16384) {
                    mi.albumID = null;
                    throw new RuntimeException("Bad music file");
                }
            }

            ToolList.getInstance().log.info("下载完成, 写出文件中");

            String result1;
            try (InputStream is = fetchMusic("https://music.163.com/api/song/lyric?id=" + mi.hashOrID + "&lv=-1&kv=-1&tv=-1")) {
                result1 = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            FileUtils.writeStringToFile(musicLyric, JsonParser.parseString(result1).getAsJsonObject().get("lrc").getAsJsonObject().get("lyric").getAsString(), "UTF-8");

            try {
                if (mi.imgFile != null && ToolList.getInstance().stringHasContext(mi.imgURL)) {
                    try (InputStream is = fetchMusic(mi.imgURL)) {
                        FileUtils.copyInputStreamToFile(is, mi.imgFile);
                    }
                }
            } catch (Exception e) {
                ToolList.getInstance().log.warn("获取 " + mi + " 图标: " + e);
            }

            ToolList.getInstance().log.info("写出完成");
        }

        mi.musicFile = musicFile;
        mi.musicLyric = musicLyric;
    }

    @Override
    public MusicInfo regenerateMusicInstance(MusicInfo musicInfo) {
        return musicInfo;
    }

    @Override
    public List<MusicInfo> searchMusic(String musicName) throws Exception {
        return search(musicName);
    }

    @Override
    public String getType() {
        return "OVOOA2";
    }

    @Override
    public String getDisplayName() {
        return "Ovooa2";
    }

    @Override
    public boolean canDownloadVIPMusic() {
        return true;
    }
}
