package pers.XiaoShadiao.skydiao.utils.musicplayer;

/**
 * Freeverb 结构的洞穴混响: 8 个带阻尼的 comb 滤波器并联, 再串联 4 个 allpass 滤波器。
 * 输入信号在进入混响网络前会先叠加一组不等间隔的单次早期反射(无反馈), 用于拉出拖尾。
 * 早期反射之后还有一条带阻尼的反馈延迟, 产生逐次衰减的离散重复回声。
 * 左右声道使用同一组延迟长度, 右声道整体错开 {@link #STEREO_SPREAD} 个样本以拉开空间感。
 * 延迟线状态在多次 process 调用之间保留, 因此可以直接处理流式 PCM 分片而不会在分片边界断裂。
 */
final class CaveReverb {

    private static final int[] COMB_TUNING = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
    private static final int[] ALLPASS_TUNING = {556, 441, 341, 225};
    private static final int STEREO_SPREAD = 23;
    // 拖尾延迟线的容量上限, 改这个需要重启; 具体延迟时间在 process 里调整
    private static final float MAX_DELAY_SECONDS = 0.5F;

    private final Channel left;
    private final Channel right;
    private DelayLine leftDelay;
    private DelayLine rightDelay;
    private FeedbackDelay leftFeedback;
    private FeedbackDelay rightFeedback;

    CaveReverb(int sampleRate, int channels) {
        float scale = sampleRate / 44100.0F;
        this.left = new Channel(COMB_TUNING, ALLPASS_TUNING, 0, scale);
        this.right = channels == 2 ? new Channel(COMB_TUNING, ALLPASS_TUNING, STEREO_SPREAD, scale) : null;
        ensureDelayLines(sampleRate, channels);
    }

    /**
     * 延迟线按采样率懒分配: 热加载新增字段时旧实例上的引用为 null, 首次调用会在这里补建。
     */
    private void ensureDelayLines(int sampleRate, int channels) {
        int capacity = Math.max(2, Math.round(sampleRate * MAX_DELAY_SECONDS) + 1);
        if (leftDelay == null) {
            leftDelay = new DelayLine(capacity);
            rightDelay = channels == 2 ? new DelayLine(capacity) : null;
        }
        if (leftFeedback == null) {
            leftFeedback = new FeedbackDelay(capacity);
            rightFeedback = channels == 2 ? new FeedbackDelay(capacity) : null;
        }
    }

    /**
     * 就地处理一段交错排列的 16 位 PCM。len 是 short 数量, 不是帧数。
     */
    void process(short[] samples, int off, int len, int channels, int sampleRate) {
        // 调试参数放在方法内而不是 static final, 避免被编译期常量内联,
        // 这样热加载时只改这里的数值再 Reload Changed Classes 就能生效
        float wet = 0.5F;
        float dry = 0.78F;
        // 多抽头早期反射: 间隔取不等值避免梳状滤波, 增益逐级衰减; 无反馈, 不会产生重复回声
        float[] tapDelayMs = {89.0F, 149.0F, 227.0F, 313.0F};
        float[] tapGain = {0.12F, 0.09F, 0.065F, 0.045F};
        // 右声道整体再延迟一点, 拉开早期反射的立体声宽度
        float stereoSpreadMs = 7.0F;

        // 反馈延迟: 离散重复回声, g 必须小于 1; 反馈环路内的低通让每次重复逐次变暗
        float feedbackDelayMs = 150.0F;
        float feedback = 0.75F;
        float feedbackDamp = 0.6F;
        float feedbackMix = 0.15F;
        float feedbackSpreadMs = 9.0F;

        ensureDelayLines(sampleRate, channels);
        int[] tapDelays = new int[tapDelayMs.length];
        for (int i = 0; i < tapDelayMs.length; i++) {
            tapDelays[i] = Math.round(tapDelayMs[i] * sampleRate / 1000.0F);
            tapDelays[i] = Math.max(1, Math.min(leftDelay.capacity() - 1, tapDelays[i]));
        }
        int spreadSamples = Math.max(0, Math.round(stereoSpreadMs * sampleRate / 1000.0F));

        int feedbackDelaySamples = Math.round(feedbackDelayMs * sampleRate / 1000.0F);
        feedbackDelaySamples = Math.max(1, Math.min(leftFeedback.capacity() - 1, feedbackDelaySamples));
        int feedbackSpreadSamples = Math.max(0, Math.round(feedbackSpreadMs * sampleRate / 1000.0F));
        float feedbackDamp1 = feedbackDamp * 0.4F;
        float feedbackDamp2 = 1.0F - feedbackDamp1;

        int end = off + len;
        if (channels == 2 && right != null) {
            for (int i = off; i + 1 < end; i += 2) {
                float l = samples[i] * (1.0F / 32768.0F);
                float r = samples[i + 1] * (1.0F / 32768.0F);
                float dl = l + leftDelay.process(l, tapDelays, tapGain, 0);
                float dr = r + rightDelay.process(r, tapDelays, tapGain, spreadSamples);
                float fl = dl + leftFeedback.process(dl, feedbackDelaySamples, feedback, feedbackDamp1, feedbackDamp2) * feedbackMix;
                float fr = dr + rightFeedback.process(dr, feedbackDelaySamples + feedbackSpreadSamples, feedback, feedbackDamp1, feedbackDamp2) * feedbackMix;
                samples[i] = toPcm(fl * dry + left.process(fl) * wet);
                samples[i + 1] = toPcm(fr * dry + right.process(fr) * wet);
            }
        } else {
            for (int i = off; i < end; i++) {
                float x = samples[i] * (1.0F / 32768.0F);
                float dx = x + leftDelay.process(x, tapDelays, tapGain, 0);
                float fx = dx + leftFeedback.process(dx, feedbackDelaySamples, feedback, feedbackDamp1, feedbackDamp2) * feedbackMix;
                samples[i] = toPcm(fx * dry + left.process(fx) * wet);
            }
        }
    }

