package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import pers.XiaoShadiao.skydiao.SkyDiaoModClient;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.URLFetchProcess;
import pers.XiaoShadiao.skydiao.utils.musicplayer.PlayerThread;
import pers.XiaoShadiao.skydiao.utils.musicplayer.StringLyric;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class TitleChanger extends AbstractListener {

    private String mcTitle = "";
    private int passedTick = 0;

    private String currentTitle = "";

    public final VersionTip versionTip = new VersionTip();
    public final CustomTitle customTitle = new CustomTitle();
    public final DownloadProcess downloadProcess = new DownloadProcess();
    public final FPSTPS fpsAndTps = new FPSTPS();
    public final MusicLyric musicLyric = new MusicLyric();
    public final Time time = new Time();

    private final List<Part> titleParts = List.of(
            versionTip,
            customTitle,
            downloadProcess,
            fpsAndTps,
            musicLyric,
            time
    );

    @Override
    public String getListenerName() {
        return "TitleChanger";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((mc, level) -> titleParts.forEach(Part::resetTick));
    }

    private void onStartTick(Minecraft mc) {
        passedTick++;
        if (!ConfigManager.cooltitle.getValue() && downloadProcess.process.isEmpty()) return;

        titleParts.forEach(Part::tick);
        String title = titleParts.stream()
                .filter(Part::shouldShow)
                .map(Part::getContent)
                .collect(Collectors.joining(" | "));
        String title1 = mcTitle + " | " + title;
        if (!title1.equals(currentTitle)) {
            currentTitle = title1;
            mc.getWindow().setTitle(title1);
        }
    }

    public String updateMCTitle(String title) {
        currentTitle = "";
        return this.mcTitle = title;
    }

    public interface Part {
        public String getContent();
        public void tick();
        public boolean shouldShow();
        public default void resetTick() {};
    }

    public class Time implements Part {

        @Override
        public String getContent() {
            return translate("features.customtitle.timepassed", ToolList.getInstance().timeToString(passedTick / 20 * 1000L));
        }

        @Override
        public void tick() {

        }

        @Override
        public boolean shouldShow() {
            return true;
        }

    }

    public static class VersionTip implements Part {

        private final String TITLE1 = "SkyDiao v" + SkyDiaoModClient.VERSION + ", by ";
        private final String TITLE2 = "XiaoShadiao";
        private final String TITLE3 = TITLE1 + " ".repeat((int) (TITLE2.length() * 1.8));
        private final String TITLE = TITLE1 + TITLE2;

        private int animationTick = 0;
        private int flag = 0;

        @Override
        public String getContent() {
            int index = animationTick % (TITLE1.length() + TITLE2.length() + 20 * 5);
            if (index < TITLE1.length()) {
                return TITLE.substring(0, index) + " " + TITLE.substring(index + 1);
            } else if ((index >= TITLE1.length() + 10 && index < TITLE1.length() + 15) || (index >= TITLE1.length() + 20 && index < TITLE1.length() + 25)) {
                return TITLE3;
            }
            return TITLE;
        }

        @Override
        public void tick() {
            flag++;
            if (flag % 3 != 0) animationTick++;
        }

        @Override
        public boolean shouldShow() {
            return true;
        }

        @Override
        public void resetTick() {
            animationTick = 0;
        }
    }

    public static class MusicLyric implements Part {

        @Override
        public String getContent() {
            return "♪ " + Optional.ofNullable(PlayerThread.current).map(instance -> instance.getLyric(0)).orElse(StringLyric.noLyricInstance).lyric() + " ♪";
        }

        @Override
        public void tick() {

        }

        @Override
        public boolean shouldShow() {
            return PlayerThread.isPlaying();
        }

    }

    public static class CustomTitle implements Part {
        @Override
        public String getContent() {
            return ConfigManager.customTitleText.getValue();
        }

        @Override
        public void tick() {

        }

        @Override
        public boolean shouldShow() {
            return !ConfigManager.customTitleText.getValue().isEmpty();
        }
    }

    public static class FPSTPS implements Part {
        @Override
        public String getContent() {
            return "FPS: " + mc.getFps() + ", TPS: " + (tpsListener.isTPSAvaliable() ? tpsListener.getCurrentFormattedTPS() : "--");
        }

        @Override
        public void tick() {

        }

        @Override
        public boolean shouldShow() {
            return true;
        }
    }

    public static class DownloadProcess implements Part {

        private final List<URLFetchProcess> process = new ArrayList<>();

        public void addProcess(URLFetchProcess process) {
            this.process.add(process);
        }

        @Override
        public String getContent() {
            return process.isEmpty() ? "" : (process.size() == 1 ? "正在获取资源 (" : "正在获取" + process.size() + "个资源 (") + process.getFirst().getProcessString() + ")";
        }

        @Override
        public void tick() {
            if(!process.isEmpty()) process.removeIf(URLFetchProcess::isDone);
        }

        @Override
        public boolean shouldShow() {
            return !process.isEmpty();
        }

    }

}
