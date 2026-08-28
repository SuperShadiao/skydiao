package pers.XiaoShadiao.skydiao.utils.musicplayer.musicgetter.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicInfo;

import java.io.File;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Kugou extends MusicPlatform {

    private List<MusicInfo> search(String song, int page) throws Exception {
        List<MusicInfo> list = new ArrayList<>();
        String result;
        try (InputStream is = fetchMusic("https://songsearch.kugou.com/song_search_v2?platform=WebFilter&ver=1&pagesize=100&sver=5&tag=em&correct=1&keyword=" + URLEncoder.encode(song, "UTF-8") + "&page=" + page + "&clientver=&filter=2&iscorrection=1")) {
            result = new String(is.readAllBytes(), "UTF-8");
        }
        try {
            ToolList.getInstance().log.info(result.substring(0, Math.min(result.length(), 3000)));
        } catch (Exception e) {}

        JsonObject joo = JsonParser.parseString(result).getAsJsonObject();
        if ("Nonsupport Area".equals(joo.get("error_msg").getAsString()))
            throw new RuntimeException("酷狗返回了error_msg: Nonsupport Area, 你是开加速器了吗? 是的话请开启左下角的VPN模式");
        JsonArray array = joo.get("data").getAsJsonObject().get("lists").getAsJsonArray();
        for (JsonElement je : array) {
            JsonObject jo = je.getAsJsonObject();
            MusicInfo mi = new MusicInfo();
            String songName = jo.get("SongName").getAsString().replaceAll("<.*?>", "").replace("\"", ""),
                    singerName = jo.get("SingerName").getAsString().replaceAll("<.*?>", "").replace("\"", ""),
                    hash = jo.get("FileHash").getAsString(),
                    albumId = jo.get("AlbumID").getAsString(),
                    imageUrl = jo.get("Image").getAsString();
            mi.hashOrID = hash;
            mi.name = songName;
            mi.singer = singerName;
            mi.albumID = albumId;
            mi.type = "KG";
            mi.imgURL = imageUrl == null ? null : imageUrl.replace("{size}", "400");
            list.add(mi);
        }
        return list;
    }

    @Override
    public void downloadMusic(MusicInfo mi) throws Exception {
        File musicFile, musicLyric;
        String albumId2 = mi.albumID;

        if ((musicFile = mi.musicFile).exists() & (musicLyric = mi.musicLyric).exists()) {
            ToolList.getInstance().log.info("文件存在, 跳过下载");
        } else {
            String url = "https://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=" + mi.hashOrID;
            String result1;
            try (InputStream is = fetchMusic(url)) {
                result1 = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            ToolList.getInstance().log.info(result1);

            ToolList.getInstance().log.info("下载音乐");
            try {
                if (!mi.albumID.isEmpty()) Integer.parseInt(mi.albumID);
            } catch (NumberFormatException e) {
                ToolList.getInstance().log.error("albumid缺失!!!尝试自动补齐...");
                ToolList.getInstance().log.error(mi.name + " - " + mi.singer);
                List<MusicInfo> searchResult = search(mi.name + " - " + mi.singer, 1);
                for (MusicInfo mii : searchResult) {
                    if (mii.hashOrID.equals(mi.hashOrID)) {
                        ToolList.getInstance().log.error(mii.hashOrID + " - " + mii.albumID);
                        mi.albumID = mii.albumID;
                        downloadMusic(mi);
                        return;
                    }
                }
                ToolList.getInstance().log.error(mi.singer);
                searchResult = search(mi.singer, 1);
                for (MusicInfo mii : searchResult) {
                    if (mii.hashOrID.equals(mi.hashOrID)) {
                        mi.albumID = mii.albumID;
                        downloadMusic(mi);
                        return;
                    }
                }
                ToolList.getInstance().log.error(mi.name);
                searchResult = search(mi.name, 1);
                for (MusicInfo mii : searchResult) {
                    if (mii.hashOrID.equals(mi.hashOrID)) {
                        mi.albumID = mii.albumID;
                        downloadMusic(mi);
                        return;
                    }
                }
            }
            JsonObject obj1 = JsonParser.parseString(result1).getAsJsonObject();
            String downloadURL;
            mi.URL = downloadURL = obj1.get("url").getAsString();
            mi.imgURL = obj1.get("album_img").getAsString().replace("{size}", "400");

            String lyric = "";
            String lyricurl;

            try (InputStream b2 = fetchMusic(downloadURL)) {
                try {
                    if (!ToolList.getInstance().stringHasContext(mi.lyricURL)) {
                        lyricurl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=" + mi.hashOrID + "&hash=" + mi.hashOrID;
                        String lyricSearchResult;
                        try (InputStream is = fetchMusic(lyricurl)) {
                            lyricSearchResult = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        }
                        JsonObject jo2 = JsonParser.parseString(lyricSearchResult).getAsJsonObject().get("candidates").getAsJsonArray().get(0).getAsJsonObject();

                        String lyricID = jo2.get("id").getAsString();
                        String accessKey = jo2.get("accesskey").getAsString();
                        mi.lyricURL = lyricurl = "https://lyrics.kugou.com/download?ver=1&client=pc&id=" + lyricID + "&accesskey=" + accessKey + "&fmt=lrc&charset=utf8";
                    }
                    lyricurl = mi.lyricURL;

                    if (ToolList.getInstance().stringHasContext(mi.lyricURL)) {
                        String lyricResult;
                        try (InputStream is = fetchMusic(lyricurl)) {
                            lyricResult = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        }
                        lyric = new String(Base64.getDecoder().decode(JsonParser.parseString(lyricResult).getAsJsonObject().get("content").getAsString()), StandardCharsets.UTF_8);
                    }
                } catch (Exception e) {
                    ToolList.getInstance().log.warn("音乐 " + mi + " 似乎没有歌词: " + e);
                }

                ToolList.getInstance().log.info("下载完成, 写出文件中");

                FileUtils.copyInputStreamToFile(b2, musicFile);
            }
            FileUtils.writeStringToFile(musicLyric, lyric, "UTF-8");

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
        return search(musicName, 1);
    }

    @Override
    public String getType() {
        return "KG";
    }

    @Override
    public String getDisplayName() {
        return "酷狗";
    }

    @Override
    public boolean canDownloadVIPMusic() {
        return false;
    }

}
