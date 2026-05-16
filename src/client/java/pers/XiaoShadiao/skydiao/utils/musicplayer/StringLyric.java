package pers.XiaoShadiao.skydiao.utils.musicplayer;

public record StringLyric(String lyric) {

    public static final StringLyric noLyricInstance = new StringLyric("** No lyric **");
    public static final StringLyric emptyInstance = new StringLyric("");

}
