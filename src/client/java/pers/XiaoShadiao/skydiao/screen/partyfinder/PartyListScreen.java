package pers.XiaoShadiao.skydiao.screen.partyfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.irc.XSDIRCConfig;
import pers.XiaoShadiao.skydiao.ircinner.api.v1.ChatPacket;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.partyfinder.PartyFinderData;
import pers.XiaoShadiao.skydiao.utils.partyfinder.PartyFinderManager;

import java.util.*;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class PartyListScreen extends Screen {

    private final String randomRequestKey = UUID.randomUUID().toString().replace("-", "");

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private PartyList partyList;

    private final String initSearch;

    private EditBox searchBox;

    public PartyListScreen() {
        this("");
    }

    public PartyListScreen(String initSearch) {
        super(Component.translatable("skydiao.gui.partyfinder.title_findparty"));
        this.initSearch = Objects.requireNonNull(initSearch);
    }

    @Override
    public void added() {
        syncPartyList();
    }

    @Override
    protected void init() {
//        LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
//        linearLayout.addChild(Button.builder(Component.literal("返回"), this::onClose).size(50, 20).build());
//        linearLayout.addChild(Button.builder(Component.literal("创建自己的队伍"), (button) -> {
//            minecraft.gui.setScreen(new SetupPartyScreen(this));
//        }).build());
//        linearLayout.setX(width / 2);
        LinearLayout linearLayout1 = this.layout.addToFooter(LinearLayout.vertical().spacing(2));
        linearLayout1.defaultCellSetting().alignHorizontallyCenter();
        linearLayout1.addChild(new StringWidget(Component.translatable("skydiao.gui.partyfinder.warning1"), this.font));
        linearLayout1.addChild(new StringWidget(Component.translatable("skydiao.gui.partyfinder.warning2"), this.font));
        LinearLayout linearLayout2 = linearLayout1.addChild(LinearLayout.horizontal().spacing(8));
        linearLayout2.addChild(Button.builder(Component.translatable("skydiao.gui.partyfinder.buttonback"), this::onClose).size(50, 20).build());
        linearLayout2.addChild(Button.builder(Component.translatable("skydiao.gui.partyfinder.buttoncreateparty"), (button) -> minecraft.gui.setScreen(new SetupPartyScreen(this))).build());
        linearLayout2.addChild(Button.builder(Component.translatable("skydiao.gui.partyfinder.buttonrefresh"), (button) -> refreshPartyList()).size(50, 20).build());

        partyList = new PartyList();
        this.layout.setHeaderHeight(50);
        this.layout.setFooterHeight(54);
        this.layout.addToContents(partyList);

        searchBox = new EditBox(minecraft.font, Component.literal("搜索队伍"));
        searchBox.setHint(Component.translatable("skydiao.gui.partyfinder.searchboxhint"));
        searchBox.setWidth(200);
        searchBox.setHeight(20);
        searchBox.setResponder(partyList::updateFilter);
        searchBox.setValue(initSearch);

        LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(this.title, this.font));
        header.addChild(searchBox);

        this.layout.visitWidgets(abstractWidget -> {
            abstractWidget.setTabOrderGroup(1);
            this.addRenderableWidget(abstractWidget);
        });

        if(false && ToolList.getInstance().isDevEnvironment() && PartyFinderManager.isPublic) {
            partyList.addEntry(new PartyList.PartyInfoEntry(PartyFinderManager.selfPartyData));
            partyList.addEntry(new PartyList.PartyInfoEntry(PartyFinderManager.selfPartyData));
            PartyFinderData data = PartyFinderManager.selfPartyData;
            data = data.clone();
            data.players.clear();
            partyList.addEntry(new PartyList.PartyInfoEntry(data));
        }

        this.repositionElements();

        syncPartyList();
    }

    private void syncPartyList() {
        if(partyList != null) {
            partyList.reset();
            ChatPacket packet = new ChatPacket();
            packet.packetType = "find_party_check_list";
            packet.message = randomRequestKey;
            packet.initSender();
            XSDIRCConfig.IRC.trySendOrWarning(packet, true);
        }
    }

    private void refreshPartyList() {
        minecraft.gui.setScreen(new PartyListScreen());
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        if(partyList != null) {
            partyList.updateSize(PartyListScreen.this.width, PartyListScreen.this.layout);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // JoinMultiplayerScreen
        if (event.shortcutKey() == 1073741886) { // F5
            refreshPartyList();
            return true;
        }
        return super.keyPressed(event);
    }

    public void addData(PartyFinderData data) {
        if (!data.requestKey.equals(randomRequestKey)) {
            return;
        }
        partyList.addParty(new PartyList.PartyInfoEntry(data));
    }

    private void onClose(Button button) {
        onClose();
    }

    public class PartyList extends ContainerObjectSelectionList<PartyList.@NotNull PartyInfoEntry> {

        private final List<PartyInfoEntry> partyList = new ArrayList<>();
        public boolean enabledSearch;
        private final Comparator<PartyInfoEntry> comparator = Comparator.<PartyInfoEntry>comparingInt(e -> e.data.alreadyInThisParty() ? 0 : 1).thenComparingInt(e -> e.data.canJoin() ? 0 : 1);

        private String searchContent = "";

        private int rowWidth = 300;

        public PartyList() {
            super(Minecraft.getInstance(), PartyListScreen.this.width, PartyListScreen.this.layout.getContentHeight(), PartyListScreen.this.layout.getHeaderHeight(), 40);
        }

        @Override
        public int getRowWidth() {
            return Math.max(300, Math.min(rowWidth, PartyListScreen.this.width - 60));
        }

        public void updateMaxRowWidth() {
            int temp = children().stream().mapToInt(PartyInfoEntry::getEntryRowWidth).max().orElse(300);
            if(temp != rowWidth) {
                rowWidth = temp;
                repositionElements();
            }
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
            updateMaxRowWidth();
        }

        public static class PartyInfoEntry extends ContainerObjectSelectionList.Entry<@NotNull PartyInfoEntry> {
            private final Button joinParty;
            private final PartyFinderData data;
            private final List<AbstractWidget> childs;

            private int rowWidth = 300;

            public int getEntryRowWidth() {
                return rowWidth;
            }

            public PartyInfoEntry(PartyFinderData data) {
                this.data = data;
                this.joinParty = Button.builder(
                        Component.translatable("skydiao.gui.partyfinder.partyjoin"),
                        b -> {
                            PartyFinderManager.joinPlayerNameParty(data.leaderPlayerName);
                            ToolList.mc.gui.setScreen(null);
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] ").append(Component.translatable("skydiao.gui.partyfinder.tip1")));
                        }
                ).bounds(0, 0, 50, 20).build();
                this.childs = List.of(joinParty);

                joinParty.setTooltip(Tooltip.create(Component.literal(translate("skydiao.gui.partyfinder.partymembers") + ": " + String.join(", ", data.players) + "\n\n" + translate("skydiao.gui.partyfinder.partydescription") + ":\n" + data.description)));
            }

            @Override
            public @NotNull List<? extends NarratableEntry> narratables() {
                return childs;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                if(data.isValid()) {
                    graphics.text(ToolList.mc.font, Component.literal("§e" + data.leaderPlayerName + "§a的队伍 (" + Math.max(data.players.size(), 1) + "/" + data.maxPlayers + ")"), getContentX(), getContentY() + 2, -1);

                    int widthOffset = 0;
                    graphics.text(ToolList.mc.font, Component.literal(data.partyType.displayName), getContentX() + 2 + widthOffset, getContentY() + 2 + ToolList.mc.font.lineHeight + 5, data.partyType.color.getRGB());
                    int typeTextWidth = ToolList.mc.font.width(data.partyType.displayName);
                    graphics.outline(getContentX() - 1, getContentY() + ToolList.mc.font.lineHeight + 4, typeTextWidth + 5, ToolList.mc.font.lineHeight + 4, data.partyType.color.getRGB());

                    if(
                            mouseX > getContentX() + 2 + widthOffset &&
                                    mouseX < getContentX() + 2 + widthOffset + typeTextWidth &&
                                    mouseY > getContentY() + 2 + ToolList.mc.font.lineHeight + 5 &&
                                    mouseY < getContentY() + 2 + ToolList.mc.font.lineHeight * 2 + 5
                    ) {
                        graphics.setTooltipForNextFrame(Component.literal(data.partyType.getI18nDescription()), mouseX, mouseY);
                    }
                    widthOffset += typeTextWidth + 8;

                    for (PartyFinderData.SubType subType : data.getOrderedPartySubTypes()) {
                        int subTypeTextWidth = ToolList.mc.font.width(subType.displayName);
                        if(widthOffset + subTypeTextWidth + 8 > getContentWidth() - 80) {
                            widthOffset += subTypeTextWidth + 8;
                            continue;
                        }
                        graphics.text(ToolList.mc.font, Component.literal(subType.displayName), getContentX() + 2 + widthOffset, getContentY() + 2 + ToolList.mc.font.lineHeight + 5, subType.color.getRGB());
                        // graphics.outline(getContentX() - 1 + widthOffset, getContentY() + ToolList.mc.font.lineHeight + 4, ToolList.mc.font.width(subType.displayName) + 5, ToolList.mc.font.lineHeight + 4, subType.color.getRGB());
                        if(
                                mouseX > getContentX() + 2 + widthOffset &&
                                        mouseX < getContentX() + 2 + widthOffset + subTypeTextWidth &&
                                        mouseY > getContentY() + 2 + ToolList.mc.font.lineHeight + 5 &&
                                        mouseY < getContentY() + 2 + ToolList.mc.font.lineHeight * 2 + 5
                        ) {
                            List<FormattedCharSequence> tooltip = ToolList.mc.font.split(Component.literal(subType.getI18nDescription()), 170);
                            graphics.setTooltipForNextFrame(tooltip, mouseX, mouseY);
                        }

                        widthOffset += subTypeTextWidth + 8;
                    }
                    rowWidth = widthOffset + 100;
                    joinParty.setX(getContentRight() - 60);
                    joinParty.setY(getContentY() + 2 + ToolList.mc.font.lineHeight - 10);
                    joinParty.setWidth(50);
                    joinParty.setHeight(20);
                    joinParty.active = data.canJoin();
                    joinParty.extractRenderState(graphics, mouseX, mouseY, a);
                }
            }

            @Override
            public @NotNull List<? extends GuiEventListener> children() {
                return childs;
            }

        }

        @Override
        protected void removeEntry(PartyInfoEntry entry) {
            super.removeEntry(entry);
        }

        public void addParty(PartyInfoEntry entry) {
            if(!entry.data.isValid()) return;
            if(partyList.stream().anyMatch(e -> e.data.partyId.equals(entry.data.partyId))) return;
            partyList.add(entry);
            partyList.sort(comparator);
            if(!enabledSearch) {
                addEntry(entry);
            } else if(matchEntry(entry, searchContent)) {
                addEntry(entry);
            }
        }

        @Override
        protected int addEntry(PartyInfoEntry entry) {
            if(!entry.data.isValid()) {
                return children().size() - 1;
            } else if(children().stream().anyMatch(e -> e.data.partyId.equals(entry.data.partyId))) {
                return children().size() - 1;
            }
            int added = super.addEntry(entry);
            sort(comparator);
            return added;
        }

        public void updateFilter(String search) {
            searchContent = search;
            enabledSearch = !search.isBlank();

            clearEntries();

            for (PartyInfoEntry entry : partyList) {
                if(matchEntry(entry, search)) {
                    addEntry(entry);
                }
            }
        }

        private boolean matchEntry(PartyInfoEntry entry, String searchContent) {
            if(!enabledSearch) return true;
            String lowerCase = searchContent.toLowerCase();

            return entry.data.leaderPlayerName.toLowerCase().contains(lowerCase) ||
                    entry.data.description.toLowerCase().contains(lowerCase) ||
                    entry.data.partyType.displayName.toLowerCase().contains(lowerCase) ||
                    entry.data.partySubTypes.stream().anyMatch(type -> type.displayName.toLowerCase().contains(lowerCase));
        }


        public void reset() {
            clearEntries();
            partyList.clear();
        }
    }

}
