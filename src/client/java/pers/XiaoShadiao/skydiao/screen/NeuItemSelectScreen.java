package pers.XiaoShadiao.skydiao.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 通用 NEU 物品选择界面：搜索物品后通过回调把选中的物品交给调用方。
 *
 * <p>选择完成后默认返回 {@code parent}；若回调自己切换了界面则不会覆盖。
 */
public class NeuItemSelectScreen extends Screen {

    private static final Pattern COLOR_PATTERN = Pattern.compile("§.");

    private final Screen parent;
    private final Consumer<NeuItemRepository.NeuItem> onSelect;
    private final NeuItemRepository repository = NeuItemRepository.INSTANCE;

    private EditBox searchBox;
    private ItemList itemList;
    private Button selectButton;

    public NeuItemSelectScreen(Screen parent, Consumer<NeuItemRepository.NeuItem> onSelect) {
        this(parent, "选择物品", onSelect);
    }

    public NeuItemSelectScreen(Screen parent, String title, Consumer<NeuItemRepository.NeuItem> onSelect) {
        super(Component.literal(title));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        repository.ensureLoaded();

        int listWidth = Math.min(380, width - 20);
        int listX = (width - listWidth) / 2;
        int listY = 54;
        int listHeight = Math.max(60, height - 100);

        searchBox = new EditBox(font, listX, 28, listWidth, 20, Component.literal("搜索物品"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("搜索物品名 / 内部 ID"));
        searchBox.setResponder(_ -> refreshResults());
        addRenderableWidget(searchBox);

        itemList = new ItemList(minecraft, listWidth, listHeight, listY, 24);
        itemList.setX(listX);
        addRenderableWidget(itemList);

        selectButton = addRenderableWidget(Button.builder(Component.literal("选择"), _ -> select())
                .bounds(width / 2 - 85, height - 30, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), _ -> onClose())
                .bounds(width / 2 + 5, height - 30, 80, 20).build());

        refreshResults();
    }

    private void refreshResults() {
        String query = searchBox == null ? "" : searchBox.getValue().trim();
        List<NeuItemRepository.NeuItem> results = query.isEmpty()
                ? repository.browse(80)
                : repository.search(query, 80);
        if (itemList != null) {
            itemList.updateEntries(results);
        }
        if (selectButton != null) {
            selectButton.active = false;
        }
    }

    private void select() {
        ItemEntry selected = itemList == null ? null : itemList.getSelected();
        if (selected == null) {
            return;
        }
        onSelect.accept(selected.item);
        if (minecraft.gui.screen() == this) {
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        if (itemList != null && itemList.children().isEmpty()) {
            graphics.centeredText(font, "没有匹配的物品", width / 2, height / 2 - 10, 0xFFAAAAAA);
            if (!repository.isLoaded()) {
                graphics.centeredText(font, "尚未下载 NEU 数据，请执行 /skydiao neu download",
                        width / 2, height / 2 + 4, 0xFFFFAA00);
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private static String stripColors(String value) {
        return value == null ? "" : COLOR_PATTERN.matcher(value).replaceAll("");
    }

    private class ItemList extends ObjectSelectionList<ItemEntry> {

        private ItemList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        private void updateEntries(List<NeuItemRepository.NeuItem> items) {
            clearEntries();
            for (NeuItemRepository.NeuItem item : items) {
                addEntry(new ItemEntry(item));
            }
            setSelected(null);
        }

        @Override
        public void setSelected(ItemEntry entry) {
            super.setSelected(entry);
            if (selectButton != null) {
                selectButton.active = entry != null;
            }
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 12;
        }

        @Override
        protected int scrollBarX() {
            return getX() + getWidth() - 6;
        }
    }

    private class ItemEntry extends ObjectSelectionList.Entry<ItemEntry> {

        private final NeuItemRepository.NeuItem item;
        private final ItemStack icon;

        private ItemEntry(NeuItemRepository.NeuItem item) {
            this.item = item;
            this.icon = repository.createItemStack(item);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            int x = getContentX();
            int y = getContentY();
            int contentHeight = getContentHeight();
            graphics.item(minecraft.player, icon, x + 2, y + (contentHeight - 16) / 2, 0);

            int textY = y + (contentHeight - font.lineHeight * 2) / 2;
            String name = font.plainSubstrByWidth(stripColors(item.displayName()), getContentWidth() - 28);
            graphics.text(font, name, x + 24, textY, 0xFFFFFFFF);
            String internalName = font.plainSubstrByWidth(item.internalName(), getContentWidth() - 28);
            graphics.text(font, internalName, x + 24, textY + font.lineHeight, 0xFFAAAAAA);
        }

        @Override
        public Component getNarration() {
            return Component.literal(item.displayName());
        }
    }
}
