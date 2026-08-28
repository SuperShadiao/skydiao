package pers.XiaoShadiao.skydiao.utils.musicplayer.musicgetter.impl;

import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicInfo;
import pers.XiaoShadiao.skydiao.utils.musicplayer.musicgetter.IMusicDownloader;
import pers.XiaoShadiao.skydiao.utils.musicplayer.musicgetter.IMusicInstanceRegeneration;
import pers.XiaoShadiao.skydiao.utils.musicplayer.musicgetter.IMusicSearcher;

import java.io.InputStream;
import java.net.URLConnection;
import java.util.List;
import java.util.function.Consumer;

public abstract class MusicPlatform implements IMusicSearcher, IMusicDownloader, IMusicInstanceRegeneration {

    private static final List<MusicPlatform> musicPlatforms = List.of(
            new Kugou(),
            new NeteaseCloud(),
            new OVOOA2()
    );

    public static List<MusicPlatform> getMusicPlatforms() {
        return musicPlatforms;
    }

    public static MusicPlatform getMusicPlatform(String type) {
        return musicPlatforms.stream().filter(mp -> mp.getType().equals(type)).findFirst().orElse(null);
    }

    public static MusicPlatform getMusicPlatform(MusicInfo musicInfo) {
        return getMusicPlatform(musicInfo.type);
    }

    abstract public String getType();

    public abstract String getDisplayName();

    public static int invokeFetchCount = 0;

    protected InputStream fetchMusic(String url) {
        invokeFetchCount++;
        if(invokeFetchCount % 2 == 0) {
            return ToolList.getInstance().makeReqToURL_E(url);
        } else {
            return ToolList.getInstance().makeReqToURL(url);
        }
    }

    protected InputStream fetchMusic(String url, boolean allowErrorStream, Consumer<URLConnection> ucin, Consumer<String> onRedirect) {
        invokeFetchCount++;
        if(invokeFetchCount % 2 == 0) {
            return ToolList.getInstance().makeReqToURL_E(url);
        } else {
            return ToolList.getInstance().makeReqToURL(url, allowErrorStream, ucin, onRedirect);
        }
    }

    public abstract boolean canDownloadVIPMusic();

}
