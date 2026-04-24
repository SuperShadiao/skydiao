package pers.XiaoShadiao.skydiao.utils.musicplayer;

public class StringLyric {

    public static final StringLyric noLyricInstance = new StringLyric("** No lyric **");
    public static final StringLyric emptyInstance = new StringLyric("");

    public final String lyric;

    public StringLyric(String lyric) {
        this.lyric = lyric;
    }

}
