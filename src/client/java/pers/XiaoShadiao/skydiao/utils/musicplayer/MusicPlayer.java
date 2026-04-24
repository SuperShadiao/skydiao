package pers.XiaoShadiao.skydiao.utils.musicplayer;

import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.advanced.PlaybackEvent;
import javazoom.jl.player.advanced.PlaybackListener;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MusicPlayer extends AdvancedPlayer {

    private MusicStatus ms;
    private Bitstream bitstream;
    private Decoder decoder;
    private AudioDevice audio;
    private boolean closed = false;
    private boolean complete = false;
    private int lastPosition = 0;
    private PlaybackListener listener;
    private int currentFrame;
    private int availableCount;
    private int currentAvailableBytes;
    private InputStream is;
    
    private boolean stopped = false;
    
    private Long musicLength;
    private static final InputStream useless = new ByteArrayInputStream(new byte[] {});
    
    public MusicPlayer(InputStream stream) throws JavaLayerException, IOException {
        this(stream, (AudioDevice)null);
    }

    public MusicPlayer(InputStream stream, AudioDevice device) throws JavaLayerException, IOException {
        super(useless, device);
        this.bitstream = new Bitstream(is = stream);
        currentAvailableBytes = availableCount = is.available();
        if (device != null) {
            this.audio = device;
        } else {
            this.audio = FactoryRegistry.systemRegistry().createAudioDevice();
        }
        this.audio.open(this.decoder = new Decoder());
    }

    public void play() throws JavaLayerException {
        this.play(Integer.MAX_VALUE);
    }

    public boolean play(int frames) throws JavaLayerException {

        boolean ret = true;
        currentFrame = 0;
        if (this.listener != null) {
            this.listener.playbackStarted(this.createEvent(PlaybackEvent.STARTED));
        }

        while(frames-- > 0 && ret && !stopped && !PlayerThread.switchMusicFlag) {

            if(currentFrame++ % 10 == 0) PlayerThread.threadStatus_SetAlive();
            ret = this.decodeFrame();
            try {
                currentAvailableBytes = is.available();
            } catch (IOException e) {
                currentAvailableBytes = 0;
            }
            if(PlayerThread.current != null) PlayerThread.current.setVolumeCtrl(ConfigManager.xsdmusicvolume.getValue());
        }

        if(PlayerThread.switchMusicFlag) PlayerThread.switchMusicFlag = false;
        
        AudioDevice out = this.audio;
        if (out != null) {
            out.flush();
            synchronized(this) {
                this.complete = !this.closed;
                this.close();
            }

            if (this.listener != null) {
                this.listener.playbackFinished(this.createEvent(out, PlaybackEvent.STOPPED));
            }
        }
        if(musicLength == null) throw new RuntimeException("Bad mp3!");

        return ret;
    }

    public synchronized void close() {
        AudioDevice out = this.audio;
        if (out != null) {
            this.closed = true;
            this.audio = null;
            out.close();
            this.lastPosition = out.getPosition();

            try {
                this.bitstream.close();
            } catch (BitstreamException var3) {
            }
        }

    }

    protected boolean decodeFrame() throws JavaLayerException {
        try {
            AudioDevice out = this.audio;
            if (out == null) {
                return false;
            } else {
                Header h = this.bitstream.readFrame();
                if (h == null) {
                    return false;
                } else {
                    if(musicLength == null) musicLength = (long)h.total_ms(is.available());
                    SampleBuffer output = (SampleBuffer)this.decoder.decodeFrame(h, this.bitstream);
                    synchronized(this) {
                        out = this.audio;
                        if (out != null) {
                            out.write(output.getBuffer(), 0, output.getBufferLength());
                        }
                    }

                    this.bitstream.closeFrame();
                }

                return true;
            }

        } catch (Exception var7) {
            throw new JavaLayerException("Exception decoding audio frame", var7);
        }
    }

    protected boolean skipFrame() throws JavaLayerException {
        Header h = this.bitstream.readFrame();
        if (h == null) {
            return false;
        } else {
            this.bitstream.closeFrame();
            return true;
        }
    }

    public boolean play(int start, int end) throws JavaLayerException {
        boolean ret = true;
        int offset = start;

        while(offset-- > 0 && ret) {
            ret = this.skipFrame();
        }

        return this.play(end - start);
    }

    private PlaybackEvent createEvent(int id) {
        return this.createEvent(this.audio, id);
    }

    private PlaybackEvent createEvent(AudioDevice dev, int id) {
        return new PlaybackEvent(this, id, dev == null ? 0 : dev.getPosition());
    }

    public void setPlayBackListener(PlaybackListener listener) {
        this.listener = listener;
    }

    public PlaybackListener getPlayBackListener() {
        return this.listener;
    }
    
    public void stop() {
        this.listener.playbackFinished(this.createEvent(PlaybackEvent.STOPPED));
        stopped = true;
        // this.close();
    }

    public MusicPlayer(InputStream stream, MusicStatus in) throws JavaLayerException, IOException {
        this(stream);
        ms = in;
    }
    //    
    //    public void play() throws JavaLayerException {
    //        getPlayBackListener().playbackStarted(new PlaybackEvent(this, 0, 0));
    //        for(int i = 0; i < Integer.MAX_VALUE; i++) {
    //            if(!decodeFrame()) break;
    //        }
    //        try {
    //            stop();
    //        } catch(NullPointerException e) {}
    //    }
    
    public boolean isComplete() {
        return complete;
    }

    public int lastPosition() {
        return lastPosition;
    }

    public int getPosition() {
        return audio.getPosition();
    }
    
    public AudioDevice getCurrentAD() {
        return audio;
    }
    
    public MusicStatus getMusicStatus() {
        return ms;
    }
    
    public long getMusicLengthMillsecond() {
        if(musicLength == null) return 0;
        return musicLength;
    }
    
    public double currentPlayTime() {
        double temp = ((double)(availableCount - currentAvailableBytes) / (double)availableCount);
        double total = (double)ms.getLength() * temp;
        // ((double) audio.getPosition() - (double) header.framesize) * (double) header.ms_per_frame();
        // 工具列表.getInstance().log.info(temp);
        return total / 1000d;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

}

