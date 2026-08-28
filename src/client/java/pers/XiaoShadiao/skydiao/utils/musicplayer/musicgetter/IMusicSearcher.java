package pers.XiaoShadiao.skydiao.utils.musicplayer.musicgetter;

import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicInfo;

import java.util.List;

public interface IMusicSearcher {

    public List<MusicInfo> searchMusic(String musicName) throws Exception;

}
