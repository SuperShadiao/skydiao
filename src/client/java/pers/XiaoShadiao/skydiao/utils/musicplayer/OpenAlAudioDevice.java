package pers.XiaoShadiao.skydiao.utils.musicplayer;

import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDevice;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 把 jlayer 解码出的 PCM 直接流式写入 Minecraft 已经初始化好的 OpenAL 上下文。
 * 用于 JavaSound 不可用的环境(例如 Android 运行时缺少 libjsound)。
 */
public class OpenAlAudioDevice implements AudioDevice {

    private static final int QUEUE_SIZE = 8;
    private static final long WAIT_TIMEOUT_MS = 300L;

    private Decoder decoder;
    private volatile boolean open;
    private volatile boolean inited;
    private int source = -1;
    private int[] buffers;
    private int bufferCursor;
    private int format;
    private int sampleRate;
    private int channels;
    private int framesWritten;
    private float gainDb = -35.0F;
    private ByteBuffer pcm;
    private CaveReverb reverb;
    private boolean soundEffectEnabled;

    @Override
    public void open(Decoder decoder) {
        // 此时还没有解码任何帧, 拿不到采样率/声道数, 真正的 OpenAL 资源延迟到第一次 write 时创建
        this.decoder = decoder;
        this.open = true;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void write(short[] samples, int off, int len) throws JavaLayerException {
        if (!open || len <= 0) {
            return;
        }
        if (!inited) {
            init();
        }
        // 在送入 OpenAL 之前就地处理这一帧 PCM; 混响状态会跨帧保留
        boolean enableSoundEffect = Boolean.TRUE.equals(ConfigManager.enablemusicplayersoundeffect.getValue());
        if (enableSoundEffect) {
            if (!soundEffectEnabled) {
                // 重新启用时重建处理器, 清掉关闭期间残留的延迟线状态
                reverb = new CaveReverb(sampleRate, channels);
            }
            reverb.process(samples, off, len, channels, sampleRate);
        }
        soundEffectEnabled = enableSoundEffect;

        int buffer = acquireBuffer();
        if (buffer == 0) {
            // 正在切歌或线程被打断, 丢弃这一帧
            return;
        }

        if (pcm.capacity() < len * 2) {
            pcm = ByteBuffer.allocateDirect(len * 2).order(ByteOrder.nativeOrder());
        }
        pcm.clear();
        pcm.asShortBuffer().put(samples, off, len);
        pcm.position(0).limit(len * 2);

        AL10.alBufferData(buffer, format, pcm, sampleRate);
        AL10.alSourceQueueBuffers(source, buffer);
        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            AL10.alSourcePlay(source);
        }
        framesWritten += len / channels;
    }

    @Override
    public void flush() {
        if (!inited) {
            return;
        }
        try {
            // 与 JavaSound 的 SourceDataLine.drain() 对齐: 等队列里剩余的数据播完
            while (AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) > 0) {
                if (AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED) > 0) {
                    if (AL10.alSourceUnqueueBuffers(source) == 0) {
                        break;
                    }
                } else if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                    break;
                } else {
                    Thread.sleep(5);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void close() {
        open = false;
        if (!inited) {
            return;
        }
        inited = false;
        try {
            stopAndClear();
            AL10.alDeleteBuffers(buffers);
            AL10.alDeleteSources(source);
        } catch (Throwable ignored) {
        } finally {
            source = -1;
            buffers = null;
            reverb = null;
        }
    }

    @Override
    public int getPosition() {
        return framesWritten;
    }

    public void setGainDb(float db) {
        gainDb = db;
        if (!inited || source == -1) {
            return;
        }
        try {
            AL10.alSourcef(source, AL10.AL_GAIN, dbToLinear(db));
        } catch (Throwable ignored) {
        }
    }

    private void init() throws JavaLayerException {
        try {
            long context = ALC10.alcGetCurrentContext();
            if (context == 0L) {
                // Minecraft 的声音引擎还没初始化好(或已销毁), 让上层稍后重试
                throw new JavaLayerException("Cannot create AudioDevice: OpenAL 上下文尚未就绪");
            }
            // OpenAL 的 capabilities 是线程级的, 播放线程不是 Minecraft 的渲染线程, 需要自行建立
            ALCCapabilities alcCaps;
            try {
                alcCaps = ALC.getCapabilities();
            } catch (IllegalStateException e) {
                alcCaps = ALC.createCapabilities(ALC10.alcGetContextsDevice(context));
            }
            try {
                AL.getCapabilities();
            } catch (IllegalStateException e) {
                AL.createCapabilities(alcCaps);
            }

            channels = decoder.getOutputChannels();
            sampleRate = decoder.getOutputFrequency();
            if (channels <= 0 || sampleRate <= 0) {
                throw new JavaLayerException("Cannot create AudioDevice: 解码器输出格式无效");
            }
            format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            reverb = new CaveReverb(sampleRate, channels);
            soundEffectEnabled = Boolean.TRUE.equals(ConfigManager.enablemusicplayersoundeffect.getValue());

            source = AL10.alGenSources();
            buffers = new int[QUEUE_SIZE];
            AL10.alGenBuffers(buffers);
            if (source == 0 || AL10.alGetError() != AL10.AL_NO_ERROR) {
                throw new JavaLayerException("Cannot create AudioDevice: OpenAL 资源创建失败");
            }
            AL10.alSourcei(source, AL10.AL_BUFFER, 0);
            AL10.alSourcef(source, AL10.AL_GAIN, dbToLinear(gainDb));
            pcm = ByteBuffer.allocateDirect(1152 * channels * 2).order(ByteOrder.nativeOrder());
            inited = true;
        } catch (JavaLayerException e) {
            throw e;
        } catch (Throwable e) {
            throw new JavaLayerException("Cannot create AudioDevice", e);
        }
    }

    /**
     * 取一个可以写入的 buffer。队列没满时用尚未入队的 buffer, 否则回收已经播放完的。
     * 返回 0 表示等待超时或线程被打断, 调用方应丢弃当前帧。
     */
    private int acquireBuffer() {
        if (AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) < QUEUE_SIZE) {
            int buffer = buffers[bufferCursor];
            bufferCursor = (bufferCursor + 1) % QUEUE_SIZE;
            return buffer;
        }
        // 队列吃满时等播放端消费。正常情况下一个 buffer 播完就会返回(约一帧时长),
        // 超时上限只用于音频设备停滞的情况, 避免把播放线程永久卡在这里。
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED) > 0) {
                return AL10.alSourceUnqueueBuffers(source);
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0;
            }
        }
        return 0;
    }

    private void stopAndClear() {
        if (source == -1) {
            return;
        }
        AL10.alSourceStop(source);
        for (int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED); queued > 0; queued--) {
            if (AL10.alSourceUnqueueBuffers(source) == 0) {
                break;
            }
        }
    }

    private static float dbToLinear(float db) {
        return db <= -80.0F ? 0.0F : (float) Math.pow(10.0, db / 20.0);
    }
}
