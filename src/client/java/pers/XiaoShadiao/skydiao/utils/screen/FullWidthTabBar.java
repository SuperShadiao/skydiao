package pers.XiaoShadiao.skydiao.utils.screen;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class FullWidthTabBar extends MenuTabBar {
    public FullWidthTabBar(int x, int y, int width, int height, TabManager tabManager, ImmutableList<TabButton> tabButtons, ImmutableList<Tab> tabs) {
        super(x, y, width, height, tabManager, tabButtons, tabs);
    }

    @Override
    public void arrangeElements(final int width) {
        this.width = width;
        int tabsWidth = width - 28;
        int tabWidth = Mth.roundToward(tabsWidth / this.tabs.size(), 2);

        for (TabButton button : this.tabButtons) {
            button.setWidth(tabWidth);
        }

        this.layout.arrangeElements();
        this.layout.setX(Mth.roundToward((width - tabsWidth) / 2, 2));
        this.layout.setY(0);
    }

    public static FullWidthTabBar.Builder builder2(final TabManager tabManager, final int width) {
        return new FullWidthTabBar.Builder(tabManager, width);
    }

    public static class Builder extends TabNavigationBar.Builder {
        private Builder(final TabManager tabManager, final int width) {
            super(tabManager, 0, 0, width, 24);
        }

        public FullWidthTabBar.Builder addTab(final Tab tab) {
            super.addTab(new MenuTabBar.MenuTabButton(this.tabManager, tab, 0, this.height), tab);
            return this;
        }

        public FullWidthTabBar.Builder addTabs(final Tab... tabs) {
            for (Tab tab : tabs) {
                this.addTab(tab);
            }

            return this;
        }

        public @NotNull FullWidthTabBar build() {
            return new FullWidthTabBar(this.x, this.y, this.width, this.height, this.tabManager, ImmutableList.copyOf(this.tabButtons), ImmutableList.copyOf(this.tabs));
        }
    }

}