    private static short toPcm(float value) {
        if (value >= 1.0F) {
            return Short.MAX_VALUE;
        }
        if (value <= -1.0F) {
            return Short.MIN_VALUE;
        }
        return (short) (value * 32767.0F);
    }

    /**
     * 多抽头单次延迟线: 从同一个缓冲里读取多个延迟位置并按各自增益叠加, 不写回反馈,
     * 因此不会产生重复回声。offset 用于右声道整体错开, 拉开立体声宽度。
     */
    private static final class DelayLine {

        private final float[] buffer;
        private int cursor;

        private DelayLine(int capacity) {
            this.buffer = new float[capacity];
        }

        private int capacity() {
            return buffer.length;
        }

        private float process(float input, int[] delays, float[] gains, int offset) {
            float output = 0.0F;
            for (int i = 0; i < delays.length; i++) {
                int delay = Math.min(buffer.length - 1, delays[i] + offset);
                int read = cursor - delay;
                if (read < 0) {
                    read += buffer.length;
                }
                output += buffer[read] * gains[i];
            }
            buffer[cursor] = input;
            cursor = cursor + 1 == buffer.length ? 0 : cursor + 1;
            return output;
        }
    }

    /**
     * 反馈延迟: 输出延迟样本, 同时把输入与滤波后的延迟样本写回缓冲, 形成逐次衰减的重复回声。
     * 反馈增益必须小于 1; 反馈低通的直流增益为 1, 不会改变稳定性。
     */
    private static final class FeedbackDelay {

        private final float[] buffer;
        private int cursor;
        private float filterStore;

        private FeedbackDelay(int capacity) {
            this.buffer = new float[capacity];
        }

        private int capacity() {
            return buffer.length;
        }

        private float process(float input, int delaySamples, float feedback, float damp1, float damp2) {
            int delay = Math.min(buffer.length - 1, Math.max(1, delaySamples));
            int read = cursor - delay;
            if (read < 0) {
                read += buffer.length;
            }
            float delayed = buffer[read];
            filterStore = delayed * damp2 + filterStore * damp1;
            buffer[cursor] = input + filterStore * feedback;
            cursor = cursor + 1 == buffer.length ? 0 : cursor + 1;
            return delayed;
        }
    }

    private static final class Channel {

        private final float[][] combBuffers;
        private final int[] combCursors;
        private final float[] combStores;
        private final float[][] allpassBuffers;
        private final int[] allpassCursors;

        private Channel(int[] combTuning, int[] allpassTuning, int spread, float sampleRateScale) {
            combBuffers = new float[combTuning.length][];
            combCursors = new int[combTuning.length];
            combStores = new float[combTuning.length];
            for (int i = 0; i < combTuning.length; i++) {
                combBuffers[i] = new float[delayLength(combTuning[i] + spread, sampleRateScale)];
            }
            allpassBuffers = new float[allpassTuning.length][];
            allpassCursors = new int[allpassTuning.length];
            for (int i = 0; i < allpassTuning.length; i++) {
                allpassBuffers[i] = new float[delayLength(allpassTuning[i] + spread, sampleRateScale)];
            }
        }

        private static int delayLength(int tuning, float sampleRateScale) {
            return Math.max(1, Math.round(tuning * sampleRateScale));
        }

        private float process(float input) {
            // 同上: 这些是热加载调试参数, 不要挪出方法或改成 static final
            float inputGain = 0.025F;
            float feedback = 0.96F;
            float damp = 0.999F;
            // Freeverb 的阻尼映射: damp1 是上一拍能量的保留比例, damp2 是本拍新能量的比例
            float damp1 = damp * 0.4F;
            float damp2 = 1.0F - damp1;

            float x = input * inputGain;
            float sum = 0.0F;

            for (int i = 0; i < combBuffers.length; i++) {
                float[] buffer = combBuffers[i];
                int cursor = combCursors[i];
                float delayed = buffer[cursor];
                combStores[i] = delayed * damp2 + combStores[i] * damp1;
                buffer[cursor] = x + combStores[i] * feedback;
                combCursors[i] = cursor + 1 == buffer.length ? 0 : cursor + 1;
                sum += delayed;
            }
            // 8 路并联后取平均, 让 wet 增益与组合数量解耦
            float out = sum / combBuffers.length;

            for (int i = 0; i < allpassBuffers.length; i++) {
                float[] buffer = allpassBuffers[i];
                int cursor = allpassCursors[i];
                float delayed = buffer[cursor];
                buffer[cursor] = out + delayed * 0.5F;
                allpassCursors[i] = cursor + 1 == buffer.length ? 0 : cursor + 1;
                out = delayed - out;
            }

            return out;
        }
    }
}
