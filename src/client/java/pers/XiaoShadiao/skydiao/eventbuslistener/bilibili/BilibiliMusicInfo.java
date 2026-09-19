package pers.XiaoShadiao.skydiao.eventbuslistener.bilibili;

import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicInfo;
import top.mrxiaom.bili.live.runtime.data.Dm;

public class BilibiliMusicInfo extends MusicInfo {

    public final Dm dm;
    private boolean skip;

    public BilibiliMusicInfo(MusicInfo mi, Dm user) {
        this.dm = user;
        replace(mi);
    }

    public void flagSkip() {
        skip = true;
    }

    public boolean isSkiped() {
        return skip;
    }
}
