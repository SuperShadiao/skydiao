package pers.XiaoShadiao.skydiao.utils.musicplayer;

import net.minecraft.resources.Identifier;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.io.File;
import java.util.Objects;

public class MusicInfo {

    public String name;
    public String singer;
    public String hashOrID;
    public String imgURL;
    public String URL;
    public String lyricURL;
    public String albumID;

    public String type;

    private Identifier texture;
    public String brokenReason;
    
    public boolean isDownloading;
    public boolean isDisabled;
    private int isBroken;
    
    public File musicLyric, musicFile, imgFile;

    public boolean hasImage() {
        return ToolList.getInstance().stringHasContext(imgURL) && imgFile != null && imgFile.exists();
    }

    public Identifier getTexture() {
//        if(texture == null && hasImage()) {
//            try {
//                if(!isDownloading) {
//                    Identifier temp = Identifier.fromNamespaceAndPath("skydiao", "music_" + ToolList.getInstance().getMD5(singer + name + hashOrID).toLowerCase());
//                    ToolList.mc.getTextureManager().registerAndLoad(temp, new ImageTexture(temp, FileUtils.readFileToByteArray(imgFile)));
//                    texture = temp;
//                }
//            } catch (Exception e) {
//                return null;
//            }
//        }

        return texture;
    }

    public void flagBroken(String reason) {
        brokenReason = reason;
        isBroken++;
    }
    
    public MusicInfo replace(MusicInfo mi) {
        if(ToolList.getInstance().stringHasContext(name)) name = mi.name;
        if(ToolList.getInstance().stringHasContext(singer)) singer = mi.singer;
        if(ToolList.getInstance().stringHasContext(hashOrID)) hashOrID = mi.hashOrID;
        if(ToolList.getInstance().stringHasContext(imgURL)) imgURL = mi.imgURL;
        if(ToolList.getInstance().stringHasContext(URL)) URL = mi.URL;
        if(ToolList.getInstance().stringHasContext(lyricURL)) lyricURL = mi.lyricURL;
        if(ToolList.getInstance().stringHasContext(albumID)) albumID = mi.albumID;

        return this;
    }
    
    public void setNotBroken() {
        isBroken = 0;
    }
    
    public boolean isBroken() {
        return isBroken > 5;
    }

    public int errCount() {
        return isBroken;
    }

    public String toString() {
        String s = name + " - " + singer;

        s = s.length() > 20 ? s.substring(0, 20) + "..." : s;

        return s;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MusicInfo musicInfo)) return false;
        return Objects.equals(hashOrID, musicInfo.hashOrID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hashOrID);
    }

}
