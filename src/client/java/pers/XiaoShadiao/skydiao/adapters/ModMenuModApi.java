package pers.XiaoShadiao.skydiao.adapters;

import com.terraformersmc.modmenu.api.*;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.XiaoShadiao.skydiao.SkyDiaoModClient;
import pers.XiaoShadiao.skydiao.screen.ConfigScreen;
import pers.XiaoShadiao.skydiao.utils.AutoUpdater;

import java.util.Map;
import java.util.function.Consumer;

public class ModMenuModApi implements ModMenuApi {

    private AutoUpdater updater;

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }

    @Override
    public UpdateChecker getUpdateChecker() {
        return () -> new UpdateInfo() {

            @Override
            public @NotNull Component getUpdateMessage() {
                return u().downlanded ? Component.literal("§a已下载最新版本, 重启后自动更新") : Component.literal("§c已检查到最新版本, 但未能下载成功, 你可以手动尝试下载");
            }

            @Override
            public boolean isUpdateAvailable() {
                return !SkyDiaoModClient.VERSION.equals(u().newVer);
            }

            @Override
            public String getDownloadLink() {
                return "https://5ixsd.top/skydiao";
            }

            @Override
            public UpdateChannel getUpdateChannel() {
                return UpdateChannel.RELEASE;
            }

        };

    }

    @Override
    public Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        return ModMenuApi.super.getProvidedConfigScreenFactories();
    }

    @Override
    public Map<String, UpdateChecker> getProvidedUpdateCheckers() {
        return ModMenuApi.super.getProvidedUpdateCheckers();
    }

    @Override
    public void attachModpackBadges(Consumer<String> consumer) {
        ModMenuApi.super.attachModpackBadges(consumer);
    }

    private Thread thread;

    private AutoUpdater u() {
        if(thread == null) {
            thread = new Thread(() -> {
                do {
                    updater = AutoUpdater.checkUpdate();
                } while(updater == null);
            });
            thread.start();
        }
        return updater == null ? AutoUpdater.emptyInstance : updater;
    }
}
