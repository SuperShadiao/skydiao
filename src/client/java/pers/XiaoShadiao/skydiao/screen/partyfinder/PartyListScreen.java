package pers.XiaoShadiao.skydiao.screen.partyfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.irc.XSDIRCConfig;
import pers.XiaoShadiao.skydiao.ircinner.api.v1.ChatClientManager;
import pers.XiaoShadiao.skydiao.ircinner.api.v1.ChatPacket;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.partyfinder.PartyFinderData;
import pers.XiaoShadiao.skydiao.utils.partyfinder.PartyFinderManager;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class PartyListScreen extends Screen {

    private final String randomRequestKey = UUID.randomUUID().toString().replace("-", "");

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private PartyList partyList;

    private boolean hasSentCheckListPacket = false;

    public PartyListScreen() {
        super(Component.literal("寻找队伍"));
    }

    @Override
    protected void init() {
        LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        linearLayout.addChild(Button.builder(Component.literal("返回"), this::onClose).size(50, 20).build());
        linearLayout.addChild(Button.builder(Component.literal("创建自己的队伍"), (button) -> {
            minecraft.gui.setScreen(new SetupPartyScreen());
        }).build());
        linearLayout.setX(width / 2);
        partyList = new PartyList();
        this.layout.addToContents(partyList);
        this.layout.addTitleHeader(this.title, this.font);
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

        if(!hasSentCheckListPacket) {
            ChatPacket packet = new ChatPacket();
            packet.packetType = "find_party_check_list";
            packet.message = randomRequestKey;
            packet.initSender();
            ChatClientManager.trySendOrWarning(packet, XSDIRCConfig.INSTANCE);
        }
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        if(partyList != null) {
            partyList.updateSize(PartyListScreen.this.width, PartyListScreen.this.layout);
        }
    }

    public void addData(PartyFinderData data) {
        if (!data.requestKey.equals(randomRequestKey)) {
            return;
        }
        partyList.addEntry(new PartyList.PartyInfoEntry(data));
    }

    private void onClose(Button button) {
        onClose();
    }

    public class PartyList extends ContainerObjectSelectionList<PartyList.@NotNull PartyInfoEntry> {

        public PartyList() {
            super(Minecraft.getInstance(), PartyListScreen.this.width, PartyListScreen.this.layout.getContentHeight(), PartyListScreen.this.layout.getHeaderHeight(), 40);
        }

        @Override
        public int getRowWidth() {
            return 300;
        }

        public static class PartyInfoEntry extends ContainerObjectSelectionList.Entry<@NotNull PartyInfoEntry> {
            private final Button joinParty;
            private final PartyFinderData data;
            private final List<AbstractWidget> childs;

            public PartyInfoEntry(PartyFinderData data) {
                this.data = data;
                this.joinParty = Button.builder(
                        Component.literal("加入"),
                        b -> {
                            PartyFinderManager.joinPlayerNameParty(data.leaderPlayerName);
                            ToolList.mc.gui.setScreen(null);
                        }
                ).bounds(0, 0, 50, 20).build();
                this.childs = List.of(joinParty);

                joinParty.setTooltip(Tooltip.create(Component.literal("队伍成员: " + String.join(", ", data.players) + "\n\n队伍备注:\n" + data.description)));
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
                    graphics.outline(getContentX() - 1, getContentY() + ToolList.mc.font.lineHeight + 4, ToolList.mc.font.width(data.partyType.displayName) + 5, ToolList.mc.font.lineHeight + 4, data.partyType.color.getRGB());
                    widthOffset += ToolList.mc.font.width(data.partyType.displayName) + 8;

                    for (PartyFinderData.SubType subType : data.getOrderedPartySubTypes()) {
                        if(widthOffset + ToolList.mc.font.width(subType.displayName) + 8 > getContentWidth() - 80) {
                            break;
                        }
                        graphics.text(ToolList.mc.font, Component.literal(subType.displayName), getContentX() + 2 + widthOffset, getContentY() + 2 + ToolList.mc.font.lineHeight + 5, subType.color.getRGB());
                        // graphics.outline(getContentX() - 1 + widthOffset, getContentY() + ToolList.mc.font.lineHeight + 4, ToolList.mc.font.width(subType.displayName) + 5, ToolList.mc.font.lineHeight + 4, subType.color.getRGB());
                        widthOffset += ToolList.mc.font.width(subType.displayName) + 8;
                    }
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

        @Override
        protected int addEntry(PartyInfoEntry entry) {
            if(!entry.data.isValid()) {
                return children().size() - 1;
            } else if(children().stream().anyMatch(e -> e.data.partyId.equals(entry.data.partyId))) {
                return children().size() - 1;
            }
            int added = super.addEntry(entry);
            sort(Comparator.<PartyInfoEntry>comparingInt(e -> e.data.alreadyInThisParty() ? 0 : 1).thenComparingInt(e -> e.data.canJoin() ? 1 : 0));
            return added;
        }
    }

}
