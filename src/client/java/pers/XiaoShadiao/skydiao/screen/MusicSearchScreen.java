package pers.XiaoShadiao.skydiao.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicInfo;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicListManager;
import pers.XiaoShadiao.skydiao.utils.musicplayer.musicgetter.impl.MusicPlatform;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class MusicSearchScreen extends Screen {

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final MusicPlayerScreen parent;
    private LinearLayout footerButtonLayout;
    private MusicList musicList;

    private static List<MusicInfo> lastSearchResult = List.of();
    private static String lastSearch = "";

    private Throwable err;

    public MusicSearchScreen(MusicPlayerScreen musicPlayerScreen) {
        super(Component.literal("小沙雕点歌台 - 搜索"));
        this.parent = musicPlayerScreen;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(new MusicPlayerScreen());
    }

    @Override
    protected void init() {
        LinearLayout linearLayout = footerButtonLayout =  this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        linearLayout.addChild(LinearLayout.horizontal().spacing(8));
        linearLayout.addChild(Button.builder(Component.literal("返回"), b -> onClose()).size(50, 20).build());
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        if(err != null) {
            graphics.centeredText(minecraft.font, "发生错误, 请重试, 若问题依旧存在, 请报告给小沙雕", width / 2, height / 2 - minecraft.font.lineHeight - 3, 0xFFFF0000);
            graphics.centeredText(minecraft.font, err.toString(), width / 2, height / 2 + 3, 0xFFFF0000);
        }
    }

    @Override
    public void repositionElements() {
        this.layout.arrangeElements();
        if(musicList != null) {
            musicList.updateSize(MusicSearchScreen.this.width, MusicSearchScreen.this.layout);
        }
    }

    public abstract static class AbstractEntry extends ContainerObjectSelectionList.Entry<AbstractEntry> {}

    public class MusicList extends ContainerObjectSelectionList<AbstractEntry> {

        private final SearchEntry searchEntry = new SearchEntry();

        public MusicList() {
            super(Minecraft.getInstance(), MusicSearchScreen.this.width, MusicSearchScreen.this.layout.getContentHeight(), MusicSearchScreen.this.layout.getHeaderHeight(), 21);
            updateSearchResult(lastSearchResult);
        }

        @Override
        public int getRowWidth() {
            return 305;
        }

        private void updateSearchResult(List<MusicInfo> musics) {
            clearEntries();
            for (MusicInfo music : musics) {
                addEntry(new MusicEntry(music));
            }
            addEntryToTop(searchEntry);
            lastSearchResult = musics;
            scrollToEntry(searchEntry);
        }

        public static class MusicEntry extends AbstractEntry {

            private final MusicInfo musicInfo;
            private final List<AbstractWidget> childs;
            private final Button playButton;

            public MusicEntry(MusicInfo musicInfo) {
                this.musicInfo = musicInfo;
                childs = List.of(playButton = Button.builder(Component.literal("+"), (b) -> {
                    MusicListManager.add(musicInfo.clone());
                }).size(20, 20).build());
            }

            @Override
            public @NotNull List<? extends NarratableEntry> narratables() {
                return childs;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor guiGraphics, int left, int top, boolean bl, float f) {
                RenderUtils.renderScrollingString(guiGraphics, ToolList.mc.font, Component.literal(MusicListManager.getDisplayName(musicInfo, true)), getContentX(), getContentX(), getContentY() - 25, getContentX() + 270, getContentY() + 44, 0xFFFFFFFF);

                if(musicInfo.getTexture() != null) {
                    guiGraphics.blit(RenderPipelines.GUI_TEXTURED,  musicInfo.getTexture(), getContentX() - 25, getContentY(), 0, 0,  20, 20, 20, 20, 0xFFFFFFFF);
                }

                playButton.setX(getContentRight() - playButton.getWidth());
                playButton.setY(getContentY());
                playButton.extractRenderState(guiGraphics, left, top, f);
            }

            @Override
            public @NotNull List<? extends GuiEventListener> children() {
                return childs;
            }
        }

        public class SearchEntry extends AbstractEntry {

            private MusicPlatform currentPlatform = Objects.requireNonNull(MusicPlatform.getMusicPlatform("KG"));

            private final EditBox searchBox;
            private final Button searchPlatform;
            private final Button searchButton;

            public SearchEntry() {
                searchBox = new EditBox(ToolList.mc.font, 0, 0, 120, 20, Component.literal(translate("configcategory.搜索")));
                searchBox.setValue(lastSearch);
                searchBox.setMaxLength(1000);
                searchBox.setResponder(s -> lastSearch = s);

                Supplier<Tooltip> tooltipGetter = () -> Tooltip.create(Component.literal("§e点击切换搜索与下载源\n" + (!currentPlatform.canDownloadVIPMusic() ? "§c此平台不支持下载VIP音乐" : "§a此平台可下载VIP音乐")));
                searchPlatform = Button.builder(Component.literal(currentPlatform.getDisplayName()), b -> {
                    currentPlatform = MusicPlatform.getMusicPlatforms().get((MusicPlatform.getMusicPlatforms().indexOf(currentPlatform) + 1) % MusicPlatform.getMusicPlatforms().size());
                    b.setMessage(Component.literal(currentPlatform.getDisplayName()));
                    b.setTooltip(tooltipGetter.get());
                }).size(40, 20).build();
                searchPlatform.setTooltip(tooltipGetter.get());

                searchButton = Button.builder(Component.literal("搜索"), b -> {
                    searchBox.active = false;
                    searchPlatform.active = false;
                    b.active = false;
                    err = null;
                    updateSearchResult(List.of());
                    musicList.setScrollAmount(0);
                    ToolList.addThreadedTask(() -> {
                        int tries = 0;
                        Throwable temp;
                        while(true) {
                            try {
                                List<MusicInfo> musics = currentPlatform.searchMusic(searchBox.getValue());
                                minecraft.execute(() -> {
                                    updateSearchResult(musics);
                                });
                                break;
                            } catch (Exception e) {
                                temp = e;
                            }
                            tries++;
                            if(tries >= 3) {
                                err = temp;
                                break;
                            }
                        }
                        searchBox.active = true;
                        searchPlatform.active = true;
                        b.active = true;
                        musicList.setScrollAmount(0);
                    }, null);
                }).size(40, 20).build();
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(searchBox, searchPlatform, searchButton);
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                searchBox.setX(getContentRight() - (getContentWidth() + 80 + searchBox.getWidth()) / 2);
                searchBox.setY(getContentY());
                searchBox.extractRenderState(graphics, mouseX, mouseY, a);

                searchPlatform.setX(getContentRight() - ((getContentWidth() + 80 + searchBox.getWidth()) / 2) + searchBox.getWidth());
                searchPlatform.setY(getContentY());
                searchPlatform.extractRenderState(graphics, mouseX, mouseY, a);
                searchButton.setX(getContentRight() - ((getContentWidth() + 80 + searchBox.getWidth()) / 2) + searchBox.getWidth() + searchPlatform.getWidth());
                searchButton.setY(getContentY());
                searchButton.extractRenderState(graphics, mouseX, mouseY, a);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(searchBox, searchPlatform, searchButton);
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
