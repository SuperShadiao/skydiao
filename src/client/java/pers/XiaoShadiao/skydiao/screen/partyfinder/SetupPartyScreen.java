package pers.XiaoShadiao.skydiao.screen.partyfinder;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
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
import net.minecraft.network.chat.Component;
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

    public SetupPartyScreen() {
        super(Component.literal("设置队伍信息"));
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);

        LinearLayout contents = LinearLayout.vertical().spacing(7);
        contents.addChild(typeSelector = new TypeSelector(Component.literal("队伍类型")));
        contents.addChild(subTypeSelector = new SubTypeSelector(Component.literal("队伍子类型")));

        LinearLayout descriptionEdit = descriptionEditArea = contents.addChild(LinearLayout.vertical().spacing(5));
        descriptionEdit.defaultCellSetting().alignHorizontallyCenter();
        // descriptionEdit.spacing(5);
        descriptionEdit.addChild(new StringWidget(Component.literal("队伍备注"), font));
        descriptionEditBox = descriptionEdit.addChild(
                new MultiLineEditBox.Builder()
                        .setShowDecorations(true)
                        .setTextShadow(true)
                        .setPlaceholder(Component.literal("输入队伍备注..."))
                        .build(font, width - BORDER * 2, 20, Component.literal("队伍备注"))
        );
        descriptionEditBox.setValueListener(description -> PartyFinderManager.selfPartyData.description = description);
        descriptionEditBox.setCharacterLimit(150);
        descriptionEditBox.setValue(PartyFinderManager.selfPartyData.description);

        layout.addToContents(contents);

        openPartyButton = Button.builder(
                Component.literal("开放队伍"),
                onPress -> {
                    Runnable runnable = () -> {
                        openPartyButton.active = false;
                        closePartyButton.active = true;

                        PartyFinderManager.selfPartyData.leaderPlayerName = ToolList.mc.getUser().getName();
                        PartyFinderManager.selfPartyData.partyId = UUID.randomUUID();
                        PartyFinderManager.isPublic = true;

                        ToolList.printChatMessage(Component.literal("§a[小沙雕] 队伍已开放, 可以使用§e/skydiaofp broadcast§a来广播你的队伍!"));
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
                                Component.literal("当前队伍类型推荐最大人数为" + PartyFinderManager.selfPartyData.partyType.recommendMaxPlayers + (PartyFinderManager.selfPartyData.partyType.recommendMoreMaxPlayers ? "人或以上" : "人")), Component.literal("但你当前队伍最大人数为" + PartyFinderManager.selfPartyData.maxPlayers + "人, 是否仍要开放队伍?")
                        ));
                    }
                }
        ).size(100, 20).build();
        closePartyButton = Button.builder(
                Component.literal("关闭队伍"),
                onPress -> {
                    PartyFinderManager.isPublic = false;
                    openPartyButton.active = true;
                    closePartyButton.active = false;
                }
        ).size(100, 20).build();

        openPartyButton.active = !PartyFinderManager.isPublic;
        closePartyButton.active = PartyFinderManager.isPublic;

        LinearLayout footer = LinearLayout.horizontal().spacing(7);

        footer.addChild(partyMaxPlayer = new EditBox(font, 35, 20, Component.literal("队伍最大人数")));
        partyMaxPlayer.setTooltip(Tooltip.create(Component.literal("队伍最大人数")));
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

        footer.addChild(closePartyButton);
        footer.addChild(openPartyButton);
        footer.addChild(Button.builder(Component.literal("返回"), this::onClose).size(50, 20).build());


        layout.addToFooter(footer);

        layout.visitWidgets(this::addRenderableWidget);

        repositionElements();
    }

    private void onClose(Button button) {
        onClose();
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
            FrameLayout.centerInRectangle(descriptionEditArea, 0, descriptionEditArea.getY() - 15, width, height - descriptionEditArea.getY() - 44 + 10);
            if(descriptionEditBox != null) {
                descriptionEditBox.setWidth(width - BORDER * 2);
                descriptionEditBox.setHeight(height - descriptionEditArea.getY() - 44);
            }
        }
    }

    public abstract class Selector<T> extends AbstractWidget {

        private List<Button> buttons;

        private List<List<Button>> buttonLines;

        public Selector(Component message, T[] options) {
            super(0, 0, 0, 0, message);

            buttons = getButtons(options);
            setButtonPos();
            repositionElements();
        }

        @Override
        public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
            int nextIndex = buttons.indexOf(getFocused()) + 1;
            if(nextIndex == 0 && !this.isFocused()) return null;
            if(nextIndex >= buttons.size()) return super.nextFocusPath(navigationEvent);
            return ComponentPath.leaf(buttons.get(nextIndex));
        }

        public void updateButtons(T[] options) {
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
            boolean flag = false;
            for (Button button : buttons) {
                flag |= button.mouseClicked(event, doubleClick);
            }
            return super.mouseClicked(event, doubleClick) || flag;
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            boolean flag = false;
            for (Button button : buttons) {
                flag |= button.mouseReleased(event);
            }
            return super.mouseReleased(event) || flag;
        }

        @Override
        public void mouseMoved(double x, double y) {
            for (Button button : buttons) {
                button.mouseMoved(x, y);
            }
            super.mouseMoved(x, y);
        }

        @Override
        public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
            boolean flag = false;
            for (Button button : buttons) {
                flag |= button.mouseScrolled(x, y, scrollX, scrollY);
            }
            return super.mouseScrolled(x, y, scrollX, scrollY) || flag;
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            boolean flag = false;
            for (Button button : buttons) {
                flag |= button.keyPressed(event);
            }
            return super.keyPressed(event) || flag;
        }

        @Override
        public boolean keyReleased(KeyEvent event) {
            boolean flag = false;
            for (Button button : buttons) {
                flag |= button.keyReleased(event);
            }
            return super.keyReleased(event) || flag;
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            boolean flag = false;
            for (Button button : buttons) {
                flag |= button.charTyped(event);
            }
            return super.charTyped(event) || flag;
        }

        @Override
        public @Nullable ComponentPath getCurrentFocusPath() {
            return super.getCurrentFocusPath();
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