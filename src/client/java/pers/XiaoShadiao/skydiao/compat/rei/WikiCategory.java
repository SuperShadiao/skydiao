package pers.XiaoShadiao.skydiao.compat.rei;

import com.mojang.blaze3d.Blaze3D;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.api.client.gui.widgets.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * “Wiki”分类：卡片中心放一个打开 Wiki 的按钮。
 */
public class WikiCategory implements DisplayCategory<WikiDisplay> {

    private static final ItemStack ICON = new ItemStack(Items.BOOK);

    @Override
    public CategoryIdentifier<? extends WikiDisplay> getCategoryIdentifier() {
        return CategoryIdentifier.of(WikiDisplay.CATEGORY_ID);
    }

    @Override
    public Component getTitle() {
        return Component.literal("Wiki");
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(ICON);
    }

    @Override
    public int getDisplayHeight() {
        return 82;
    }

    @Override
    public int getDisplayWidth(WikiDisplay display) {
        return 160;
    }

    @Override
    public List<Widget> setupDisplay(WikiDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));

        widgets.add(Widgets.createSlot(new Point(bounds.getCenterX() - 9, bounds.getY() + 8))
                .backgroundEnabled(true)
                .entry(EntryStacks.of(display.stack())));

        String url = display.url();
        Button button = Widgets.createButton(
                new Rectangle(bounds.getCenterX() - 50, bounds.getY() + 38, 100, 20),
                url == null ? Component.literal("无 Wiki 页面") : Component.literal("打开 Wiki"));
        button.setEnabled(url != null);
        button.setOnClick(b -> {
            if (url != null) {
                Blaze3D.openUri(URI.create(url));
            }
        });
        widgets.add(button);

        return widgets;
    }
}
