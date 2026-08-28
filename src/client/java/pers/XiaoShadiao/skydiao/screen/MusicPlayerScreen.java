package pers.XiaoShadiao.skydiao.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicInfo;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicListManager;
import pers.XiaoShadiao.skydiao.utils.musicplayer.PlayerThread;
import pers.XiaoShadiao.skydiao.utils.musicplayer.musicgetter.impl.MusicPlatform;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;
import pers.XiaoShadiao.skydiao.utils.screen.XSDSliderButton;

import java.awt.*;
import java.io.File;
import java.util.List;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class MusicPlayerScreen extends Screen {

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private LinearLayout footerButtonLayout;
    private MusicList musicList;

    public MusicPlayerScreen() {
        super(Component.literal("小沙雕点歌台 - 歌单"));
    }

    private static double scroll;

    @Override
    public void onClose() {
        scroll = musicList.scrollAmount();
        super.onClose();
        ConfigManager.saveConfig();
    }

    @Override
    protected void init() {
        MusicListManager.loadMusicFromFolder();
        LinearLayout linearLayout = footerButtonLayout =  this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        linearLayout.addChild(LinearLayout.horizontal().spacing(8));
        EditBox editBox = new EditBox(ToolList.mc.font, 0, 0, 100, 20, Component.literal("音乐目录"));
        editBox.setMaxLength(10000);
        editBox.setTooltip(Tooltip.create(Component.literal("Skydiao的音乐播放器是只读状态, 在此输入Hypixel Helper Mod的音乐文件夹完整路径")));
        editBox.setHint(Component.literal("在此输入音乐目录"));
        editBox.setValue(ConfigManager.hypixelhelpermusicfolder.getValue());
        editBox.setResponder((text) -> {
            ConfigManager.hypixelhelpermusicfolder.setValue(text);
            MusicListManager.loadMusicFromFolder();
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
        linearLayout.addChild(Button.builder(Component.literal("下载音乐"), b -> {
            if(ConfigManager.hypixelhelpermusicfolder.getValue().isBlank() || !new File(ConfigManager.hypixelhelpermusicfolder.getValue()).exists()) {
                minecraft.setScreen(new ConfirmScreen(flag -> {
                    if(flag) {
                        File folder = new File(minecraft.gameDirectory, "XSDKGMusic");
                        if(folder.mkdirs() || folder.isDirectory()) {
                            ConfigManager.hypixelhelpermusicfolder.setValue(folder.getAbsolutePath());
                            MusicListManager.loadMusicFromFolder();
                            minecraft.setScreen(new MusicSearchScreen(this));
                        } else {
                            minecraft.setScreen(MusicPlayerScreen.this);
                        }
                    }
                }, Component.literal("指定的文件夹目录不存在"), Component.literal("你想要设置为默认文件夹并创建吗?")));
            } else {
                minecraft.setScreen(new MusicSearchScreen(this));
            }
        }).size(70, 20).build());
        linearLayout.addChild(
                new XSDSliderButton(0, 0, 120, 20, Component.literal("音量"), 0.5)
                        .valueGetter(() -> ConfigManager.xsdmusicvolume.getValue() / 200.0d)
                        .valueSetter(value -> ConfigManager.xsdmusicvolume.setValue((int) (200 * value)))
                        .stringMsgGetter(() -> "音量: " + ConfigManager.xsdmusicvolume.getValue())
        );

        linearLayout.setX(width / 2);
        musicList = new MusicList();
        musicList.setScrollAmount(scroll);

        this.layout.addToContents(musicList);
        this.layout.addTitleHeader(this.title, this.font);
        this.layout.visitWidgets(abstractWidget -> {
            abstractWidget.setTabOrderGroup(1);
            this.addRenderableWidget(abstractWidget);
        });
        this.repositionElements();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int i, int j, float f) {
        super.extractRenderState(guiGraphics, i, j, f);

        if(PlayerThread.current != null) {
            double time = PlayerThread.current.getPlayer().currentPlayTime();
            double az = PlayerThread.current.getPlayer().getMusicStatus().getLength();

            guiGraphics.fill(0, height - 3, (int) (width * time * 1000d / az), height, Color.GREEN.getRGB());
        }
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

        private final SearchEntry searchEntry = new SearchEntry();

        public MusicList() {
            super(Minecraft.getInstance(), MusicPlayerScreen.this.width, MusicPlayerScreen.this.layout.getContentHeight(), MusicPlayerScreen.this.layout.getHeaderHeight(), 21);
            updateSearchResult("", false);
        }

        @Override
        public int getRowWidth() {
            return 305;
        }

        private void updateSearchResult(String s) {
            updateSearchResult(s, true);
        }

        private void updateSearchResult(String s, boolean resetScroll) {
            clearEntries();
            for (MusicInfo music : MusicListManager.getMusics()) {
                if(s.isBlank() || (music.name + " - " + music.singer).toLowerCase().contains(s.toLowerCase())) {
                    addEntry(new MusicEntry(music));
                }
            }
            addEntryToTop(searchEntry);
            if(resetScroll) musicList.setScrollAmount(0);
        }

        public class MusicEntry extends AbstractEntry {

            private final MusicInfo musicInfo;
            private final List<AbstractWidget> childs;
            private final Button playButton;
            private final Button disableButton;
            private final Button deleteButton;
            private final Button fixButton;

            public MusicEntry(MusicInfo musicInfo) {
                this.musicInfo = musicInfo;
                playButton = Button.builder(Component.literal("P"), (b) -> PlayerThread.playMI(musicInfo)).size(20, 20).build();
                disableButton = Button.builder(Component.literal("D"), (b) -> {
                    musicInfo.isDisabled = !musicInfo.isDisabled;
                    MusicListManager.save();
                }).size(20, 20).build();
                deleteButton = Button.builder(Component.literal("-"), (b) -> {
                    MusicListManager.removeMusic(musicInfo);
                    minecraft.setScreen(new MusicPlayerScreen());
                }).size(20, 20).build();
                fixButton = Button.builder(Component.literal("F"), (b) -> {
                    musicInfo.fixMIFileVeriable();
                    MusicPlatform platform = MusicPlatform.getMusicPlatform(musicInfo);
                    if(platform != null) {
                        musicInfo.musicFile.delete();
                        musicInfo.musicLyric.delete();
                        musicInfo.setNotBroken();
                        MusicListManager.addFixQueue(musicInfo);
                    }
                }).size(20, 20).build();
                childs = List.of(disableButton, fixButton, playButton, deleteButton);
            }

            @Override
            public @NotNull List<? extends NarratableEntry> narratables() {
                return childs;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor guiGraphics, int left, int top, boolean bl, float f) {
                RenderUtils.renderScrollingString(guiGraphics, ToolList.mc.font, Component.literal(MusicListManager.getDisplayName(musicInfo, false)), getContentX(), getContentX(), getContentY() - 25, getContentX() + 210, getContentY() + 44, 0xFFFFFFFF);

                playButton.active = musicInfo.canPlay();

                if(musicInfo.getTexture() != null) {
                    guiGraphics.blit(RenderPipelines.GUI_TEXTURED,  musicInfo.getTexture(), getContentX() - 25, getContentY(), 0, 0,  20, 20, 20, 20, 0xFFFFFFFF);
                }
                fixButton.active = MusicPlatform.getMusicPlatform(musicInfo) != null;
                deleteButton.active = musicInfo.isDisabled;

                deleteButton.setX(getContentRight() - deleteButton.getWidth());
                deleteButton.setY(getContentY());
                deleteButton.extractRenderState(guiGraphics, left, top, f);
                playButton.setX(getContentRight() - deleteButton.getWidth() - playButton.getWidth());
                playButton.setY(getContentY());
                playButton.extractRenderState(guiGraphics, left, top, f);
                fixButton.setX(getContentRight() - deleteButton.getWidth() - playButton.getWidth() - fixButton.getWidth());
                fixButton.setY(getContentY());
                fixButton.extractRenderState(guiGraphics, left, top, f);
                disableButton.setX(getContentRight() - deleteButton.getWidth() - fixButton.getWidth() - playButton.getWidth() -  disableButton.getWidth());
                disableButton.setY(getContentY());
                disableButton.extractRenderState(guiGraphics, left, top, f);
            }

            @Override
            public @NotNull List<? extends GuiEventListener> children() {
                return childs;
            }
        }

        public class SearchEntry extends AbstractEntry {

            private final EditBox searchBox;

            public SearchEntry() {
                searchBox = new EditBox(ToolList.mc.font, 0, 0, 200, 20, Component.literal(translate("configcategory.搜索")));
                searchBox.setValue("");
                searchBox.setMaxLength(1000);
                searchBox.setResponder(MusicList.this::updateSearchResult);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(searchBox);
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                searchBox.setX(getContentRight() - (getContentWidth() + searchBox.getWidth()) / 2);
                searchBox.setY(getContentY());
                searchBox.extractRenderState(graphics, mouseX, mouseY, a);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(searchBox);
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

}
