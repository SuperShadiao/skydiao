package pers.XiaoShadiao.skydiao.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.hud.DungeonMapHUD;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.dungeon.DungeonClass;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;
import pers.XiaoShadiao.skydiao.utils.tab.TabReader;

import java.util.*;
import java.util.regex.Matcher;

/**
 * Spirit Leap（传送到玩家）菜单：把服务端发来的箱子界面换成玩家头像网格。
 *
 * <p>和 Skyblocker 一样读取箱子里第 0~rows*9 个槽位的玩家头颅，按职业名、玩家名排序；
 * 左键直接点对应槽位完成传送。职业从 tab 列表（{@code [等级] 名字 (职业 等级)}）解析。
 */
public class LeapOverlayScreen extends AbstractContainerScreen<ChestMenu> {

    private static final int DEFAULT_BUTTON_HEIGHT = 50;
    private static final int BUTTON_SPACING = 8;
    private static final int MAP_SPACING = 12;
    /** 主按钮到「推荐Leap」文字的额外间距。 */
    private static final int RECOMMEND_GAP = 16;
    /** 「推荐Leap」文字到推荐按钮的间距。 */
    private static final int RECOMMEND_LABEL_SPACING = 6;

    private static final Minecraft MC = Minecraft.getInstance();

    /**
     * 两个“推荐 Leap”槽位的玩家名，非静态：每次打开菜单都默认没有推荐，由外部在打开后按需设置。
     * 推荐槽位固定在四个主槽位下方，显示与否不影响主槽位的布局。
     */
    private String addRecommendLeapPlayer1;
    private String addRecommendLeapPlayer2;

    /** 设置（或清空）第一个推荐槽位的玩家名，传 {@code null} 表示不显示。 */
    public void setRecommendLeapPlayer1(String player) {
        addRecommendLeapPlayer1 = player;
        // 界面还没 init 时不用刷新，init 里会正常读取这两个字段
        if (lastItems != null) refreshEntries();
    }

    /** 设置（或清空）第二个推荐槽位的玩家名，传 {@code null} 表示不显示。 */
    public void setRecommendLeapPlayer2(String player) {
        addRecommendLeapPlayer2 = player;
        if (lastItems != null) refreshEntries();
    }

    private final ChestMenu menu;
    /** 按钮高度由配置控制，宽和高按 130:50 的比例联动，头像约为高度的 48%。 */
    private final int buttonHeight;
    private final int buttonWidth;
    private final int headSize;
    private final List<Entry> entries = new ArrayList<>();
    /** 推荐槽位里的玩家（从主列表复制，位置单独计算）。 */
    private final List<Entry> recommended = new ArrayList<>();
    /**
     * 玩家名 -> 最后一次看到的职业。玩家死亡后 tab 行会变成 {@code (DEAD)}，靠缓存保住原职业，
     * 不然职业会退化成 Unknown、排序和颜色也会错。
     */
    private static final Map<String, DungeonClass> CLASS_CACHE = new HashMap<>();
    /**
     * 上一次读到的容器内容。客户端收到容器内容包时只会 {@code initializeContents} 填槽位，
     * 不会通知 {@code ContainerListener}，所以只能自己比对槽位来发现内容变化。
     */
    private List<ItemStack> lastItems;
    /** 地图是否绘制（只看地图自身状态，不看 HUD 配置开关）。 */
    private boolean mapVisible;
    private float mapScale = 1f;
    private int mapX;
    private int mapY;
    /** 「推荐Leap」文字的 y 坐标。 */
    private int recommendLabelY;
    /** 鼠标悬停到的地图玩家标记。 */
    private UUID hoveredMapPlayer;

