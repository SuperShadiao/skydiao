package pers.XiaoShadiao.skydiao.eventbuslistener.quicknav;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Lazy;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.config.QuickNavConfig;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;
import pers.XiaoShadiao.skydiao.utils.screen.QuickNavButton;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 在打开容器界面时，在界面上方/下方添加类似创造模式物品栏的快捷导航选项卡。
 * 顺序与默认图标参考 Skyblocker 的 Quick Navigation。
 */
public class QuickNavListener extends AbstractListener {

    private static final Logger log = LogManager.getLogger();

    private static volatile List<QuickNavConfig.Entry> cachedEntries;

    @Override
    public String getListenerName() {
        return "QuickNav";
    }

    private Screen currentScreen;

    @Override
    public void registerListeners() {
        ScreenEvents.AFTER_INIT.register(this::onScreenInit);
        CustomFabricEvents.AFTER_BACKGROUND_RENDER.register(this::afterBackgroundRender);
    }

    private void afterBackgroundRender(Screen screen, GuiGraphicsExtractor graphicsExtractor, int x, int y, float a) {
        // 与创造模式物品栏一致：未选中选项卡在容器贴图之前，选中选项卡在主要内容之后
        if (currentScreen == screen) {
            List<AbstractWidget> buttons = Screens.getWidgets(screen);
            for (AbstractWidget button : buttons) {
                if (button instanceof QuickNavButton quickNavButton && !quickNavButton.isActive0()) {
                    quickNavButton.renderTab(graphicsExtractor);
                }
            }
        }
    }

    private void onScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return;
        }
        if (!ConfigManager.quickNav.getValue() || !ToolList.isInSkyblock()) {
            return;
        }
        if (client.player == null || client.player.isCreative()) {
            return;
        }

        String title = screen.getTitle().getString().trim();
        List<QuickNavConfig.Entry> entries = entries();
        int count = Math.min(entries.size(), 14);
        List<QuickNavButton> buttons = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            QuickNavConfig.Entry entry = entries.get(i);
            boolean selected = entry.titleRegex != null && title.matches(entry.titleRegex);
            Lazy<ItemStack> icon = Lazy.lazy(() -> createIcon(entry));
            buttons.add(new QuickNavButton(container, i, selected, entry.command, icon.get(),
                    entry.tooltip, entry.doubleClick));
        }
        Screens.getWidgets(screen).addAll(buttons);

        currentScreen = screen;
        ScreenEvents.afterForeground(screen).register((s, graphics, mouseX, mouseY, delta) -> {
            for (QuickNavButton button : buttons) {
                if (button.isActive0()) {
                    button.renderTab(graphics);
                }
            }
        });
    }

    public static void reloadEntries() {
        cachedEntries = null;
    }

    private static List<QuickNavConfig.Entry> entries() {
        List<QuickNavConfig.Entry> current = cachedEntries;
        if (current == null) {
            current = QuickNavConfig.load().buttons;
            cachedEntries = current;
        }
        return current;
    }

    public static ItemStack createIcon(QuickNavConfig.Entry entry) {
        String icon = entry.icon == null ? "" : entry.icon.trim();
        if (icon.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (icon.startsWith("skyblock:")) {
            NeuItemRepository.INSTANCE.ensureLoaded();
            return NeuItemRepository.INSTANCE.createItemStack(icon);
        }
        if (icon.startsWith("head:")) {
            return head(icon.substring("head:".length()));
        }
        return parseIconItem(icon, entry.components);
    }

    private static ItemStack parseIconItem(String icon, String components) {
        if (ToolList.mc.level == null) {
            return new ItemStack(Items.BARRIER);
        }
        String componentPart = components == null ? "" : components.trim();
        if (!componentPart.isEmpty() && !componentPart.startsWith("[")) {
            componentPart = "[" + componentPart + "]";
        }
        String itemString = icon + componentPart;
        try {
            ItemParser parser = new ItemParser(ToolList.mc.level.registryAccess());
            ItemInput input = parser.parse(new StringReader(itemString));
            return input.createItemStack(1);
        } catch (Exception e) {
            log.debug("解析 QuickNav 图标失败: {}", itemString, e);
            return new ItemStack(Items.BARRIER);
        }
    }

    private static ItemStack head(String texture) {
        Multimap<String, Property> properties = ArrayListMultimap.create();
        properties.put("textures", new Property("textures", texture));
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)), "",
                new PropertyMap(properties));
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return stack;
    }
}
