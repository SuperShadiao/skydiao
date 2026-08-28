package pers.XiaoShadiao.skydiao.utils.musicplayer.musicgetter.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javazoom.jl.decoder.Bitstream;
import org.apache.commons.io.FileUtils;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicInfo;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NeteaseCloud extends MusicPlatform {

    private List<MusicInfo> search(String song, int page) throws Exception {
        List<MusicInfo> list = new ArrayList<>();
        String result;
        try (InputStream is = fetchMusic("https://music.163.com/api/search/get/web?csrf_token=hlpretag=&hlposttag=&s=" + URLEncoder.encode(song, StandardCharsets.UTF_8) + "&type=1&offset=" + (page - 1) + "&total=true&limit=100", false, uc -> uc.setRequestProperty("Host", "music.163.com"), null)) {
            result = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        try {
            ToolList.getInstance().log.info(result.substring(0, Math.min(result.length(), 3000)));
        } catch (Exception e) {}

        JsonObject joo = JsonParser.parseString(result).getAsJsonObject();
        if (joo.has("abroad") && joo.get("abroad").getAsBoolean())
            throw new RuntimeException("网易云返回了aboard: true, 你是开加速器了吗? 是的话请开启左下角的VPN模式");
        JsonArray ja = joo.get("result").getAsJsonObject().get("songs").getAsJsonArray();
        for (JsonElement je : ja) {
            MusicInfo mi = new MusicInfo();
            JsonObject jo = je.getAsJsonObject();
            String id = jo.get("id").getAsString();
            String name = jo.get("name").getAsString();
            List<String> temp = new ArrayList<>();
            for (JsonElement je2 : jo.get("artists").getAsJsonArray()) {
                temp.add(je2.getAsJsonObject().get("name").getAsString());
            }
            String singer = String.join(", ", temp);

            mi.hashOrID = id;
            mi.name = name;
            mi.singer = singer;
            mi.type = "NE";

            list.add(mi);
        }
        return list;
    }

    @Override
    public void downloadMusic(MusicInfo mi) throws Exception {
        File musicFile, musicLyric;
        musicFile = mi.musicFile;
        boolean flag;
        try {
            flag = new Bitstream(new ByteArrayInputStream(FileUtils.readFileToByteArray(musicFile))).readFrame() != null;
            if (!flag) mi.flagBroken("mp3文件无效");
        } catch (Throwable e) {
            flag = false;
        }

        if ((musicFile.exists() & (musicLyric = mi.musicLyric).exists()) && flag) {
            ToolList.getInstance().log.info("文件存在, 跳过下载");
        } else {
            String url = "https://music.163.com/song/media/outer/url?id=" + mi.hashOrID;
            ToolList.getInstance().log.info(url);

            ToolList.getInstance().log.info("下载音乐");

            Consumer<String> redirectConsumer = redirect -> {
                if (!redirect.endsWith("404")) mi.URL = redirect;
                else throw new RuntimeException("无版权或要VIP");
            };

            try (InputStream bytes = fetchMusic(url, false, null, redirectConsumer)) {

                byte[] bytes1;
                try (InputStream is = fetchMusic("https://music.163.com/api/song/lyric?id=" + mi.hashOrID + "&lv=-1&kv=-1&tv=-1")) {
                    bytes1 = is.readAllBytes();
                }

                String result1 = new String(bytes1, StandardCharsets.UTF_8);
                ToolList.getInstance().log.info("下载完成, 写出文件中");

                try {
                    String imgUrl = "https://music.163.com/api/song/detail?ids=[" + mi.hashOrID + "]";

                    String result4;
                    try (InputStream is = fetchMusic(imgUrl)) {
                        result4 = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    }

                    Pattern pattern = Pattern.compile("\\{.*\\}");
                    Matcher matcher = pattern.matcher(result4);

                    if (matcher.find()) {
                        String json = matcher.group();
                        JsonObject jsonObject = JsonParser.parseString(json)
                                .getAsJsonObject()
                                .getAsJsonArray("songs")
                                .iterator()
                                .next()
                                .getAsJsonObject()
                                .getAsJsonObject("album");
                        mi.imgURL = jsonObject.get("picUrl").getAsString() + "?param=400y400";
                    } else throw new RuntimeException("IMG fetch failed");

                    if (mi.imgFile != null && ToolList.getInstance().stringHasContext(mi.imgURL)) {
                        try (InputStream is = fetchMusic(mi.imgURL)) {
                            FileUtils.copyInputStreamToFile(is, mi.imgFile);
                        }
                    }
                } catch (Exception e) {
                    ToolList.getInstance().log.warn("获取 " + mi + " 图标: " + e);
                }

                FileUtils.copyInputStreamToFile(bytes, musicFile);
                FileUtils.writeStringToFile(musicLyric, JsonParser.parseString(result1).getAsJsonObject().get("lrc").getAsJsonObject().get("lyric").getAsString(), "UTF-8");
                ToolList.getInstance().log.info("写出完成");
            }
        }
    }

    @Override
    public MusicInfo regenerateMusicInstance(MusicInfo musicInfo) {
        return musicInfo;
    }

    @Override
    public List<MusicInfo> searchMusic(String musicName) throws Exception {
        return search(musicName, 1);
    }

    @Override
    public String getType() {
        return "NE";
    }

    @Override
    public String getDisplayName() {
        return "网易云";
    }

    @Override
    public boolean canDownloadVIPMusic() {
        return false;
    }
}
