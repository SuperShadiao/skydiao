package pers.XiaoShadiao.skydiao.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.keybinds.KeyBindsManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

public class FastCommandMenuScreen extends Screen {

    private static final File configFile = ConfigManager.getCustomConfigFileName("fastCommand.json");

    public record TitleAndCommand(String title, String command) { }

    private static final List<TitleAndCommand> commands = new ArrayList<>();
    private static final List<String> pageTitle = new ArrayList<>();

    private static int savedPage = 0;

    private final boolean editMode;
    private int lastPage;
    private int page;

    private boolean canScrollLeft;
    private boolean canScrollRight;

    // edit
    private CommandButton editingButton;
    private EditBox editTitle;
    private EditBox editButtonTitle;
    private EditBox editButtonCommand;

    private Button editAddPageButton;
    private Button editRemovePageButton;
    private Button editScrollLeftPageButton;
    private Button editScrollRightPageButton;

    private Button savePageStateButton;
    private Button saveDefaultPageButton;

    static {
        load();
    }

    public static void load() {
        try {
            String s = FileUtils.readFileToString(configFile, StandardCharsets.UTF_8);
            Iterator<JsonElement> iterator = JsonParser.parseString(s).getAsJsonArray().iterator();
            List<TitleAndCommand> tempCommands = new ArrayList<>();
            List<String> tempPageTitle = new ArrayList<>();
            int pageCountDown = 0;
            while (iterator.hasNext()) {
                if (pageCountDown == 0) {
                    tempPageTitle.add(iterator.next().getAsString());
                    pageCountDown = 8;
                    continue;
                }
                String title = iterator.next().getAsString();
                if (!iterator.hasNext()) break;
                String command = iterator.next().getAsString();
                tempCommands.add(new TitleAndCommand(title, command));
                pageCountDown--;
            }
            commands.clear();
            commands.addAll(tempCommands);
            pageTitle.clear();
            pageTitle.addAll(tempPageTitle);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void save() {
        try {
            JsonArray jsonArray = new JsonArray();
            int counter = 0;
            for (TitleAndCommand command : commands) {
                if (counter % 8 == 0) {
                    jsonArray.add(pageTitle.get(counter / 8));
                }
                jsonArray.add(command.title());
                jsonArray.add(command.command());

                counter++;
            }
            FileUtils.writeStringToFile(configFile, jsonArray.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public FastCommandMenuScreen(boolean editMode) {
        super(Component.literal("快捷指令"));
        this.editMode = editMode;
        if(ConfigManager.fastCommandMenuSavePageState.getValue()) {
            this.page = savedPage;
        } else {
            this.page = ConfigManager.fastCommandMenuDefaultPage.getValue();
            if(page >= pageTitle.size() || page < 0) {
                page = 0;
            }
        }
    }

    private void createCommandButton() {
        List<? extends GuiEventListener> listeners = new ArrayList<>(children());
        for (GuiEventListener listener : listeners) {
            if(listener instanceof CommandButton) {
                removeWidget(listener);
            }
        }

        for (int i = 0; i < pageTitle.size(); i++) {
            int j = 0;
            if (Math.min((i + 1) * 8, commands.size()) != (i + 1) * 8) {
                while (commands.size() < (i + 1) * 8) {
                    commands.add(new TitleAndCommand("", ""));
                }
            }

            for (TitleAndCommand command : commands.subList(i * 8, Math.min((i + 1) * 8, commands.size()))) {
                CommandButton button = new CommandButton();
                button.setInstance(command);
                button.setPage(i);
                button.setSlot(j);
                addRenderableWidget(button);
                j++;
            }
        }
    }

    @Override
    protected void init() {
        if (editMode) save();

        createCommandButton();

        if (editMode) {
            editTitle = new EditBox(font, 100, 20, Component.literal("标题"));
            editTitle.setHint(Component.literal("页面标题"));
            editTitle.setResponder(string -> {
                pageTitle.set(page, string);
            });
            if (!pageTitle.isEmpty()) {
                editTitle.setValue(pageTitle.get(page));
            } else {
                editTitle.active = false;
            }
            addRenderableWidget(editTitle);
            editButtonTitle = new EditBox(font, 100, 20, Component.literal("按钮标题"));
            editButtonTitle.setHint(Component.literal("按钮标题"));
            editButtonTitle.setResponder(string -> {
                if(editingButton != null) editingButton.setTitle(string);
            });
            editButtonCommand = new EditBox(font, 100, 20, Component.literal("按钮指令"));
            editButtonCommand.setHint(Component.literal("按钮指令"));
            editButtonCommand.setResponder(string -> {
                if(editingButton != null) editingButton.setCommand(string);
            });
            editButtonTitle.active = editButtonCommand.active = false;
            addRenderableWidget(editButtonTitle);
            addRenderableWidget(editButtonCommand);

            editAddPageButton = Button.builder(Component.literal("+"), _ -> {
                pageTitle.add("");
                page = pageTitle.size() - 1;

                updateScrollButtonActive();
                createCommandButton();
            }).size(20, 20).build();
            editRemovePageButton = Button.builder(Component.literal("-"), _ -> {
                pageTitle.remove(page);
                commands.subList(page * 8, (page + 1) * 8).clear();
                if(page > pageTitle.size() - 1) {
                    page = pageTitle.size() - 1;
                }
                updateScrollButtonActive();
                createCommandButton();
            }).size(20, 20).build();
            editScrollLeftPageButton = Button.builder(Component.literal("<"), b -> {
                if(page > 0) {
                    page--;
                }
                updateScrollButtonActive();
            }).size(20, 20).build();
            editScrollRightPageButton = Button.builder(Component.literal(">"), b -> {
                if(page < pageTitle.size() - 1) {
                    page++;
                }
                updateScrollButtonActive();
            }).size(20, 20).build();
            savePageStateButton = Button.builder(Component.literal((ConfigManager.fastCommandMenuSavePageState.getValue() ? "§a" : "§c") + "保存页面状态"), b -> {
                ConfigManager.fastCommandMenuSavePageState.setValue(!ConfigManager.fastCommandMenuSavePageState.getValue());
                ConfigManager.saveConfig();
                b.setMessage(Component.literal((ConfigManager.fastCommandMenuSavePageState.getValue() ? "§a" : "§c") + "保存翻页状态"));
            }).size(80, 20).build();
            savePageStateButton.setTooltip(Tooltip.create(Component.literal("保存你当前的翻页状态, 这样你重启客户端前每次打开这个菜单都会保持上次的翻页, 而不是重置到第一页")));

            saveDefaultPageButton = Button.builder(Component.empty(), _ -> {
                ConfigManager.fastCommandMenuDefaultPage.setValue(page);
                ConfigManager.saveConfig();
            }).size(80, 20).build();
            saveDefaultPageButton.setTooltip(Tooltip.create(Component.literal("保存你当前的默认页面, 这样你每次打开这个菜单就会重置到这个页面")));

            addRenderableWidget(saveDefaultPageButton);
            addRenderableWidget(savePageStateButton);
            addRenderableWidget(editScrollLeftPageButton);
            addRenderableWidget(editRemovePageButton);
            addRenderableWidget(editAddPageButton);
            addRenderableWidget(editScrollRightPageButton);
            updateScrollButtonActive();
        }
        updateScrollState();
        Runnable runnable = () -> GLFW.glfwSetCursorPos(minecraft.getWindow().handle(), minecraft.getWindow().getScreenWidth() / 2f, minecraft.getWindow().getScreenHeight() / 2f);
        minecraft.schedule(runnable);
        runnable.run();
    }

    private void updateScrollButtonActive() {
        if(editScrollLeftPageButton != null) editScrollLeftPageButton.active = page > 0;
        if(editScrollRightPageButton != null) editScrollRightPageButton.active = page < pageTitle.size() - 1;
        if(editRemovePageButton != null) editRemovePageButton.active = !pageTitle.isEmpty();
    }

    private void updateScrollState() {
        canScrollLeft = page > 0;
        canScrollRight = page < pageTitle.size() - 1;
    }

    private void loadPageInfo() {
        if(editTitle != null && page >= 0 && page < pageTitle.size()) editTitle.setValue(pageTitle.get(page));
        editingButton = null;
    }

    @Override
    public void tick() {
        if (editTitle != null) editTitle.active = !pageTitle.isEmpty();
        if (editButtonTitle != null) {
            editButtonTitle.active = editingButton != null;
            String setValue = editingButton != null ? editingButton.getTitle() : "";
            if(!setValue.equals(editButtonTitle.getValue())) {
                editButtonTitle.setValue(setValue);
            }
        }
        if (editButtonCommand != null) {
            editButtonCommand.active = editingButton != null;
            String setValue = editingButton != null ? editingButton.getCommand() : "";
            if(!setValue.equals(editButtonCommand.getValue())) {
                editButtonCommand.setValue(setValue);
            }
        }

        if(lastPage != page) {
            lastPage = page;
            if(editMode) loadPageInfo();
            updateScrollState();
            updateScrollButtonActive();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if(editTitle != null) {
            editTitle.setX((width - editTitle.getWidth()) / 2);
            editTitle.setY(10);
        }
        if(editButtonTitle != null) {
            editButtonTitle.setX((width - editButtonTitle.getWidth()) / 2);
            editButtonTitle.setY(height - 50);

            if(editAddPageButton != null) {
                editAddPageButton.setX((width + editButtonTitle.getWidth()) / 2);
                editAddPageButton.setY(editButtonTitle.getY());

            }
            if(editRemovePageButton != null) {
                editRemovePageButton.setX((width - editButtonTitle.getWidth()) / 2 - editRemovePageButton.getWidth());
                editRemovePageButton.setY(editButtonTitle.getY());
            }
        }
        if(editButtonCommand != null) {
            editButtonCommand.setX((width - editButtonCommand.getWidth()) / 2);
            editButtonCommand.setY(height - 30);
            graphics.text(font, "§a" + (page + 1) + "/" + pageTitle.size(), (width + editButtonCommand.getWidth()) / 2 + 25, editButtonCommand.getY() + editButtonCommand.getHeight() / 2 - font.lineHeight / 2, 0xFFFFFFFF);

            if(editScrollRightPageButton != null) {
                editScrollRightPageButton.setX((width + editButtonCommand.getWidth()) / 2);
                editScrollRightPageButton.setY(editButtonCommand.getY());
            }
            if(editScrollLeftPageButton != null) {
                editScrollLeftPageButton.setX((width - editButtonCommand.getWidth()) / 2 - editScrollLeftPageButton.getWidth());
                editScrollLeftPageButton.setY(editButtonCommand.getY());
            }
        }
        if(savePageStateButton != null) {
            savePageStateButton.setX(10);
            savePageStateButton.setY(height - 30);
        }
        if(saveDefaultPageButton != null) {
            saveDefaultPageButton.setX(10);
            saveDefaultPageButton.setY(height - 50);
            saveDefaultPageButton.setMessage(ConfigManager.fastCommandMenuDefaultPage.getValue() != page ? Component.literal("保存为默认页面") : Component.literal("默认页面已保存"));
            saveDefaultPageButton.active = ConfigManager.fastCommandMenuDefaultPage.getValue() != page;
        }

        if(!editMode) {
            if(!pageTitle.isEmpty()) {
                graphics.centeredText(font, "快捷菜单", width / 2, 10, 0xFFFFFFFF);
                String result = pageTitle.get(page).replace("&", "§");
                graphics.centeredText(font, result.isBlank() ? "§7<未命名>" : result, width / 2, 30, 0xFFFFFFFF);
            } else {
                graphics.centeredText(font, "还没有设置快捷指令, 使用/skydiao editfastcommand进行自定义设置awa", width / 2, height / 2, 0xFFFFFFFF);
            }
            graphics.centeredText(font, "§a左键§b上一页§a, 右键§e下一页", width / 2, height - font.lineHeight * 2 - 10 , 0xFFFFFFFF);
            graphics.centeredText(font, "§a将鼠标移至按钮上后松开快捷键, /skydiao editfastcommand进行自定义设置", width / 2, height - font.lineHeight - 10 , 0xFFFFFFFF);
        }

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean temp = super.mouseClicked(event, doubleClick);
        if(!temp && editMode) {
            setFocused(null);
            editingButton = null;
        }
        if(!editMode) {
            if(canScrollLeft && event.button() == 0) {
                page--;
                updateScrollState();
                return true;
            }
            if(canScrollRight && event.button() == 1) {
                page++;
                updateScrollState();
                return true;
            }
        }
        return temp;
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent event) {
        if (KeyBindsManager.fastMenu.matchesMouse(event) && !editMode) {
            onClose();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyReleased(@NotNull KeyEvent event) {
        if (KeyBindsManager.fastMenu.matches(event) && !editMode) {
            onClose();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public void onClose() {
        if (editMode) {
            save();
        } else {
            for (GuiEventListener child : children()) {
                if(child instanceof CommandButton cb) {
                    if (cb.isHovered()) {
                        cb.playDownSound(minecraft.getSoundManager());
                        ToolList.sendChatMessage(cb.getCommand());
                    }
                }
            }
            if(ConfigManager.fastCommandMenuSavePageState.getValue()) savedPage = this.page;
        }
        super.onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return editMode;
    }

    public class CommandButton extends Button {

        private int page;
        private int slot;
        private TitleAndCommand instance;

        private float animation;

        private float xOffAnimation;
        private float targetXOffAnimation;

        public CommandButton() {
            super(-50, -50, 50, 50, Component.empty(), b -> {
                if (editMode) editingButton = (CommandButton) b;
            }, Supplier::get);
            xOffAnimation = targetXOffAnimation = FastCommandMenuScreen.this.page * FastCommandMenuScreen.this.width;
        }

        private void updateXOffAnimation(float a) {
            targetXOffAnimation = FastCommandMenuScreen.this.page * FastCommandMenuScreen.this.width;
            xOffAnimation = Mth.clamp(xOffAnimation + (targetXOffAnimation - xOffAnimation) * a * 1.5f, Math.min(xOffAnimation, targetXOffAnimation), Math.max(xOffAnimation, targetXOffAnimation));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            updateXOffAnimation(a);
            active = editMode;
            if(isHovered()) {
                if(animation < 1) animation += a / 3;
            } else {
                if(animation > 0) animation -= a / 3;
            }
            if(editingButton == this) {
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF009900);
                graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0xFF000000 | ((int) Mth.clampedLerp(animation, 0, 55)) << 8);
            } else {
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), (getCommand().isBlank() ? 0xFF220000 : 0xFF000000) | ((int) Mth.clampedLerp(animation, 0, 55)) << 8);
            }

            RenderUtils.renderScrollingString(graphics, font, getMessage(), getX() + getWidth() / 2, getX() + 4, getY(), getX() + getWidth() - 4, getY() + getHeight(), 0xFFFFFFFF);
        }

        public String getCommand() {
            return instance.command();
        }

        public void setInstance(TitleAndCommand instance) {
            this.instance = instance;
            setMessage(Component.literal(instance.title().replace("&", "§")));
        }

        @Override
        public boolean isHovered() {
            return super.isHovered() && !instance.command().isBlank();
        }

        public void setSlot(int slot) {
            this.slot = slot;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public String getTitle() {
            return instance.title();
        }

        public int getX() {
            return switch (slot) {
                case 0, 3, 5 -> FastCommandMenuScreen.this.width / 2 - getWidth() / 2 - 5 - getWidth();
                case 1, 6 -> FastCommandMenuScreen.this.width / 2 - getWidth() / 2;
                case 2, 4, 7 -> FastCommandMenuScreen.this.width / 2 + getWidth() / 2 + 5;
                default -> super.getX();
            } + page * FastCommandMenuScreen.this.width - (int) xOffAnimation;
        }

        public int getY() {
            return switch (slot) {
                case 0, 1, 2 -> FastCommandMenuScreen.this.height / 2 - getHeight() / 2 - 5 - getHeight();
                case 3, 4 -> FastCommandMenuScreen.this.height / 2 - getHeight() / 2;
                case 5, 6, 7 -> FastCommandMenuScreen.this.height / 2 + getHeight() / 2 + 5;
                default -> super.getY();
            };
        }

        public void setX(int x) {}

        public void setY(int y) {}

        public void setTitle(String string) {
            TitleAndCommand temp = new TitleAndCommand(string, instance.command());
            FastCommandMenuScreen.commands.replaceAll(inst -> inst == instance ? temp : inst);
            setInstance(temp);
        }

        public void setCommand(String string) {
            TitleAndCommand temp = new TitleAndCommand(instance.title(), string);
            FastCommandMenuScreen.commands.replaceAll(inst -> inst == instance ? temp : inst);
            setInstance(temp);
        }

        @Override
        public @NotNull Component getMessage() {
            if(instance.title().isBlank() && editMode) return Component.literal("§7点击编辑");
            return super.getMessage();
        }

    }
}
