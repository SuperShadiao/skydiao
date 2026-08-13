package pers.XiaoShadiao.skydiao.utils.autoupdater;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.XiaoShadiao.skydiao.SkyDiaoModClient;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import java.util.zip.ZipInputStream;

public class AutoUpdater {

    public static final String FILE_VERSION = "26_1_2";

    public static final AutoUpdater emptyInstance = new AutoUpdater(new String[0], SkyDiaoModClient.VERSION, false);
    public static final File modsFolder;

    static {
//        File modsFolder1;
//        modsFolder1 = new File(ToolList.mc.gameDirectory, "mods");
//        File f = new File(modsFolder1, SharedConstants.getCurrentVersion().name());
//        if(f.exists()) modsFolder1 = f;
//        modsFolder = modsFolder1;
        modsFolder = new File(ToolList.mc.gameDirectory, "mods");
    }

    private static CountDownLatch locker = new CountDownLatch(1);

    private static boolean passedUpdateTip;
    public static final File updaterEXE = new File(ToolList.mc.gameDirectory, "xsdhhup.exe");
    private static File oldFile,newFile;
    private static int updateCountGithub = 0;
    private static Logger log = LogManager.getLogger("XSD AutoUpdater");

    private static AutoUpdater hh;
    private static FutureTask<AutoUpdater> ft;

    public final String URL[], newVer;
    public final boolean downlanded;

    public static AutoUpdater getInstance() {
        return hh;
    }

    public static void unlock() {
        locker.countDown();
    }

