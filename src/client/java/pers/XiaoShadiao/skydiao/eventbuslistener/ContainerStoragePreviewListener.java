package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.screen.ContainerStoragePreviewScreen;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.storage.ContainerStorageCache;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 持续捕获当前打开的末影箱/背包内容，并定期落盘。
 *
 * <p>同时把 Hypixel 的 Storage/Ender Chest/Backpack 容器界面替换成存储预览界面，
 * 参考 Firmament 的 StorageOverlay。
 */
public class ContainerStoragePreviewListener extends AbstractListener {

    private static final Pattern PROFILE_ID = Pattern.compile(
            "(?i)Profile ID:\\s*([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");

    @Override
    public String getListenerName() {
        return "ContainerStoragePreview";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onMessage);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onMessage);
        CustomFabricEvents.REPLACE_SCREEN.register(this::replaceScreen);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ContainerStorageCache.INSTANCE.captureCurrentScreen();
            ContainerStorageCache.INSTANCE.flushIfDirty();
        });
    }

    /** 把 Hypixel 自带的存储容器替换成预览界面，但保留真实菜单以支持直接操作物品。 */
    private Screen replaceScreen(Screen screen) {
        if (!ConfigManager.betterStorage.getValue()) {
            return null;
        }
        if (!(screen instanceof ContainerScreen container) || ToolList.mc.player == null) {
            return null;
        }
        String title = ToolList.getInstance().deleteColorCode(container.getTitle().getString()).trim();
        if (!ContainerStorageCache.isStorageOverview(title) && ContainerStorageCache.parsePage(title) == null) {
            return null;
        }
        return new ContainerStoragePreviewScreen(container.getMenu(), container.getTitle());
    }

    private void onMessage(Component message, boolean overlay) {
        if (overlay) {
            return;
        }
        String text = ToolList.getInstance().deleteColorCode(message.getString());
        Matcher matcher = PROFILE_ID.matcher(text);
        if (matcher.find()) {
            ContainerStorageCache.INSTANCE.setProfileId(matcher.group(1));
        }
    }
}
