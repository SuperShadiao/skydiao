package pers.XiaoShadiao.skydiao.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicInfo;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicPlayer;
import pers.XiaoShadiao.skydiao.utils.musicplayer.PlayerThread;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class MusicPlayerScreen extends Screen {

    private static List<MusicInfo> musics = new ArrayList<>();

    public static List<MusicInfo> getMusics() {
        return musics;
    }

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private LinearLayout footerButtonLayout;
    private MusicList musicList;

    public MusicPlayerScreen() {
        super(Component.literal("小沙雕点歌台"));
    }

    @Override
    protected void init() {
        loadMusicFromFolder();
        LinearLayout linearLayout = footerButtonLayout =  this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        linearLayout.addChild(LinearLayout.horizontal().spacing(8));
        EditBox editBox = new EditBox(ToolList.mc.font, 0, 0, 100, 20, Component.literal("音乐目录"));
        editBox.setMaxLength(10000);
        editBox.setTooltip(Tooltip.create(Component.literal("Skydiao的音乐播放器是只读状态, 在此输入Hypixel Helper Mod的音乐文件夹完整路径")));
        editBox.setHint(Component.literal("在此输入音乐目录"));
        editBox.setValue(ConfigManager.hypixelhelpermusicfolder.getValue());
        editBox.setResponder((text) -> {
            ConfigManager.hypixelhelpermusicfolder.setValue(text);
            loadMusicFromFolder();
        });
        linearLayout.addChild(editBox);
        linearLayout.addChild(Button.builder(Component.literal("返回"), b -> onClose()).size(50, 20).build());
        linearLayout.addChild(Button.builder(Component.literal(PlayerThread.isPlaying() ? "§a播放" : "§c播放"), b -> {
            if (PlayerThread.isPlaying()) {
                PlayerThread.stopPlay();
                b.setMessage(Component.literal("§c播放"));
            } else {
                PlayerThread.startPlay();
                b.setMessage(Component.literal("§a播放"));
            }
        }).size(50, 20).build());
        linearLayout.addChild(Button.builder(Component.literal(ConfigManager.musicplayermode.getCurrentDisplayString()), b -> {
            ConfigManager.musicplayermode.switchOption();
            b.setMessage(Component.literal(ConfigManager.musicplayermode.getCurrentDisplayString()));
            PlayerThread.clearQueue();
        }).size(70, 20).build());
        linearLayout.addChild(new AbstractSliderButton(0, 0, 120, 20, Component.literal("音量"), 0.5) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.literal("音量: " + ConfigManager.xsdmusicvolume.getValue()));
            }

            @Override
            protected void applyValue() {
                ConfigManager.xsdmusicvolume.setValue((int) (200 * value));
                updateMessage();
            }
        });

        linearLayout.setX(width / 2);

        musicList = new MusicList();
        this.layout.addToContents(musicList);
        this.layout.addTitleHeader(this.title, this.font);
        this.layout.visitWidgets(abstractWidget -> {
            abstractWidget.setTabOrderGroup(1);
            this.addRenderableWidget(abstractWidget);
        });
        this.repositionElements();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int i, int j, float f) {
        super.render(guiGraphics, i, j, f);
    }

    @Override
    public void repositionElements() {
        this.layout.arrangeElements();
        if(musicList != null) {
            musicList.updateSize(MusicPlayerScreen.this.width, MusicPlayerScreen.this.layout);
        }
    }

    public abstract static class AbstractEntry extends ContainerObjectSelectionList.Entry<AbstractEntry> {}

    public class MusicList extends ContainerObjectSelectionList<AbstractEntry> {

        public MusicList() {
            super(Minecraft.getInstance(), MusicPlayerScreen.this.width, MusicPlayerScreen.this.layout.getContentHeight(), MusicPlayerScreen.this.layout.getHeaderHeight(), 21);
            for (MusicInfo music : musics) {
                addEntry(new MusicEntry(music));
            }
        }

        public static class MusicEntry extends AbstractEntry {

            private final MusicInfo musicInfo;
            private final List<AbstractWidget> childs;
            private final Button playButton;

            public MusicEntry(MusicInfo musicInfo) {
                this.musicInfo = musicInfo;
                childs = List.of(playButton = Button.builder(Component.literal("P"), (b) -> PlayerThread.playMI(musicInfo)).size(20, 20).build());
            }

            @Override
            public @NotNull List<? extends NarratableEntry> narratables() {
                return childs;
            }

            @Override
            public void renderContent(GuiGraphics guiGraphics, int left, int top, boolean bl, float f) {
                RenderUtils.renderScrollingString(guiGraphics, ToolList.mc.font, Component.literal((musicInfo.equals(PlayerThread.currentMusic) ? "§a" : "") + musicInfo.name + " - " + musicInfo.singer), getContentX(), getContentX(), getContentY() - 25, getContentX() + 180, getContentY() + 44, 0xFFFFFFFF);

                playButton.setX(getContentRight() - playButton.getWidth());
                playButton.setY(getContentY());
                playButton.render(guiGraphics, left, top, f);
            }

            @Override
            public @NotNull List<? extends GuiEventListener> children() {
                return childs;
            }
        }

        @Override
        protected void removeEntry(AbstractEntry entry) {
            super.removeEntry(entry);
        }

        public List<AbstractEntry> getEntries() {
            return children();
        }
    }

    public static MusicInfo jsonToMI(JsonObject jo) {
        MusicInfo mi = new MusicInfo();
        try {
            Objects.requireNonNull(mi.name = jo.get("name").getAsString());
        } catch(Exception e) {
            mi.name = "";
        }
        try {
            Objects.requireNonNull(mi.singer = jo.get("singer").getAsString());
        } catch(Exception e) {
            mi.singer = "";
        }
        try {
            Objects.requireNonNull(mi.hashOrID = jo.get("hashorid").getAsString());
        } catch(Exception e) {
            mi.hashOrID = "";
        }
        try {
            Objects.requireNonNull(mi.imgURL = jo.get("imgurl").getAsString());
        } catch(Exception e) {
            mi.imgURL = "";
        }
        try {
            Objects.requireNonNull(mi.lyricURL = jo.get("lyricurl").getAsString());
        } catch(Exception e) {
            mi.lyricURL = "";
        }
        try {
            Objects.requireNonNull(mi.URL = jo.get("url").getAsString());
        } catch(Exception e) {
            mi.URL = "";
        }
        try {
            Objects.requireNonNull(mi.albumID = jo.get("albumid").getAsString());
        } catch(Exception e) {
            mi.albumID = "";
        }
        try {
            Objects.requireNonNull(mi.type = jo.get("type").getAsString());
        } catch(Exception e) {
            mi.type = "CUSTOM";
        }
        try {
            mi.isDisabled = jo.get("disabled").getAsBoolean();
        } catch(Exception e) {
            e.printStackTrace();
        }

        PlayerThread.fixMIFileVeriable(mi);
        return mi;
    }

    public static List<MusicInfo> loadMusicFromFolder() {
        List<MusicInfo> list = new ArrayList<>();
        try {
            JsonArray ja = JsonParser.parseString(FileUtils.readFileToString(new File(ConfigManager.hypixelhelpermusicfolder.getValue(), "musicList.json"), StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement je : ja) {
                JsonObject jo = je.getAsJsonObject();
                list.add(jsonToMI(jo));
            }
        } catch(Throwable e) {
            list.clear();
        }
        return musics = list;
    }

}