    public LeapOverlayScreen(ChestMenu menu, Component title) {
        super(menu, ToolList.mc.player.getInventory(), title, 450, 500);
        this.menu = menu;
        Integer configured = ConfigManager.dungeonLeapMenuButtonSize.getValue();
        int height = configured == null ? DEFAULT_BUTTON_HEIGHT : configured;
        buttonHeight = Math.max(36, Math.min(80, height));
        buttonWidth = Math.round(buttonHeight * 2.6f);
        headSize = Math.round(buttonHeight * 0.48f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    protected void init() {
        super.init();
        refreshEntries();
    }

    /** 槽位内容是否和上一次读取时不一样。 */
    private boolean itemsChanged() {
        int containerSlots = menu.getRowCount() * 9;
        if (lastItems == null || lastItems.size() != containerSlots) return true;
        for (int i = 0; i < containerSlots && i < menu.slots.size(); i++) {
            if (!ItemStack.matches(lastItems.get(i), menu.slots.get(i).getItem())) return true;
        }
        return false;
    }

    /** 重新读取所有玩家头颅并重新排版。 */
    private void refreshEntries() {
        refreshClassCache();
        int containerSlots = menu.getRowCount() * 9;
        List<Entry> found = new ArrayList<>();
        List<ItemStack> items = new ArrayList<>(containerSlots);
        for (int slot = 0; slot < containerSlots && slot < menu.slots.size(); slot++) {
            ItemStack stack = menu.slots.get(slot).getItem();
            items.add(stack.copy());
            if (!stack.is(Items.PLAYER_HEAD) || !stack.has(DataComponents.PROFILE)) continue;
            ResolvableProfile profile = stack.get(DataComponents.PROFILE);
            if (profile == null) continue;
            String name = stack.getHoverName().getString();
            DungeonClass dungeonClass = CLASS_CACHE.getOrDefault(name, DungeonClass.UNKNOWN);
            found.add(new Entry(profile, name, dungeonClass, statusOf(stack), menu.containerId, slot));
        }
        lastItems = items.size() == containerSlots ? items : null;
        found.sort(Comparator.comparing((Entry entry) -> entry.dungeonClass.displayName())
                .thenComparing(entry -> entry.name));
        entries.clear();
        entries.addAll(assignSlots(found));

        recommended.clear();
        for (String name : new String[]{addRecommendLeapPlayer1, addRecommendLeapPlayer2}) {
            if (name == null || name.isBlank()) continue;
            for (Entry entry : found) {
                if (entry.name.equalsIgnoreCase(name.trim())) {
                    recommended.add(copyOf(entry));
                    break;
                }
            }
        }
        layout();
    }

    /**
     * 按配置的槽位优先级排布玩家：每个槽位按自己的规则顺序找第一个还没分配的匹配玩家；
     * 条件都没命中的空槽位，最后按默认顺序用剩余玩家补齐。
     */
    private static List<Entry> assignSlots(List<Entry> sorted) {
        List<List<String>> slotRules = readSlotRules();
        Entry[] slots = new Entry[4];
        boolean[] used = new boolean[sorted.size()];
        for (int slot = 0; slot < slots.length; slot++) {
            for (String rule : slotRules.get(slot)) {
                int match = findMatch(sorted, used, rule);
                if (match < 0) continue;
                slots[slot] = sorted.get(match);
                used[match] = true;
                break;
            }
        }

        // 空槽位按默认顺序补剩余玩家
        int next = 0;
        for (int slot = 0; slot < slots.length; slot++) {
            if (slots[slot] != null) continue;
            while (next < sorted.size() && used[next]) next++;
            if (next >= sorted.size()) break;
            slots[slot] = sorted.get(next);
            used[next] = true;
        }

        // dev 环境方便看布局：队伍不足 5 人时，用现有玩家循环填满剩下的空槽位
        if (false && ToolList.getInstance().isDevEnvironment() && !sorted.isEmpty()) {
            for (int slot = 0, i = 0; slot < slots.length; slot++) {
                if (slots[slot] != null) continue;
                slots[slot] = copyOf(sorted.get(i++ % sorted.size()));
            }
        }

        List<Entry> result = new ArrayList<>(sorted.size());
        for (int slot = 0; slot < slots.length; slot++) {
            if (slots[slot] == null) continue;
            slots[slot].slot = slot;
            result.add(slots[slot]);
        }
        // 队伍超过 4 人时多出来的按默认顺序追加
        for (int i = 0; i < sorted.size(); i++) {
            if (used[i]) continue;
            Entry entry = sorted.get(i);
            entry.slot = result.size();
            result.add(entry);
        }
        return result;
    }

    /** 读取四个槽位的规则：槽位用分号分隔，槽位内规则用逗号分隔。 */
    private static List<List<String>> readSlotRules() {
        String raw = ConfigManager.dungeonLeapSlotPriority.getValue();
        String[] slots = raw.split(";", -1);
        List<List<String>> rules = new ArrayList<>(4);
        for (int slot = 0; slot < 4; slot++) {
            List<String> slotRules = new ArrayList<>();
            if (slot < slots.length) {
                for (String rule : slots[slot].split(",")) {
                    String trimmed = rule.trim();
                    if (!trimmed.isEmpty()) slotRules.add(trimmed);
                }
            }
            rules.add(slotRules);
        }
        return rules;
    }

    /** 规则可以是职业名，也可以是玩家名；返回第一个匹配的未分配玩家下标。 */
    private static int findMatch(List<Entry> sorted, boolean[] used, String rule) {
        DungeonClass ruleClass = DungeonClass.from(rule);
        for (int i = 0; i < sorted.size(); i++) {
            if (used[i]) continue;
            Entry entry = sorted.get(i);
            if ((ruleClass != DungeonClass.UNKNOWN && entry.dungeonClass == ruleClass)
                    || entry.name.equalsIgnoreCase(rule)) {
                return i;
            }
        }
        return -1;
    }

    private static Entry copyOf(Entry entry) {
        return new Entry(entry.profile, entry.name, entry.dungeonClass, entry.status, entry.syncId, entry.slotId);
    }

    /** 地图在上、2 列按钮网格在下；没有地图时四个主按钮横竖居中，推荐区固定在主按钮下方。 */
    private void layout() {
        // 不受 HUD 配置开关影响，只看地图自身状态（没扫描到 / 进 Boss 房隐藏等）
        mapVisible = DungeonMapHUD.canRenderMap();
        // 按槽位编号算行数，空槽位不占渲染，但位置保留
        int rows = 0;
        for (Entry entry : entries) rows = Math.max(rows, Math.max(0, entry.slot) / 2 + 1);
        int gridWidth = buttonWidth * 2 + BUTTON_SPACING;
        int gridHeight = rows * buttonHeight + Math.max(0, rows - 1) * BUTTON_SPACING;
        // 推荐区高度固定预留，显示/隐藏都不影响主按钮的位置
        int reservedBelow = RECOMMEND_GAP + MC.font.lineHeight + RECOMMEND_LABEL_SPACING + buttonHeight;

        int mapSize = DungeonMapHUD.MAP_SIZE;
        int spacing = mapVisible ? MAP_SPACING : 0;
        mapScale = 1f;
        if (mapVisible) {
            // 屏幕太矮时缩小地图，给下面的按钮和推荐区留出位置
            int available = height - 16 - gridHeight - reservedBelow - spacing;
            if (available < mapSize) {
                mapScale = Math.max(0.5f, available / (float) mapSize);
                mapSize = Math.round(DungeonMapHUD.MAP_SIZE * mapScale);
            }
        }

        // 没地图时只按主网格居中，四个按钮横竖居中；有地图时地图+主网格+推荐区整体居中
        int totalHeight = gridHeight + (mapVisible ? mapSize + spacing + reservedBelow : 0);
        int startY = (height - totalHeight) / 2;
        if (mapVisible) {
            mapX = (width - mapSize) / 2;
            mapY = startY;
            startY += mapSize + spacing;
        } else {
            mapX = 0;
            mapY = 0;
            hoveredMapPlayer = null;
        }

        int startX = (width - gridWidth) / 2;
        for (Entry entry : entries) {
            int index = Math.max(0, entry.slot);
            entry.x = startX + (index % 2) * (buttonWidth + BUTTON_SPACING);
            entry.y = startY + (index / 2) * (buttonHeight + BUTTON_SPACING);
        }

        recommendLabelY = startY + gridHeight + RECOMMEND_GAP;
        int recommendedY = recommendLabelY + MC.font.lineHeight + RECOMMEND_LABEL_SPACING;
        for (int i = 0; i < recommended.size(); i++) {
            Entry entry = recommended.get(i);
            entry.x = startX + (i % 2) * (buttonWidth + BUTTON_SPACING);
            entry.y = recommendedY + (i / 2) * (buttonHeight + BUTTON_SPACING);
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // super.extractRenderState(graphics, mouseX, mouseY, partialTicks);

        Entry buttonHovered = entryAt(mouseX, mouseY);
        if (mapVisible) {
            hoveredMapPlayer = XSDHUD.dungeonMapHUD.renderMap(graphics, mapX, mapY, mapScale, mouseX, mouseY,
                    buttonHovered == null ? null : buttonHovered.profile.partialProfile().id());
        } else {
            hoveredMapPlayer = null;
        }

        for (Entry entry : entries) {
            // 地图上悬停到对应队友时也高亮按钮
            drawEntry(graphics, entry, entry == buttonHovered || isHoveredOnMap(entry));
        }
        if (!recommended.isEmpty()) {
            Component label = Component.literal("推荐Leap").withColor(0xFF55FFFF);
            graphics.centeredText(MC.font, label, width / 2, recommendLabelY, 0xFFFFFFFF);
        }
        for (Entry entry : recommended) {
            drawEntry(graphics, entry, entry == buttonHovered || isHoveredOnMap(entry));
        }
    }

    private void drawEntry(GuiGraphicsExtractor graphics, Entry entry, boolean hovered) {
        graphics.fill(entry.x, entry.y, entry.x + buttonWidth, entry.y + buttonHeight,
                hovered ? 0xFF3A5A8A : 0xFF202020);
        RenderUtils.drawOutlineRect(graphics, entry.x + 1, entry.y + 1,
                entry.x + buttonWidth - 1, entry.y + buttonHeight - 1, entry.dungeonClass.color());

        int centreX = entry.x + buttonWidth / 2;
        int centreY = entry.y + buttonHeight / 2;
        drawHead(graphics, entry.profile, entry.x + 6, centreY - headSize / 2);

        Component classText = Component.literal(entry.dungeonClass.displayName())
                .withColor(entry.dungeonClass.color());
        graphics.centeredText(MC.font, classText, centreX, entry.y + 8, 0xFFFFFFFF);
        graphics.text(MC.font, Component.literal(entry.name),
                entry.x + 6 + headSize + 6, centreY - MC.font.lineHeight / 2, 0xFFFFFFFF);

        if (entry.status != null) {
            graphics.fill(entry.x, entry.y, entry.x + buttonWidth, entry.y + buttonHeight, entry.status.overlay);
            Component statusText = Component.literal(entry.status.text).withColor(entry.status.textColor);
            graphics.centeredText(MC.font, statusText, centreX, entry.y + buttonHeight - 14, 0xFFFFFFFF);
        }
    }

    private void drawHead(GuiGraphicsExtractor graphics, ResolvableProfile profile, int x, int y) {
        PlayerSkin skin = MC.playerSkinRenderCache()
                .lookup(profile)
                .getNow(java.util.Optional.empty())
                .map(PlayerSkinRenderCache.RenderInfo::playerSkin)
                .orElseGet(() -> {
                    java.util.UUID uuid = profile.partialProfile().id();
                    return uuid == null ? DefaultPlayerSkin.getDefaultSkin() : DefaultPlayerSkin.get(uuid);
                });
        PlayerFaceExtractor.extractRenderState(graphics, skin, x, y, headSize);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) return true;
        // 和 AbstractContainerScreen 一样，背包键也能关闭界面
        if (MC.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        // 地图玩家标记：只有已经绑定到菜单里某个玩家的标记才能传送，未绑定视为无效点击
        if (mapVisible && hoveredMapPlayer != null) {
            Entry target = entryOf(hoveredMapPlayer);
            if (target != null) clickEntry(target);
            return true;
        }

        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        Entry clicked = entryAt(mouseX, mouseY);
        if (clicked != null) {
            clickEntry(clicked);
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    /** 点击一个队友按钮对应的容器槽位，然后自动关闭菜单。 */
    private void clickEntry(Entry entry) {
        if (MC.gameMode != null && MC.player != null) {
            MC.gameMode.handleContainerInput(entry.syncId, entry.slotId, 0, ContainerInput.PICKUP, MC.player);
        }
        AbstractListener.dungeonMiscMessageListener.sendPrefixedMessage(ConfigManager.dungeonLeapMsg.getValue().replace("[player]", entry.name));
        onClose();
    }

    /** 地图标记是否就是这名队友。 */
    private boolean isHoveredOnMap(Entry entry) {
        return hoveredMapPlayer != null && hoveredMapPlayer.equals(entry.profile.partialProfile().id());
    }

    /** 地图标记对应的队友；标记还没绑定到菜单里的玩家时返回 null。 */
    private Entry entryOf(UUID uuid) {
        for (Entry entry : entries) {
            if (uuid.equals(entry.profile.partialProfile().id())) return entry;
        }
        for (Entry entry : recommended) {
            if (uuid.equals(entry.profile.partialProfile().id())) return entry;
        }
        return null;
    }

    private boolean isIn(int mouseX, int mouseY, Entry entry) {
        return mouseX >= entry.x && mouseX < entry.x + buttonWidth
                && mouseY >= entry.y && mouseY < entry.y + buttonHeight;
    }

    /** 鼠标下面的队友按钮。 */
    private Entry entryAt(int mouseX, int mouseY) {
        for (Entry entry : entries) {
            if (isIn(mouseX, mouseY, entry)) return entry;
        }
        for (Entry entry : recommended) {
            if (isIn(mouseX, mouseY, entry)) return entry;
        }
        return null;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (MC.player == null || !MC.player.isAlive() || MC.player.isRemoved()) {
            onClose();
            return;
        }
        if (itemsChanged()) {
            refreshEntries();
        } else if (mapVisible != DungeonMapHUD.canRenderMap()) {
            // 地图被隐藏/恢复（例如进入 Boss 房）时重新排版
            layout();
        }
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        extractTransparentBackground(graphics);
    }

    @Override
    public void onClose() {
        if (MC.player != null) MC.player.closeContainer();
        super.onClose();
    }

    @Override
    public void removed() {
        if (MC.player != null) menu.removed(MC.player);
        super.removed();
    }

    /** 从 tab 列表更新「玩家名 -> 职业」缓存；死亡/离线行解析不出职业时保留旧值。 */
    private static void refreshClassCache() {
        for (String line : TabReader.getLines()) {
            Matcher matcher = DungeonClass.TAB_PATTERN.matcher(line);
            if (!matcher.matches()) continue;
            DungeonClass dungeonClass = DungeonClass.from(matcher.group("class"));
            if (dungeonClass == DungeonClass.UNKNOWN) continue;
            CLASS_CACHE.put(matcher.group("name"), dungeonClass);
        }
    }

    private static PlayerStatus statusOf(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return null;
        String text = String.join(" ", lore.lines().stream().map(Component::getString).toList())
                .toLowerCase(Locale.ROOT);
        if (text.contains("dead")) return PlayerStatus.DEAD;
        if (text.contains("offline")) return PlayerStatus.OFFLINE;
        return null;
    }

    /** 一名可传送的队友。 */
    private static final class Entry {
        private final ResolvableProfile profile;
        private final String name;
        private final DungeonClass dungeonClass;
        private final PlayerStatus status;
        private final int syncId;
        private final int slotId;
        /** 在 2x2 主槽位里的位置（0~3）；超过 4 人的玩家依次往后排。 */
        private int slot = -1;
        private int x;
        private int y;

        private Entry(ResolvableProfile profile, String name, DungeonClass dungeonClass, PlayerStatus status,
                      int syncId, int slotId) {
            this.profile = profile;
            this.name = name;
            this.dungeonClass = dungeonClass;
            this.status = status;
            this.syncId = syncId;
            this.slotId = slotId;
        }
    }

    private enum PlayerStatus {
        DEAD("DEAD", 0xFFFF5555, 0x40FF5555),
        OFFLINE("OFFLINE", 0xFFAAAAAA, 0x40AAAAAA);

        private final String text;
        private final int textColor;
        private final int overlay;

        PlayerStatus(String text, int textColor, int overlay) {
            this.text = text;
            this.textColor = textColor;
            this.overlay = overlay;
        }
    }
}