    public static AutoUpdater checkUpdate() {
        boolean updateFTRequired = false;
        try {
            if(ft == null) {
                updateFTRequired = true;
            }
            if(ft != null) {
               try {
                   hh = ft.get(10, TimeUnit.MILLISECONDS);
               } catch (InterruptedException | TimeoutException e) {}
            }
        } catch (ExecutionException e) {
            updateFTRequired = true;
        }
        if(updateFTRequired) {
            ft = new FutureTask<>(AutoUpdater::checkUpdate2);
            new Thread(ft,"XSD AutoUpdater").start();
        }

        if(hh == null) {
            try {
                hh = ft.get(30, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.error("获取更新失败, 但是线程仍在继续!");
                e.printStackTrace();
                hh = null;
            }
        }

        return hh;
    }

    public static void flagPassedUpdateTip() {
        passedUpdateTip = true;
    }

    public static boolean hasPassedUpdateTip() {
        return passedUpdateTip;
    }

    private static AutoUpdater checkUpdate2() {
        log.info("awa");
        log.info("让我康康有没有更新可以用!");

        AutoUpdater up2;
        up2 = updateWithJson();
        log.info("Github -> " + up2);

        return up2;
    }

    private AutoUpdater(String[] url, String newVer, boolean downlanded) {
        this.URL = url;
        this.newVer = newVer;
        this.downlanded = downlanded;
    }

    private static AutoUpdater updateWithJson() {
        AutoUpdater up = null;
        JsonObject jo = null;
        try {
            String[] urls = {
                    "https://www.gitlink.org.cn/api/SuperShadiao/hypixelhelper/raw/skydiao_" + FILE_VERSION + "_update.json?ref=main",
                    "https://xiaoshadiao.club/skydiao_" + FILE_VERSION + "_update.json",
                    "https://github.com/SuperShadiao/hypixelhelper/raw/main/skydiao_" + FILE_VERSION + "_update.json"};
            for(String url : urls) {
                log.info("尝试从GITHUB获取: " + url);
                try(InputStream is = ToolList.getInstance().makeReqToURL(url)) {
                    String s = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    log.info("搜索到: " + s);
                    jo = JsonParser.parseString(s).getAsJsonObject();

                    up = new AutoUpdater(
                            StreamSupport.stream(jo.get("ul").getAsJsonArray().spliterator(), false).map(JsonElement::getAsString).toArray(String[]::new),
                            jo.get("v").getAsString(),
                            false
                    );
                    log.info(up);
                    if(jo != null && !SkyDiaoModClient.VERSION.equals(jo.get("v").getAsString())) {
                        up = download(up);
                    } else {
                        log.info("似乎没有更新可用!");
                        up = new AutoUpdater(up.URL,up.newVer,true);
                    };

                    handleOtherData(jo);

                    return up;
                } catch(Exception e) {
                    e.printStackTrace();
                    if(url.equals(urls[urls.length - 1])) throw new RuntimeException("All of the link occured errors while connecting");
                    continue;
                }
            }

        } catch (Exception e) {
            log.error("(检测)更新失败了! 八嘎呀路");
            e.printStackTrace();
            if(updateCountGithub < 5) {
                log.warn("重新尝试检测更新");
                updateCountGithub++;
                up = updateWithJson();
            } else log.error("(检测)更新反复出错, 请检查网络连接!");
        }
        return up;
    }

    private static void handleOtherData(JsonObject jo) {
        while(true) {
            try {
                locker.await();
                break;
            } catch (InterruptedException _) {}
        }
        try { AbstractListener.basicListener.setSPMVersion(jo.get("spmv").getAsString()); } catch (Exception _) {}
        try { AbstractListener.betterAFKPlaceListener.setXiaoShadiaoNeedMoreSocialXP(jo.get("xiaoshadiaoWantMoreSocialXP").getAsBoolean()); } catch (Exception _) {}
        try { AbstractListener.hiddenSomething.setCurrentAvailableGifVersion(jo.get("gifv").getAsInt()); } catch (Exception _) {}
    }

    private static AutoUpdater download(AutoUpdater up) {
        for(String s1 : up.URL) {
            try(InputStream is = ToolList.getInstance().makeReqToURL(s1, true)) {
//
                a:{
                    Exception eee = null;
                    for(String url : new String[] {"https://www.gitlink.org.cn/api/SuperShadiao/hypixelhelper/raw/xsdhhup.exe?ref=main", "https://xiaoshadiao.club/xsdhhup.exe"}) {
                        try {
                            FileUtils.writeByteArrayToFile(updaterEXE, ToolList.getInstance().downloadFileWithMD5(url, "14fc0c3b8705bb0ab60505fbe70dc643"));
                            log.info("更新器下载完成!");
                            break a;
                        } catch (Exception e) {
                            eee = e;
                        }
                    }

                    throw new RuntimeException("Updater download failed!", eee);
                }

                newFile = new File(ToolList.mc.gameDirectory.getAbsolutePath(), "skydiao-" + up.newVer + ".jar");
                log.info("新文件 -> " + newFile.getAbsolutePath());

                byte[] bytes = is.readAllBytes();

                FileUtils.writeByteArrayToFile(newFile, bytes);
                FileInputStream fis;
                ZipInputStream zis = new ZipInputStream(fis = new FileInputStream(newFile));
                a:try {
                    if(zis.getNextEntry() != null) {
                        break a;
                    }
                    throw new RuntimeException("The new Mod jar is broken!?");
                } catch (Exception e) {
                    throw new RuntimeException("The new Mod jar is broken!?", e);
                } finally {
                    try { fis.close(); } catch(Exception ignored) {}
                    try { zis.close(); } catch(Exception ignored) {}
                }

                oldFile = new File(modsFolder, "skydiao-" + SkyDiaoModClient.VERSION + ".jar");
                if(!oldFile.exists()) {
                    String string = URLDecoder.decode(AutoUpdater.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF-8");
                    log.info(string);
                    File oldFile1 = oldFile;
                    Matcher matcher = Pattern.compile("([^\\/]+\\.jar)").matcher(string);
                    if(matcher.find()) oldFile1 = new File(modsFolder, matcher.group());
                    log.info(oldFile1);
                    if(oldFile1.exists()) {
                        oldFile = oldFile1;
                    } else {
                        log.warn("无法定位到旧文件! 请尝试手动更新文件!");
                    }
                }
                log.info("旧文件 -> " + oldFile.getAbsolutePath());
                SkyDiaoModClient.setNewestVersion(up.newVer);

                return new AutoUpdater(up.URL,up.newVer,true);
            } catch(Exception e) {
                e.printStackTrace();
                if(s1.equals(up.URL[up.URL.length - 1])) throw new RuntimeException("Download failed", e);
            }
        }

        return up;
    }

    public static File getOldFile() {
        return oldFile;
    }

    public static File getNewFile() {
        return newFile;
    }

    public String toString() {
        return "URLs: [" + StringUtils.join(URL," | ") + "], ver: " + newVer + ", downloaded:" + downlanded;
    }
}