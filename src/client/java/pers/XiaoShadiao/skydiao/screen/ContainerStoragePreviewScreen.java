package pers.XiaoShadiao.skydiao.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.storage.ContainerPage;
import pers.XiaoShadiao.skydiao.utils.storage.ContainerStorageCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 
 * 末影箱/背包预览界面。
 *
 * <p>参考 Firmament 的 StorageOverlay：Hypixel 打开 Storage/Ender Chest/Backpack 容器时，
 * 通过 {@code CustomFabricEvents.REPLACE_SCREEN} 用本界面替换原版 {@code ContainerScreen}，
 * 同时保留真实 {@link ChestMenu}。
 *
 * <p>26.3 起 {@code Slot.x/y} 是 final，无法像 Firmament 26.1 那样把真实槽位挪到自绘布局上，
 * 所以这里改为自绘物品，并在点击时直接发送 {@link ContainerInput} 到真实菜单：
 * 当前页和玩家背包的槽位都可以直接拿取/放置，其他页面显示缓存内容，点击卡片切换页面。
 */
public class ContainerStoragePreviewScreen
        extends AbstractContainerScreen<AbstractContainerMenu>
        implements IContainerScreenHelper {

    private static final Identifier SLOT_SPRITE =
            Identifier.withDefaultNamespace("container/slot");
    private static final Identifier SLOT_HIGHLIGHT_FRONT =
            Identifier.withDefaultNamespace("container/slot_highlight_front");
    private static final int CARD_WIDTH = 168;
    private static final int CARD_GAP = 10; 
    private static final int SIDE_PADDING = 10;
    private static final int CONTENT_TOP = 30;
    private static final int TITLE_HEIGHT = 14;
    private static final int SLOT_SIZE = 18;
    private static final int MISSING_HEIGHT = 58;
    private static final int PLAYER_PANEL_WIDTH = SLOT_SIZE * 9 + 8;
    private static final int PLAYER_PANEL_HEIGHT = SLOT_SIZE * 4 + 8;
    /** 玩家背包面板底部留白，避开 REI 的搜索框。 */
    private static final int PLAYER_PANEL_BOTTOM = 30;
    /** 报给 REI 的容器宽度额外留出一点空间。 */
    private static final int REI_WIDTH_BONUS = 26;
    /** 未选中页面的黑色半透明遮罩。 */
    private static final int INACTIVE_OVERLAY = 0x66000000;
    /** 入口容器固定 4 行：去掉 Storage 菜单的第 1、3 行后剩余 36 格。 */
    private static final int ENTRY_ROWS = 4;
    private static final int ENTRY_CARD_HEIGHT = TITLE_HEIGHT + ENTRY_ROWS * SLOT_SIZE + 6;

    /** 跨界面保留滚动位置，切页/重新打开容器后不会回到顶部。 */
    private static double retainedScroll;

    /** 从独立概览点开页面后，关闭页面时返回的概览界面。 */
    private static ContainerStoragePreviewScreen returnOverview;

    private final Screen parent;
    /** 真实打开的容器菜单；独立概览时为 null。 */
    private final ChestMenu realMenu;
    /** 当前真实打开的存储页；独立概览或 Hypixel 的 Storage 概览时为 null。 */
    private final ContainerPage activePage;
    private final List<Card> cards = new ArrayList<>();
    /** 玩家背包索引（0~35）到真实菜单槽位的映射。 */
    private final Map<Integer, Slot> playerSlots = new HashMap<>();

    private int contentLeft;
    private int contentWidth;
    private int contentBottom;
    private int contentHeight;
    private int playerPanelX;
    private int playerPanelY;
    private int entryCardX;
    private int entryCardY;
    private double scroll;
    private HoveredSlot hoveredSlot;

    private record Card(ContainerPage page, int x, int y, int height, int rows) {
    }

    private record HoveredSlot(Slot slot, int x, int y) {
    }

    public ContainerStoragePreviewScreen() {
        this(null);
    }

    /** 独立概览（/skydiao storage），不连接服务器容器。 */
    public ContainerStoragePreviewScreen(Screen parent) {
        super(new PreviewMenu(), ToolList.mc.player.getInventory(), Component.literal("容器预览"),
                overviewWidth(), overviewHeight());
        this.parent = parent;
        this.realMenu = null;
        this.activePage = null;
    }

    /** 接管 Hypixel 打开的容器界面，保留真实菜单以便直接操作物品。 */
    public ContainerStoragePreviewScreen(ChestMenu menu, Component title) {
        super(menu, ToolList.mc.player.getInventory(), title, overviewWidth(), overviewHeight());
        this.parent = null;
        this.realMenu = menu;
        this.activePage = ContainerStorageCache.parsePage(title.getString());
    }

    private static int columnsFor(int guiWidth) {
        return Math.max(1, (guiWidth - SIDE_PADDING * 2 + CARD_GAP) / (CARD_WIDTH + CARD_GAP));
    }

    /**
     * REI 等外部模组用 imageWidth/imageHeight 计算容器区域，
     * 这里返回实际卡片网格宽度，避免它们按默认 176 宽排布。
     */
    private static int overviewWidth() {
        int columns = columnsFor(ToolList.mc.getWindow().getGuiScaledWidth());
        return columns * CARD_WIDTH + (columns - 1) * CARD_GAP + REI_WIDTH_BONUS;
    }

    /** 高度覆盖卡片滚动区域，不含底部入口卡片/玩家背包，避免 REI 面板压到它们。 */
    private static int overviewHeight() {
        int guiHeight = ToolList.mc.getWindow().getGuiScaledHeight();
        int groupBottom = guiHeight - PLAYER_PANEL_BOTTOM;
        int groupTop = Math.max(CONTENT_TOP + ENTRY_CARD_HEIGHT, groupBottom - ENTRY_CARD_HEIGHT);
        int bottom = Math.max(CONTENT_TOP + 20, groupTop - 20);
        return Math.max(1, bottom - CONTENT_TOP);
    }

    @Override
    protected void init() {
        super.init();
        playerSlots.clear();
        if (realMenu != null) {
            for (Slot slot : realMenu.slots) {
                if (slot.container instanceof Inventory) {
                    playerSlots.put(slot.getContainerSlot(), slot);
                }
            }
        }
        // 容器内容包通常晚于界面创建，扫描统一放到 containerTick，避免用空槽位误判解锁状态。
        ContainerStorageCache.INSTANCE.ensureLoaded();
        rebuildCards();
    }

    /** 重新计算卡片布局；容器内容/解锁状态变化后也靠它刷新。 */
    private void rebuildCards() {
        cards.clear();
        int groupBottom = height - PLAYER_PANEL_BOTTOM;
        // 玩家背包保持居中，入口容器卡片常驻在它左侧。
        playerPanelX = (width - PLAYER_PANEL_WIDTH) / 2;
        playerPanelY = groupBottom - PLAYER_PANEL_HEIGHT;
        entryCardX = Math.max(4, playerPanelX - CARD_GAP - CARD_WIDTH);
        entryCardY = playerPanelY + PLAYER_PANEL_HEIGHT - ENTRY_CARD_HEIGHT;
        contentBottom = Math.max(CONTENT_TOP + 20, Math.min(playerPanelY, entryCardY) - 20);

        int columns = columnsFor(width);
        contentWidth = columns * CARD_WIDTH + (columns - 1) * CARD_GAP;
        contentLeft = (width - contentWidth) / 2;
        // 让 REI 等模组读取到与实际卡片区域一致的容器位置。
        leftPos = contentLeft;
        topPos = CONTENT_TOP;

        int rowY = CONTENT_TOP;
        int column = 0;
        int rowHeight = 0;
        for (ContainerPage page : ContainerPage.all()) {
            // 未解锁的页直接不显示，避免占位。
            if (!page.equals(activePage) && !ContainerStorageCache.INSTANCE.isAvailable(page)) {
                continue;
            }
            int rows = rowsOf(page);
            int cardHeight = rows <= 0
                    ? MISSING_HEIGHT
                    : TITLE_HEIGHT + rows * SLOT_SIZE + 6;
            cards.add(new Card(page,
                    contentLeft + column * (CARD_WIDTH + CARD_GAP),
                    rowY, cardHeight, rows));
            rowHeight = Math.max(rowHeight, cardHeight);
            column++;
            if (column >= columns) {
                column = 0;
                rowY += rowHeight + CARD_GAP;
                rowHeight = 0;
            }
        }
        contentHeight = rowY + rowHeight + 8 - CONTENT_TOP;
        scroll = clampScroll(retainedScroll);
    }

    /** 当前页用真实菜单行数，其他页用缓存行数；0 表示尚未缓存。 */
    private int rowsOf(ContainerPage page) {
        if (realMenu != null && page.equals(activePage)) {
            return Math.max(0, realMenu.getRowCount() - 1);
        }
        ContainerStorageCache.CachedPage cached = ContainerStorageCache.INSTANCE.page(page);
        return cached == null ? 0 : cached.rows();
    }

    /** 当前是否正在显示 Hypixel 的 /storage 入口菜单（没有选中具体容器页）。 */
    private boolean entryMenuActive() {
        return realMenu != null && activePage == null && realMenu.slots.size() > 53;
    }

    /** 4 行入口卡片第 index 格对应原 Storage 菜单的槽位（跳过原第 1、3 行）。 */
    private static int entrySourceIndex(int index) {
        return index < 9 ? index + 9 : index + 18;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // 不调用原版槽位渲染：原版槽位坐标固定，无法跟随滚动卡片。
        hoveredSlot = slotAt(mouseX, mouseY);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);

        graphics.enableScissor(contentLeft, CONTENT_TOP,
                contentLeft + contentWidth, contentBottom);
        for (Card card : cards) {
            int cardY = card.y - (int) scroll;
            if (cardY + card.height < CONTENT_TOP || cardY > contentBottom) {
                continue;
            }
            drawCard(graphics, card, cardY, mouseX, mouseY);
        }
        graphics.disableScissor();

        drawScrollBar(graphics);
        drawEntryCard(graphics, mouseX, mouseY);
        drawPlayerPanel(graphics);

        if (hoveredSlot != null) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT,
                    hoveredSlot.x() - 4, hoveredSlot.y() - 4, 24, 24);
            ItemStack stack = hoveredSlot.slot().getItem();
            if (!stack.isEmpty()) {
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }
        }

        ItemStack carried = realMenu == null ? ItemStack.EMPTY : realMenu.getCarried();
        if (!carried.isEmpty()) {
            graphics.item(carried, mouseX - 8, mouseY - 8, 0);
            graphics.itemDecorations(font, carried, mouseX - 8, mouseY - 8);
        }
    }

    private void drawCard(GuiGraphicsExtractor graphics, Card card,
                          int y, int mouseX, int mouseY) {
        int x = card.x;
        boolean active = card.page.equals(activePage);
        boolean cached = card.rows > 0;
        boolean dimmed = !active;
        boolean hovered = !active && mouseX >= x && mouseX < x + CARD_WIDTH
                && mouseY >= y && mouseY < y + card.height;

        int background = active ? 0xEE1F2A1F : (cached ? 0xEE1B1B1B : 0xEE351515);
        if (hovered) {
            background = cached ? 0xEE2A2A2A : 0xEE4A1C1C;
        }
        graphics.fill(x, y, x + CARD_WIDTH, y + card.height, background);

        int border = active || hovered ? 0xFFFFD54F
                : (cached ? 0xFF4A4A4A : 0xFF8A3030);
        graphics.fill(x, y, x + CARD_WIDTH, y + 1, border);
        graphics.fill(x, y + card.height - 1, x + CARD_WIDTH, y + card.height, border);
        graphics.fill(x, y, x + 1, y + card.height, border);
        graphics.fill(x + CARD_WIDTH - 1, y, x + CARD_WIDTH, y + card.height, border);

        int titleColor = card.page.kind() == ContainerPage.Kind.ENDER_CHEST
                ? 0xFF7FDBFF : 0xFFFFC66D;
        graphics.text(font, card.page.displayName(), x + 5, y + 4, titleColor);

        if (!cached) {
            ItemStack icon = new ItemStack(card.page.kind() == ContainerPage.Kind.ENDER_CHEST
                    ? Items.ENDER_CHEST : Items.CHEST);
            if (minecraft.player != null) {
                graphics.item(minecraft.player, icon, x + 8, y + 20, 0);
            } else {
                graphics.item(icon, x + 8, y + 20, 0);
            }
            graphics.centeredText(font, Component.literal("点击加载"),
                    x + CARD_WIDTH / 2 + 8, y + 22, 0xFFFFAAAA);
            graphics.centeredText(font, Component.literal(card.page.command()),
                    x + CARD_WIDTH / 2, y + 38, 0xFF888888);
            if (hovered) {
                graphics.setTooltipForNextFrame(
                        Component.literal("§e点击加载 §7" + card.page.command()),
                        mouseX, mouseY);
            }
            if (dimmed) {
                graphics.fill(x, y, x + CARD_WIDTH, y + card.height, INACTIVE_OVERLAY);
            }
            return;
        }

        if (active) {
            graphics.text(font, "当前页", x + CARD_WIDTH - 5 - font.width("当前页"),
                    y + 4, 0xFFFFD54F);
        }

        ContainerStorageCache.CachedPage cachedPage = ContainerStorageCache.INSTANCE.page(card.page);
        int slotY = y + TITLE_HEIGHT;
        boolean overItem = false;
        for (int i = 0; i < card.rows * 9; i++) {
            int[] lineCol = getLineColByIndex(i);
            int slotX = x + 4 + lineCol[1] * SLOT_SIZE;
            int currentY = slotY + lineCol[0] * SLOT_SIZE;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE,
                    slotX, currentY, SLOT_SIZE, SLOT_SIZE);

            ItemStack stack;
            if (active) {
                int menuSlot = 9 + i;
                stack = menuSlot < realMenu.slots.size()
                        ? realMenu.slots.get(menuSlot).getItem() : ItemStack.EMPTY;
            } else {
                stack = cachedPage == null ? ItemStack.EMPTY : cachedPage.item(i);
            }
            if (stack.isEmpty()) {
                continue;
            }
            if (minecraft.player != null) {
                graphics.item(minecraft.player, stack, slotX + 1, currentY + 1, 0);
            } else {
                graphics.item(stack, slotX + 1, currentY + 1, 0);
            }
            graphics.itemDecorations(font, stack, slotX + 1, currentY + 1);

            if (!active && mouseX >= slotX && mouseX < slotX + 16
                    && mouseY >= currentY && mouseY < currentY + 16) {
                overItem = true;
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }
        }

        if (!active && hovered && !overItem) {
            graphics.setTooltipForNextFrame(
                    Component.literal("§e点击打开 §7" + card.page.command()),
                    mouseX, mouseY);
        }
        if (dimmed) {
            graphics.fill(x, y, x + CARD_WIDTH, y + card.height, INACTIVE_OVERLAY);
        }
    }

    private void drawScrollBar(GuiGraphicsExtractor graphics) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int viewHeight = contentBottom - CONTENT_TOP;
        int trackX = contentLeft + contentWidth + 4;
        graphics.fill(trackX, CONTENT_TOP, trackX + 4, contentBottom, 0x66000000);
        int knobHeight = Math.max(16, viewHeight * viewHeight / (viewHeight + maxScroll));
        int knobY = CONTENT_TOP + (int) ((viewHeight - knobHeight) * (scroll / maxScroll));
        graphics.fill(trackX, knobY, trackX + 4, knobY + knobHeight, 0xFFAAAAAA);
    }

    /** 常驻在玩家背包左侧的 Storage 入口卡片：固定 4 行（去掉菜单第 1、3 行）。 */
    private void drawEntryCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean live = entryMenuActive();
        boolean hovered = mouseX >= entryCardX && mouseX < entryCardX + CARD_WIDTH
                && mouseY >= entryCardY && mouseY < entryCardY + ENTRY_CARD_HEIGHT;

        graphics.fill(entryCardX, entryCardY,
                entryCardX + CARD_WIDTH, entryCardY + ENTRY_CARD_HEIGHT, 0xEE1B1B1B);
        int border = hovered && !live ? 0xFFFFD54F : 0xFF4A4A4A;
        graphics.fill(entryCardX, entryCardY,
                entryCardX + CARD_WIDTH, entryCardY + 1, border);
        graphics.fill(entryCardX, entryCardY + ENTRY_CARD_HEIGHT - 1,
                entryCardX + CARD_WIDTH, entryCardY + ENTRY_CARD_HEIGHT, border);
        graphics.fill(entryCardX, entryCardY,
                entryCardX + 1, entryCardY + ENTRY_CARD_HEIGHT, border);
        graphics.fill(entryCardX + CARD_WIDTH - 1, entryCardY,
                entryCardX + CARD_WIDTH, entryCardY + ENTRY_CARD_HEIGHT, border);
        graphics.text(font, "入口容器", entryCardX + 5, entryCardY + 4, 0xFFFFD54F);

        if (!live) {
            // 未选中时保持 4x9 尺寸，不显示空槽位和物品，只显示入口文字。
            graphics.fill(entryCardX, entryCardY,
                    entryCardX + CARD_WIDTH, entryCardY + ENTRY_CARD_HEIGHT, INACTIVE_OVERLAY);
            graphics.centeredText(font, Component.literal("点击打开"),
                    entryCardX + CARD_WIDTH / 2,
                    entryCardY + (TITLE_HEIGHT + ENTRY_ROWS * SLOT_SIZE) / 2 - 4,
                    0xFFAAAAAA);
            return;
        }

        for (int i = 0; i < ENTRY_ROWS * 9; i++) {
            int[] pos = entrySlotPosition(i);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE,
                    pos[0], pos[1], SLOT_SIZE, SLOT_SIZE);

            int source = entrySourceIndex(i);
            if (source >= realMenu.slots.size()) {
                continue;
            }
            ItemStack stack = realMenu.slots.get(source).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            if (minecraft.player != null) {
                graphics.item(minecraft.player, stack, pos[0] + 1, pos[1] + 1, 0);
            } else {
                graphics.item(stack, pos[0] + 1, pos[1] + 1, 0);
            }
            graphics.itemDecorations(font, stack, pos[0] + 1, pos[1] + 1);
        }
    }

    private int[] entrySlotPosition(int index) {
        int column = index % 9;
        int row = index / 9;
        return new int[]{
                entryCardX + 4 + column * SLOT_SIZE,
                entryCardY + TITLE_HEIGHT + row * SLOT_SIZE
        };
    }

    private void drawPlayerPanel(GuiGraphicsExtractor graphics) {
        graphics.fill(playerPanelX, playerPanelY,
                playerPanelX + PLAYER_PANEL_WIDTH, playerPanelY + PLAYER_PANEL_HEIGHT, 0xEE1B1B1B);
        int border = 0xFF4A4A4A;
        graphics.fill(playerPanelX, playerPanelY,
                playerPanelX + PLAYER_PANEL_WIDTH, playerPanelY + 1, border);
        graphics.fill(playerPanelX, playerPanelY + PLAYER_PANEL_HEIGHT - 1,
                playerPanelX + PLAYER_PANEL_WIDTH, playerPanelY + PLAYER_PANEL_HEIGHT, border);
        graphics.fill(playerPanelX, playerPanelY,
                playerPanelX + 1, playerPanelY + PLAYER_PANEL_HEIGHT, border);
        graphics.fill(playerPanelX + PLAYER_PANEL_WIDTH - 1, playerPanelY,
                playerPanelX + PLAYER_PANEL_WIDTH, playerPanelY + PLAYER_PANEL_HEIGHT, border);

        List<ItemStack> ownItems = realMenu == null && minecraft.player != null
                ? minecraft.player.getInventory().getNonEquipmentItems() : null;
        for (int i = 0; i < 36; i++) {
            int[] pos = playerSlotPosition(i);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE,
                    pos[0], pos[1], SLOT_SIZE, SLOT_SIZE);

            ItemStack stack;
            if (realMenu != null) {
                Slot slot = playerSlots.get(i);
                stack = slot == null ? ItemStack.EMPTY : slot.getItem();
            } else {
                stack = ownItems == null || i >= ownItems.size() ? ItemStack.EMPTY : ownItems.get(i);
            }
            if (stack.isEmpty()) {
                continue;
            }
            if (minecraft.player != null) {
                graphics.item(minecraft.player, stack, pos[0] + 1, pos[1] + 1, 0);
            } else {
                graphics.item(stack, pos[0] + 1, pos[1] + 1, 0);
            }
            graphics.itemDecorations(font, stack, pos[0] + 1, pos[1] + 1);
        }
    }

    /** 背包索引 0~8 是快捷栏，9~35 是三行主背包。 */
    private int[] playerSlotPosition(int inventoryIndex) {
        int column = inventoryIndex % 9;
        int row = inventoryIndex < 9 ? 3 : inventoryIndex / 9 - 1;
        return new int[]{
                playerPanelX + 4 + column * SLOT_SIZE,
                playerPanelY + 4 + row * SLOT_SIZE
        };
    }

    /** 当前鼠标位置下的真实槽位（当前页、Storage 入口或玩家背包）。 */
    private HoveredSlot slotAt(double mouseX, double mouseY) {
        if (realMenu == null) {
            return null;
        }
        Card activeCard = cardOf(activePage);
        if (activeCard != null) {
            int cardY = activeCard.y - (int) scroll;
            int rows = Math.max(0, realMenu.getRowCount() - 1);
            for (int i = 0; i < rows * 9 && 9 + i < realMenu.slots.size(); i++) {
                int[] lineCol = getLineColByIndex(i);
                int slotX = activeCard.x + 4 + lineCol[1] * SLOT_SIZE;
                int slotY = cardY + TITLE_HEIGHT + lineCol[0] * SLOT_SIZE;
                if (!isSlotVisible(slotY)) {
                    continue;
                }
                if (mouseX >= slotX && mouseX < slotX + 16
                        && mouseY >= slotY && mouseY < slotY + 16) {
                    return new HoveredSlot(realMenu.slots.get(9 + i), slotX, slotY);
                }
            }
        }
        if (entryMenuActive()) {
            for (int i = 0; i < ENTRY_ROWS * 9; i++) {
                int source = entrySourceIndex(i);
                if (source >= realMenu.slots.size()) {
                    break;
                }
                int[] pos = entrySlotPosition(i);
                if (mouseX >= pos[0] && mouseX < pos[0] + 16
                        && mouseY >= pos[1] && mouseY < pos[1] + 16) {
                    return new HoveredSlot(realMenu.slots.get(source), pos[0], pos[1]);
                }
            }
        }
        for (Map.Entry<Integer, Slot> entry : playerSlots.entrySet()) {
            int[] pos = playerSlotPosition(entry.getKey());
            if (mouseX >= pos[0] && mouseX < pos[0] + 16
                    && mouseY >= pos[1] && mouseY < pos[1] + 16) {
                return new HoveredSlot(entry.getValue(), pos[0], pos[1]);
            }
        }
        return null;
    }

    private boolean isSlotVisible(int slotY) {
        return slotY >= CONTENT_TOP && slotY + 16 <= contentBottom;
    }

    private Card cardOf(ContainerPage page) {
        if (page == null) {
            return null;
        }
        for (Card card : cards) {
            if (card.page.equals(page)) {
                return card;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        HoveredSlot hovered = slotAt(click.x(), click.y());
        if (hovered != null && realMenu != null && minecraft.gameMode != null) {
            int button = click.button() == 3 ? 1 : 0;
            boolean quickMove = click.button() == 1 && click.hasShiftDown()
                    && realMenu.getCarried().isEmpty();
            ContainerInput input = quickMove ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
            minecraft.gameMode.handleContainerInput(realMenu.containerId,
                    hovered.slot().index, button, input, minecraft.player);
            return true;
        }

        if (isInsideEntryCard(click.x(), click.y())) {
            if (!entryMenuActive()) {
                openStorageEntry();
            }
            return true;
        }

        if (click.button() == 1
                && click.x() >= contentLeft && click.x() < contentLeft + contentWidth
                && click.y() >= CONTENT_TOP && click.y() < contentBottom) {
            for (Card card : cards) {
                int cardY = card.y - (int) scroll;
                if (click.x() >= card.x && click.x() < card.x + CARD_WIDTH
                        && click.y() >= cardY && click.y() < cardY + card.height) {
                    if (!card.page.equals(activePage)) {
                        openPage(card.page);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        // 点击已在按下时处理，避免原版按固定槽位坐标重复处理。
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (realMenu != null && minecraft.player != null && minecraft.gameMode != null
                && hoveredSlot != null) {
            for (int i = 0; i < minecraft.options.keyHotbarSlots.length; i++) {
                if (minecraft.options.keyHotbarSlots[i].matches(event)) {
                    minecraft.gameMode.handleContainerInput(realMenu.containerId,
                            hoveredSlot.slot().index, i, ContainerInput.SWAP, minecraft.player);
                    return true;
                }
            }
            if (minecraft.options.keyDrop.matches(event)) {
                minecraft.gameMode.handleContainerInput(realMenu.containerId,
                        hoveredSlot.slot().index, event.hasControlDown() ? 1 : 0,
                        ContainerInput.THROW, minecraft.player);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void openPage(ContainerPage page) {
        if (realMenu == null) {
            // 独立概览：记住自己，关闭真实页面后回到这里。
            returnOverview = this;
        }
        AbstractListener.delayTickExecutor.delayExec(
                () -> ToolList.sendChatMessage(page.command()), 1);
    }

    private boolean isInsideEntryCard(double mouseX, double mouseY) {
        return mouseX >= entryCardX && mouseX < entryCardX + CARD_WIDTH
                && mouseY >= entryCardY && mouseY < entryCardY + ENTRY_CARD_HEIGHT;
    }

    /** 入口菜单没打开时，点击入口卡片直接发送 /storage。 */
    private void openStorageEntry() {
        AbstractListener.delayTickExecutor.delayExec(
                () -> ToolList.sendChatMessage("/storage"), 1);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        retainedScroll = clampScroll(retainedScroll - verticalAmount * 24);
        scroll = retainedScroll;
        return true;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (contentBottom - CONTENT_TOP));
    }

    private double clampScroll(double value) {
        return Math.max(0, Math.min(value, maxScroll()));
    }

    @Override
    public void onClose() {
        if (realMenu == null) {
            returnOverview = null;
            minecraft.gui.setScreen(parent);
            return;
        }
        ContainerStoragePreviewScreen overview = returnOverview;
        returnOverview = null;
        if (minecraft.player != null) {
            // 发送关闭容器包，同时会把当前 Screen 置空。
            minecraft.player.closeContainer();
        }
        if (overview != null) {
            minecraft.gui.setScreen(overview);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (realMenu != null) {
            String titleText = title.getString();
            if (activePage != null) {
                ContainerStorageCache.INSTANCE.observeTitle(titleText);
                ContainerStorageCache.INSTANCE.capture(realMenu, activePage);
            } else if (ContainerStorageCache.isStorageOverview(titleText)) {
                ContainerStorageCache.INSTANCE.captureOverview(realMenu);
            }
        }
        // 容器内容包通常晚于界面创建才到，重新排版保证卡片和解锁状态及时刷新。
        rebuildCards();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    /**
     * 独立概览只借用容器界面的渲染流程，不需要真实槽位；
     * 空菜单可以让原版的悬浮高亮、槽位物品和 tooltip 逻辑保持静默。
     */
    static final class PreviewMenu extends AbstractContainerMenu {

        PreviewMenu() {
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slotIndex) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
