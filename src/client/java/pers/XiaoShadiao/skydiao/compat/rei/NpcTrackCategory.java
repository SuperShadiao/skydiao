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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * “NPC Location”分类：NPC + 坐标，右侧指南针作为可移动输出。
 */
public class NpcTrackCategory implements DisplayCategory<NpcTrackDisplay> {

    private static final ItemStack ICON = new ItemStack(Items.COMPASS);

    @Override
    public CategoryIdentifier<? extends NpcTrackDisplay> getCategoryIdentifier() {
        return CategoryIdentifier.of(NpcTrackDisplay.CATEGORY_ID);
    }

    @Override
    public Component getTitle() {
        return Component.literal("NPC Location");
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(ICON);
    }

    @Override
    public int getDisplayHeight() {
        return 64;
    }

    @Override
    public int getDisplayWidth(NpcTrackDisplay display) {
        return 170;
    }

    @Override
    public List<Widget> setupDisplay(NpcTrackDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));

        int y = bounds.getY() + bounds.getHeight() / 2 - 9;

        Slot input = Widgets.createSlot(new Point(bounds.getX() + 10, y))
                .backgroundEnabled(true)
                .entry(EntryStacks.of(display.npcStack()));
        input.markInput();
        widgets.add(input);

        widgets.add(Widgets.createArrow(new Point(bounds.getCenterX() - 14, y)));

        Slot output = Widgets.createSlot(new Point(bounds.getX() + bounds.getWidth() - 28, y))
                .backgroundEnabled(true)
                .entry(EntryStacks.of(display.trackStack()));
        output.markOutput();
        widgets.add(output);

        widgets.add(Widgets.createLabel(
                new Point(bounds.getCenterX(), bounds.getY() + bounds.getHeight() - 14),
                Component.literal(display.npc().locationText()).withStyle(ChatFormatting.GRAY)
        ));
        return widgets;
    }
}
