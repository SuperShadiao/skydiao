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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 四类 Skyblock 配方共用的 REI 分类。
 */
public class SkyblockRecipeCategory implements DisplayCategory<SkyblockRecipeDisplay> {

    private final Identifier identifier;
    private final Component title;
    private final ItemStack icon;
    private final int height;

    public SkyblockRecipeCategory(Identifier identifier, Component title, ItemStack icon, int height) {
        this.identifier = identifier;
        this.title = title;
        this.icon = icon;
        this.height = height;
    }

    @Override
    public CategoryIdentifier<? extends SkyblockRecipeDisplay> getCategoryIdentifier() {
        return CategoryIdentifier.of(identifier);
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(icon);
    }

    @Override
    public int getDisplayHeight() {
        return height;
    }

    @Override
    public int getDisplayWidth(SkyblockRecipeDisplay display) {
        return 160;
    }

    @Override
    public List<Widget> setupDisplay(SkyblockRecipeDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));

        for (SkyblockRecipeDisplay.SlotSpec spec : display.slots(bounds.getWidth(), bounds.getHeight())) {
            Slot slot = Widgets.createSlot(new Point(bounds.getX() + spec.x(), bounds.getY() + spec.y()))
                    .backgroundEnabled(true)
                    .entry(EntryStacks.of(spec.stack()));
            if (spec.output()) {
                slot.markOutput();
            } else {
                slot.markInput();
            }
            widgets.add(slot);
        }

        widgets.add(Widgets.createArrow(new Point(
                bounds.getX() + display.arrowX(bounds.getWidth()),
                bounds.getY() + display.arrowY(bounds.getHeight())
        )));

        String duration = display.durationText();
        if (!duration.isEmpty()) {
            widgets.add(Widgets.createLabel(new Point(
                    bounds.getX() + display.arrowX(bounds.getWidth()) + 4,
                    bounds.getY() + display.arrowY(bounds.getHeight()) - 12
            ), Component.literal(duration)));
        }

        String extra = display.extraText();
        if (extra != null && !extra.isBlank()) {
            widgets.add(Widgets.createLabel(new Point(
                    bounds.getCenterX(),
                    bounds.getCenterY() + 24
            ), Component.literal(extra)));
        }

        return widgets;
    }
}
