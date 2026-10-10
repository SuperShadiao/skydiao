package pers.XiaoShadiao.skydiao.hud;

/** A reversible, time-based morph sampled once per HUD frame. */
final class DynamicSpotAnimation {
    private static final long OPEN_NANOS = 300_000_000L;
    private static final long CLOSE_NANOS = 360_000_000L;
    private boolean requested;
    private float from;
    private float progress;
    private long startedAt;

    float sample(boolean open, long now) {
        float target = requested ? 1F : 0F;
        long duration = requested ? OPEN_NANOS : CLOSE_NANOS;
        float elapsed = Math.clamp((now - startedAt) / (float) duration, 0F, 1F);
        float remaining = 1F - elapsed;
        progress = from + (target - from) * (1F - remaining * remaining * remaining);
        if (open != requested) {
            from = progress;
            startedAt = now;
            requested = open;
        }
        return progress;
    }

    void reset() {
        requested = false;
        from = progress = 0F;
        startedAt = 0L;
    }
}
