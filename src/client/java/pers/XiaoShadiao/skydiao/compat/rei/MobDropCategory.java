package pers.XiaoShadiao.skydiao.compat.rei;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;
import pers.XiaoShadiao.skydiao.utils.text.LegacyText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * REI “Mob Drops” 分类。
 *
 * <p>左侧是怪物模型，右侧是紧凑的掉落物网格。
 * 等级、经验、掉落率等信息只放在 tooltip 中，不再占用卡片宽度。
 */
public class MobDropCategory implements DisplayCategory<MobDropDisplay> {

    private static final int WIDTH = 160;
    private static final int HEIGHT = 134;
    private static final int GRID_COLUMNS = 5;
    private static final int GRID_ROWS = 6;
    private static final int CELL_SIZE = 18;
    private static final int ROW_HEIGHT = 19;
    private static final ItemStack ICON = new ItemStack(Items.ZOMBIE_HEAD);

    @Override
    public CategoryIdentifier<? extends MobDropDisplay> getCategoryIdentifier() {
        return CategoryIdentifier.of(MobDropDisplay.CATEGORY_ID);
    }

    @Override
    public Component getTitle() {
        return Component.literal("Mob Drops");
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(ICON);
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public int getDisplayWidth(MobDropDisplay display) {
        return WIDTH;
    }

    @Override
    public List<Widget> setupDisplay(MobDropDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));

        NeuItemRepository.MobDropRecipe mobDrop = display.mobDrop();
        Rectangle previewBounds = new Rectangle(bounds.x + 6, bounds.y + 16, 46, 72);
        MobPreviewWidget preview = new MobPreviewWidget(mobDrop, previewBounds);
        List<Component> previewTooltip = previewTooltip(mobDrop);
        widgets.add(previewTooltip.isEmpty() ? preview : Widgets.withTooltip(preview, previewTooltip));

        widgets.add(Widgets.createLabel(
                new Point(bounds.x + 56, bounds.y + 4),
                Component.literal(shortName(mobDrop.name(), 96)).withStyle(ChatFormatting.AQUA)
        ));

        int gridX = bounds.x + 56;
        int gridY = bounds.y + 16;
        List<NeuItemRepository.MobDrop> drops = mobDrop.drops();
        for (int i = 0; i < drops.size() && i < GRID_COLUMNS * GRID_ROWS; i++) {
            NeuItemRepository.MobDrop drop = drops.get(i);
            int column = i % GRID_COLUMNS;
            int row = i / GRID_COLUMNS;
            int x = gridX + column * CELL_SIZE;
            int y = gridY + row * ROW_HEIGHT;

            ItemStack stack = MobLootStacks.dropStack(drop, true);
            if (!stack.isEmpty()) {
                Slot slot = Widgets.createSlot(new Point(x, y))
                        .backgroundEnabled(true)
                        .entry(EntryStacks.of(stack));
                widgets.add(slot);
            }
        }

        if (drops.isEmpty()) {
            widgets.add(Widgets.createLabel(
                    new Point(gridX, gridY + 20),
                    Component.literal("No drops").withStyle(ChatFormatting.GRAY)
            ));
        }

        return widgets;
    }

    private static List<Component> previewTooltip(NeuItemRepository.MobDropRecipe mobDrop) {
        List<Component> lines = new ArrayList<>();
        lines.add(LegacyText.parse(mobDrop.name()));
        if (mobDrop.level() > 0) {
            lines.add(Component.literal("Level " + mobDrop.level()).withStyle(ChatFormatting.GRAY));
        }
        if (mobDrop.coins() > 0) {
            lines.add(Component.literal("Coins: " + mobDrop.coins()).withStyle(ChatFormatting.GOLD));
        }
        for (Map.Entry<String, Integer> entry : mobDrop.experience().entrySet()) {
            lines.add(Component.literal(experienceName(entry.getKey()) + ": +" + entry.getValue())
                    .withStyle(ChatFormatting.AQUA));
        }
        for (String extra : mobDrop.extra()) {
            lines.add(LegacyText.parse(extra));
        }
        return lines;
    }

    private static String experienceName(String key) {
        return switch (key) {
            case "xp" -> "Enchanting";
            case "combat_xp" -> "Combat";
            case "farming_xp" -> "Farming";
            case "fishing_xp" -> "Fishing";
            case "hunting_xp" -> "Hunting";
            case "mining_xp" -> "Mining";
            case "alchemy_xp" -> "Alchemy";
            case "taming_xp" -> "Taming";
            case "foraging_xp" -> "Foraging";
            case "carpentry_xp" -> "Carpentry";
            case "runecrafting_xp" -> "Runecrafting";
            case "enchanting_xp" -> "Enchanting";
            default -> {
                String value = key.endsWith("_xp") ? key.substring(0, key.length() - 3) : key;
                value = value.replace('_', ' ');
                yield value.isEmpty()
                        ? key
                        : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
            }
        };
    }

    private static String shortName(String name, int maxWidth) {
        String stripped = name == null ? "" : name.replaceAll("§.", "");
        String result = Minecraft.getInstance().font.plainSubstrByWidth(stripped, maxWidth);
        return result.isEmpty() ? "" : result + (result.length() < stripped.length() ? "..." : "");
    }
}
