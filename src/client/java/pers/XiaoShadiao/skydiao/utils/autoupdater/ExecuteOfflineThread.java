package pers.XiaoShadiao.skydiao.utils.autoupdater;

import pers.XiaoShadiao.skydiao.SkyDiaoModClient;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

import java.io.File;
import java.io.IOException;

public class ExecuteOfflineThread implements Runnable {
    public void run() {
        ConfigManager.saveConfig();
        System.out.println("SkyDiao Mod version check: " + SkyDiaoModClient.VERSION + "->" + SkyDiaoModClient.getCurrentNewVersion());
        if(!SkyDiaoModClient.VERSION.equals(SkyDiaoModClient.getCurrentNewVersion())) {
            System.out.println("启动更新程序...");
            try {
                Runtime.getRuntime().exec(new String[] {AutoUpdater.updaterEXE.getAbsolutePath(), AutoUpdater.getOldFile().getAbsolutePath(), AutoUpdater.getNewFile().getAbsolutePath(), new File(AutoUpdater.modsFolder, "skydiao-" + SkyDiaoModClient.getCurrentNewVersion() + ".jar").getAbsolutePath()});
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("SkyDiao 当前已是最新版 ovo");
        }
    }
}
