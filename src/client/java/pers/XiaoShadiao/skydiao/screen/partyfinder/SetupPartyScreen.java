package pers.XiaoShadiao.skydiao.screen.partyfinder;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.partyfinder.PartyFinderData;
import pers.XiaoShadiao.skydiao.utils.partyfinder.PartyFinderManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class SetupPartyScreen extends Screen {

    private final int BORDER = 20;

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    private Button openPartyButton;
    private Button closePartyButton;

    private TypeSelector typeSelector;
    private SubTypeSelector subTypeSelector;

    private LinearLayout descriptionEditArea;
    private MultiLineEditBox descriptionEditBox;

    private EditBox partyMaxPlayer;

    private final Screen parentScreen;

    public SetupPartyScreen() {
        this(null);
    }

    public SetupPartyScreen(Screen parentScreen) {
        super(Component.translatable("skydiao.gui.partyfinder.title_createparty"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        layout.setFooterHeight(55);
        layout.addTitleHeader(title, font);

        LinearLayout contents = LinearLayout.vertical().spacing(7);
        contents.addChild(typeSelector = new TypeSelector(Component.translatable("skydiao.gui.partyfinder.partytype")));
        contents.addChild(subTypeSelector = new SubTypeSelector(Component.translatable("skydiao.gui.partyfinder.partysubtype")));

        LinearLayout descriptionEdit = descriptionEditArea = contents.addChild(LinearLayout.vertical().spacing(5));
        descriptionEdit.defaultCellSetting().alignHorizontallyCenter();
        // descriptionEdit.spacing(5);
        descriptionEdit.addChild(new StringWidget(Component.translatable("skydiao.gui.partyfinder.partydescription"), font));
        descriptionEditBox = descriptionEdit.addChild(
                new MultiLineEditBox.Builder()
                        .setShowDecorations(true)
                        .setTextShadow(true)
                        .setPlaceholder(Component.translatable("skydiao.gui.partyfinder.partydescriptionhint"))
                        .build(font, width - BORDER * 2, 20, Component.literal("队伍备注"))
        );
        descriptionEditBox.setValueListener(description -> PartyFinderManager.selfPartyData.description = description);
        descriptionEditBox.setCharacterLimit(150);
        descriptionEditBox.setValue(PartyFinderManager.selfPartyData.description);

        layout.addToContents(contents);

        openPartyButton = Button.builder(
                Component.translatable("skydiao.gui.partyfinder.buttonopenparty"),
                onPress -> {
                    Runnable runnable = () -> {
                        openPartyButton.active = false;
                        closePartyButton.active = true;

                        PartyFinderManager.selfPartyData.leaderPlayerName = ToolList.mc.getUser().getName();
                        PartyFinderManager.selfPartyData.partyId = UUID.randomUUID();
                        PartyFinderManager.isPublic = true;

                        ToolList.printChatMessage(Component.literal("§a[小沙雕] 队伍已开放, 可以使用§e/skydiaofp broadcast§a来广播你的队伍!")
                                .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal("点击填充指令到聊天栏")))
                                        .withClickEvent(new ClickEvent.SuggestCommand("/skydiaofp broadcast"))));
                    };
                    if(PartyFinderManager.selfPartyData.partyType.isMaxPlayerRecommend(PartyFinderManager.selfPartyData.maxPlayers)) {
                        runnable.run();
                    } else {
                        minecraft.setScreenAndShow(new ConfirmScreen(
                                (accept) -> {
                                    if(accept) {
                                        runnable.run();
                                    }
                                    minecraft.setScreenAndShow(this);
                                },
                                Component.translatable(
                                        "skydiao.gui.partyfinder.maxplayerhint1_1",
                                        PartyFinderManager.selfPartyData.partyType.recommendMaxPlayers
                                ).append(PartyFinderManager.selfPartyData.partyType.recommendMoreMaxPlayers ? Component.literal(" ").append(Component.translatable("skydiao.gui.partyfinder.maxplayerhint1_2")) : Component.empty()),
                                Component.translatable("skydiao.gui.partyfinder.maxplayerhint2", PartyFinderManager.selfPartyData.maxPlayers)
                        ));
                    }
                }
        ).size(100, 20).build();
        closePartyButton = Button.builder(
                Component.translatable("skydiao.gui.partyfinder.buttoncloseparty"),
                onPress -> {
                    PartyFinderManager.isPublic = false;
                    openPartyButton.active = true;
                    closePartyButton.active = false;
                }
        ).size(100, 20).build();

        openPartyButton.active = !PartyFinderManager.isPublic;
        closePartyButton.active = PartyFinderManager.isPublic;

        LinearLayout linearLayout1 = this.layout.addToFooter(LinearLayout.vertical().spacing(2));
        linearLayout1.defaultCellSetting().alignHorizontallyCenter();
        linearLayout1.addChild(new StringWidget(Component.translatable("skydiao.gui.partyfinder.warning1"), this.font));
        linearLayout1.addChild(new StringWidget(Component.translatable("skydiao.gui.partyfinder.warning2"), this.font));
        LinearLayout linearLayout2 = linearLayout1.addChild(LinearLayout.horizontal().spacing(7));
        linearLayout2.addChild(partyMaxPlayer = new EditBox(font, 35, 20, Component.literal("队伍最大人数")));
        partyMaxPlayer.setTooltip(Tooltip.create(Component.translatable("skydiao.gui.partyfinder.maxplayer")));
        partyMaxPlayer.setResponder(stringx -> {
            try {
                int i = Integer.parseInt(stringx);
                if(i < 2) {
                    partyMaxPlayer.setTextColor(-65536);
                } else {
                    PartyFinderManager.selfPartyData.maxPlayers = i;
                    partyMaxPlayer.setTextColor(-2039584);
                }
            } catch (NumberFormatException e) {
                partyMaxPlayer.setTextColor(-65536);
            }
        });
        partyMaxPlayer.setValue(String.valueOf(PartyFinderManager.selfPartyData.maxPlayers));

        linearLayout2.addChild(closePartyButton);
        linearLayout2.addChild(openPartyButton);
        linearLayout2.addChild(Button.builder(Component.translatable("skydiao.gui.partyfinder.buttonback"), this::onClose).size(50, 20).build());

        layout.visitWidgets(this::addRenderableWidget);

        repositionElements();
    }

    private void onClose(Button button) {
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parentScreen);
    }

    @Override
    protected void repositionElements() {
        if(descriptionEditBox != null) {
            descriptionEditBox.setHeight(0);
            descriptionEditBox.setWidth(width - BORDER * 2);
        }
        for (GuiEventListener child : children()) {
            if(child instanceof Selector<?> selector) {
                selector.setButtonPos();
            }
        }
        layout.arrangeElements();
        if(descriptionEditArea != null) {
            FrameLayout.centerInRectangle(descriptionEditArea, 0, descriptionEditArea.getY() - 15, width, height - descriptionEditArea.getY() - layout.getFooterHeight());
            if(descriptionEditBox != null) {
                descriptionEditBox.setWidth(width - BORDER * 2);
                descriptionEditBox.setHeight(height - descriptionEditArea.getY() - layout.getFooterHeight() - 11);
            }
        }
    }

    public abstract class Selector<T> extends AbstractWidget implements ContainerEventHandler {

        private List<Button> buttons;

        private List<List<Button>> buttonLines;

        private @Nullable GuiEventListener focused;

        private boolean dragging;

        public Selector(Component message, T[] options) {
            super(0, 0, 0, 0, message);

            buttons = getButtons(options);
            setButtonPos();
            repositionElements();
        }

        @Override
        public List<Button> children() {
            return buttons;
        }

        @Override
        public @Nullable GuiEventListener getFocused() {
            return focused;
        }

        @Override
        public void setFocused(@Nullable GuiEventListener focused) {
            if (this.focused != focused) {
                if (this.focused != null) {
                    this.focused.setFocused(false);
                }
                if (focused != null) {
                    focused.setFocused(true);
                }
                this.focused = focused;
            }
        }

        @Override
        public boolean isFocused() {
            return ContainerEventHandler.super.isFocused();
        }

        @Override
        public void setFocused(boolean focused) {
            ContainerEventHandler.super.setFocused(focused);
        }

        @Override
        public boolean isDragging() {
            return dragging;
        }

        @Override
        public void setDragging(boolean dragging) {
            this.dragging = dragging;
        }

        @Override
        public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
            return ContainerEventHandler.super.nextFocusPath(navigationEvent);
        }

        public void updateButtons(T[] options) {
            setFocused(null);
            buttons = getButtons(options);
            setButtonPos();
            repositionElements();
        }

        private void setButtonPos() {
            int buttonTotalWidth = BORDER;
            int buttonCurrentYOffset = 12;
            int buttonCurrentLineMaxHeight = 0;
            List<Button> currentLine = new ArrayList<>();
            List<List<Button>> lines = new ArrayList<>();
            for (Button button : buttons) {
                if (buttonTotalWidth + button.getWidth() + 5 > getWidth() - BORDER) {
                    lines.add(currentLine);
                    currentLine = new ArrayList<>();
                    buttonTotalWidth = BORDER;
                    buttonCurrentYOffset += buttonCurrentLineMaxHeight + 5;
                    buttonCurrentLineMaxHeight = 0;
                }
                button.setX(buttonTotalWidth);
                button.setY(getY() + buttonCurrentYOffset);
                buttonTotalWidth += button.getWidth() + 5;
                buttonCurrentLineMaxHeight = Math.max(buttonCurrentLineMaxHeight, button.getHeight());
                currentLine.add(button);
            }
            if (!currentLine.isEmpty()) lines.add(currentLine);
            buttonLines = lines;
        }

        protected abstract List<Button> getButtons(T[] options);

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            setButtonPos();
            graphics.text(font, getMessage(), BORDER, getY(), 0xFFFFFFFF);
            for (Button button : buttons) {
                button.extractRenderState(graphics, mouseX, mouseY, a);
            }
        }

        @Override
        public int getWidth() {
            return SetupPartyScreen.this.width;
        }

        @Override
        public int getHeight() {
            if(buttonLines == null) return font.lineHeight + 2;
            return font.lineHeight + 2 + buttonLines.size() * 5 + buttonLines.stream().mapToInt(l -> l.stream().mapToInt(Button::getHeight).max().orElse(0)).sum();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return ContainerEventHandler.super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            return ContainerEventHandler.super.mouseReleased(event);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
            return ContainerEventHandler.super.mouseDragged(event, dx, dy);
        }

        @Override
        public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
            return ContainerEventHandler.super.mouseScrolled(x, y, scrollX, scrollY);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            return ContainerEventHandler.super.keyPressed(event);
        }

        @Override
        public boolean keyReleased(KeyEvent event) {
            return ContainerEventHandler.super.keyReleased(event);
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            return ContainerEventHandler.super.charTyped(event);
        }

        @Override
        public @Nullable ComponentPath getCurrentFocusPath() {
            return ContainerEventHandler.super.getCurrentFocusPath();
        }
    }

    public class TypeSelector extends Selector<PartyFinderData.Type> {

        public TypeSelector(Component message) {
            super(message, PartyFinderData.Type.values());
        }

        @Override
        protected List<Button> getButtons(PartyFinderData.Type[] types) {
            ArrayList<Button> buttons = new ArrayList<>();
            for (PartyFinderData.Type type : types) {
                if(type == PartyFinderData.Type.UNKNOWN) continue;
//                buttons.add(Button.builder(
//                        Component.literal((PartyFinderManager.selfPartyData.partyType == type ? "§a" : "") + type.displayName),
//                        onPress -> {
//                            if(PartyFinderManager.selfPartyData.partyType != type) {
//                                PartyFinderManager.selfPartyData.partyType = type;
//                                PartyFinderManager.selfPartyData.partySubTypes.clear();
//                            }
//                            subTypeSelector.updateButtons(type.subTypes.toArray(new PartyFinderData.SubType[0]));
//                            buttons.forEach(button -> button.setMessage(Component.literal((button == onPress ? "§a" : "") + type.displayName)));
//                        }
//                ).size(font.width(type.displayName) + 20, 20).build());
                buttons.add(new Button.Plain(0, 0, font.width(type.displayName) + 20, 20, Component.literal(type.displayName), onPress -> {
                    if(PartyFinderManager.selfPartyData.partyType != type) {
                        PartyFinderManager.selfPartyData.partyType = type;
                        PartyFinderManager.selfPartyData.partySubTypes.clear();
                    }
                    subTypeSelector.updateButtons(type.subTypes.toArray(new PartyFinderData.SubType[0]));
                }, Supplier::get) {
                    {
                        setTooltip(Tooltip.create(Component.literal(type.getI18nDescription())));
                    }

                    @Override
                    public @NotNull Component getMessage() {
                        return super.getMessage().copy().withColor(PartyFinderManager.selfPartyData.partyType == type ? type.color.getRGB() : 0xFFBBBBBB);
                    }
                });
            }
            return buttons;
        }

    }

    public class SubTypeSelector extends Selector<PartyFinderData.SubType> {

        public SubTypeSelector(Component message) {
            super(message, PartyFinderManager.selfPartyData.partyType == null ? new PartyFinderData.SubType[0] : PartyFinderManager.selfPartyData.partyType.subTypes.toArray(new PartyFinderData.SubType[0]));
        }

        @Override
        protected List<Button> getButtons(PartyFinderData.SubType[] options) {
            ArrayList<Button> buttons = new ArrayList<>();
            for (PartyFinderData.SubType type : options) {
//                buttons.add(Button.builder(
//                        Component.literal(type.displayName).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(PartyFinderManager.selfPartyData.partySubTypes.contains(type) ? type.color.getRGB() : 0xFFFFFFFF))),
//                        onPress -> {
//                            if (PartyFinderManager.selfPartyData.partySubTypes.contains(type)) {
//                                PartyFinderManager.selfPartyData.partySubTypes.remove(type);
//                            } else {
//                                PartyFinderManager.selfPartyData.partySubTypes.add(type);
//                            }
//                            buttons.forEach(button -> button.setMessage(Component.literal(type.displayName).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(PartyFinderManager.selfPartyData.partySubTypes.contains(type) ? type.color.getRGB() : 0xFFFFFFFF)))));
//                        }
//                ).size(font.width(type.displayName) + 20, 20).build());

                buttons.add(new Button.Plain(0, 0, font.width(type.displayName) + 20, 20, Component.literal(type.displayName), onPress -> {
                    if (PartyFinderManager.selfPartyData.partySubTypes.contains(type)) {
                        PartyFinderManager.selfPartyData.partySubTypes.remove(type);
                    } else {
                        PartyFinderManager.selfPartyData.partySubTypes.add(type);
                    }
                }, Supplier::get) {
                    {
                        setTooltip(Tooltip.create(Component.literal(type.getI18nDescription())));
                    }

                    @Override
                    public @NotNull Component getMessage() {
                        return super.getMessage().copy().withColor(PartyFinderManager.selfPartyData.partySubTypes.contains(type) ? type.color.getRGB() : 0xFFBBBBBB);
                    }
                });
            }
            return buttons;
        }

    }
}