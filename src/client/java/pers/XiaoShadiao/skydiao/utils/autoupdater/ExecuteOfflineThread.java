package pers.XiaoShadiao.skydiao.utils.autoupdater;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import pers.XiaoShadiao.skydiao.SkyDiaoModClient;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.io.File;
import java.io.IOException;

public class ExecuteOfflineThread implements Runnable {
    private boolean executed = false;
    public void run() {
        try {
            if(executed) return;
            executed = true;
            ConfigManager.saveConfig();
            ToolList.getInstance().log.info("SkyDiao Mod version check: " + SkyDiaoModClient.VERSION + "->" + SkyDiaoModClient.getCurrentNewVersion());
            if(!SkyDiaoModClient.VERSION.equals(SkyDiaoModClient.getCurrentNewVersion())) {
                if(Util.getPlatform() == Util.OS.WINDOWS) {
                    ToolList.getInstance().log.info("启动更新程序...");
                    try {
                        Runtime.getRuntime().exec(new String[]{AutoUpdater.updaterEXE.getAbsolutePath(), AutoUpdater.getOldFile().getAbsolutePath(), AutoUpdater.getNewFile().getAbsolutePath(), new File(AutoUpdater.modsFolder, "skydiao-" + SkyDiaoModClient.getCurrentNewVersion() + ".jar").getAbsolutePath()});
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    ToolList.getInstance().log.info("当前系统不是Windows, 无法启动自动更新");
                }
            } else {
                ToolList.getInstance().log.info("SkyDiao 当前已是最新版 ovo");
            }
        } catch (Exception e) {
            ToolList.getInstance().log.catching(e);
            executed = false;
        }
    }

    public void run(Minecraft mc) {
        run();
    }
}
