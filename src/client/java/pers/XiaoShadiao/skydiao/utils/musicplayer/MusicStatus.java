package pers.XiaoShadiao.skydiao.utils.musicplayer;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;
import pers.XiaoShadiao.skydiao.SkyDiaoModClient;

import javax.sound.sampled.FloatControl;
import javax.sound.sampled.FloatControl.Type;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

public class MusicStatus extends PlaybackListener {
    
    private final MusicPlayer player;
    private final LyricParser lyric;
    private boolean finished;
    private float musicLength;
    private int musicFramesize;
    @SuppressWarnings("unused")
    private double startPlayTime;
    private FloatControl volumeCtrl;
    
    public MusicStatus(File f, File lyric) {
        try {
            MusicFileReader is = new MusicFileReader(f);
            this.player = new MusicPlayer(is, this);
            this.player.setPlayBackListener(this);
            this.lyric = new LyricParser(lyric);
        } catch (JavaLayerException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public MusicPlayer getPlayer() {
        return player;
    }
    
    public boolean isFinished() {
        return finished;
    }
    
    @Override
    public void playbackFinished(PlaybackEvent evt) {
        finished = true;
        super.playbackFinished(evt);
    }
    
    @Override
    public void playbackStarted(PlaybackEvent evt) {
        startPlayTime = System.currentTimeMillis();
        finished = false;
        super.playbackStarted(evt);
    }
    
    public float getLength() {
        if(musicLength == 0) musicLength = player.getMusicLengthMillsecond();
        return musicLength;
    }
    
    public int getFrame() {
        return musicFramesize;
    }
    
    public StringLyric getLyric(int offerset) {
        
        // String result = finished ? null : lyric.getCurrentLyric((double) (System.currentTimeMillis() - startPlayTime) / 1000d, 0);
        StringLyric result = finished ? null : lyric.getCurrentLyric(player.currentPlayTime(), offerset);
        
        return result == null ? StringLyric.noLyricInstance : result;
    }

    public LyricParser getLyric() {
        return lyric;
    }
    
    public FloatControl getVolumeCtrl() {

        if(volumeCtrl == null) {
            try {
                JavaSoundAudioDevice currentAD = (JavaSoundAudioDevice) player.getCurrentAD();
                Field sourceField = currentAD.getClass().getDeclaredField("source");
                sourceField.setAccessible(true);
                volumeCtrl = (FloatControl) ((SourceDataLine) sourceField.get(currentAD)).getControl(Type.MASTER_GAIN);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
        if(volumeCtrl == null) throw new RuntimeException("无法获取音量控制器! 请联系小沙雕QQ3381949033或者加入雕の窝" + SkyDiaoModClient.CONST_QQGROUP_MAIN + "处理");
        
        return volumeCtrl;
    }

    public void setVolumeCtrl(int a) {
        FloatControl fc = getVolumeCtrl();
        fc.setValue(a == 0 ? -80.0F : (float)((double)a * 0.2 - 35.0));
    }
    
}
