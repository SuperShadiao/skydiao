package pers.XiaoShadiao.skydiao;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipInputStream;

public class SkyDiaoPreLaunch implements PreLaunchEntrypoint {

    public static final Logger log = LogManager.getLogger();

    public static final File libFolder = new File(FabricLoader.getInstance().getGameDir().toFile(), "xsdlib");
    public static final File libPrepareFolder = new File(libFolder, "1");
    public static final File libLoadFolder = new File(libFolder, "2");

    private static SkyDiaoPreLaunch instance;

    public static SkyDiaoPreLaunch getInstance() {
        if(instance == null) throw new AssertionError("错误的调用时机");
        return instance;
    }

    public Runnable initJars = null;

    public void execInitJars() {
        if(initJars == null) throw new AssertionError("不应该发生的错误, initJars 未初始化");
        initJars.run();
    }

    public static class Lib {
        public final String name;
        public final String url;
        public final String md5;
        public final File loadFile;
        public final File prepareFile;
        public Lib(String name, String url, String md5) {
            this.name = name;
            this.url = url;
            this.md5 = md5;
            this.loadFile = new File(libLoadFolder, name);
            this.prepareFile = new File(libPrepareFolder, name);
        }
        public File getPrepareFile() {
            return prepareFile;
        }
        public File getLoadFile() {
            return loadFile;
        }
        public CompletableFuture<Throwable> download() {
            return CompletableFuture.supplyAsync(() -> {
                int tries = 0;
                Throwable lastError = null;
                while(tries++ < 3) {
                    try {
                        log.info("Downloading lib " + name);
                        FileUtils.copyURLToFile(new URL(url), getPrepareFile());
                        verifyOrThrow(getPrepareFile());
                        break;
                    } catch (Throwable e) {
                        log.error("Failed to download lib " + name);
                        log.catching(e);
                        lastError = e;
                        getPrepareFile().delete();
                    }
                }
                return lastError;
            });// .thenApply(a -> getPrepareFile().exists());
        }
        public CompletableFuture<Throwable> downloadSkipIfExist() {
            if(isPrepareExist()) {
                return CompletableFuture.completedFuture(null);
            } else return download();
        }
        public boolean isLoadExist() {;
            return getLoadFile().exists() && verifyWithException(getLoadFile()) == null;
        }
        public boolean isPrepareExist() {
            return getPrepareFile().exists() && verifyWithException(getPrepareFile()) == null;
        }
        private void verify(File file) throws Exception {
            ZipInputStream zis = new ZipInputStream(Files.newInputStream(file.toPath()));
            while (zis.getNextEntry() != null) {}

            if(!getMD5(file).equals(md5)) {
                throw new RuntimeException("MD5校验失败");
            }
        }
        private Throwable verifyWithException(File file) {
            try {
                verify(file);
                return null;
            } catch (Exception e) {
                return e;
            }
        }
        private void verifyOrThrow(File file) {
            Throwable e = verifyWithException(file);
            if(e != null) {
                throw new RuntimeException(e);
            }
        }
        private String getMD5(File file) {
            try {
                return getMD5(Files.newInputStream(file.toPath()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private String getMD5(InputStream inputStream) {
            try {
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                byte[] buffer = new byte[8192]; // 8KB缓冲区
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    md5.update(buffer, 0, bytesRead); // 分块更新摘要
                }

                byte[] digest = md5.digest();
                return byteToHex(digest);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private String byteToHex(byte[] bytes) {
            StringBuilder hexString = new StringBuilder(32); // 预分配长度
            for (byte b : bytes) {
                int i = b & 0xFF;
                if (i < 16) {
                    hexString.append('0');
                }
                hexString.append(Integer.toHexString(i));
            }
            return hexString.toString();
        }
    }

    public static final List<Lib> libs = List.of(
            new Lib("mrxiaom-bilibili-open-live-1.0.4.jar", "https://xiaoshadiao.club/3rd_lib/mrxiaom-bilibili-open-live-1.0.4.jar", "a8f72cfa9754d267f0b47703a96d519e"),
            new Lib("jlayer-1.0.1.jar", "https://xiaoshadiao.club/3rd_lib/jlayer-1.0.1.jar", "601db87031f231fbf7a61f9d8384dde6"),
            new Lib("Java-WebSocket-1.5.6.jar", "https://xiaoshadiao.club/3rd_lib/Java-WebSocket-1.5.6.jar", "0c663cf078f779f5d03fa4d02e1647e3"),
            new Lib("blivemode.jar", "https://xiaoshadiao.club/3rd_lib/blivemode.jar", "3f40b7f8d8c4a14da4bc4171ec3b300f")
    );

    @Override
    public void onPreLaunch() {
        instance = this;
        ClassLoader classLoader = this.getClass().getClassLoader();
        if(!(classLoader instanceof URLClassLoader)) classLoader = classLoader.getParent();
        if(classLoader instanceof URLClassLoader urlClassLoader) {
            moveOldLibToNewLib();
            initJars = () -> {
                for (Lib lib : libs) {
                    try {
                        File loadFile = lib.getLoadFile();
                        if(loadFile.exists() && !lib.isLoadExist()) {
                            loadFile.delete();
                            throw new RuntimeException("lib文件" + lib.name + "已损坏, 请重新启动客户端。");
                        }
                        log.info(loadFile);
                        addURL(urlClassLoader, loadFile.toURI().toURL());
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                }
                log.info("成功添加" + libs.size() + "个jar文件到" + urlClassLoader);
            };
        } else {
            throw new AssertionError("Can't lookup the URLClassLoader");
        }
    }

    private void addURL(URLClassLoader urlClassLoader, URL url) {
        try {
            Method method = urlClassLoader.getClass().getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(urlClassLoader, url);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private void moveOldLibToNewLib() {
        List<CompletableFuture<Throwable>> downloadFutures = new ArrayList<>();
        for (Lib lib : libs) {
            if(lib.isPrepareExist()) {
                downloadFutures.add(CompletableFuture.completedFuture(null).thenCompose(o -> {
                    Throwable error = null;
                    try {
                        moveFile(lib);
                    } catch (IOException e) {
                        error = e;
                    }
                    return CompletableFuture.completedFuture(error);
                }));
            } else if(!lib.isLoadExist()) {
                CompletableFuture<Throwable> download = lib.download().thenCompose(a -> {
                    if(a == null) {
                        try {
                            moveFile(lib);
                            return CompletableFuture.completedFuture(null);
                        } catch (IOException e) {
                            return CompletableFuture.completedFuture(e);
                        }
                    }
                    return CompletableFuture.completedFuture(a);
                });
                downloadFutures.add(download);
            }
        }
        try {
            for (CompletableFuture<Throwable> downloadFuture : downloadFutures) {
                Throwable throwable = downloadFuture.get();
                if (throwable != null) {
                    throw new RuntimeException("初次使用SkyDiao，lib库文件下载失败，小沙雕感到非常生气。请检查网络状态并尝试重启游戏！", throwable);
                }
            }
        } catch (Throwable e) {
            RuntimeException runtimeException = new RuntimeException("初次使用SkyDiao，lib库文件下载失败，小沙雕感到非常生气。请检查网络状态并尝试重启游戏！", e);
            log.catching(runtimeException);
            throw runtimeException;
        }
    }

    private void moveFile(Lib lib) throws IOException {
        File prepare = lib.getPrepareFile();
        File load = lib.getLoadFile();

        if(prepare.exists() && !lib.isPrepareExist()) {
            throw new IOException("lib文件" + lib.name + "已损坏, 请重新启动客户端。");
        }
        FileUtils.copyFile(prepare, load);
        prepare.delete();
    }

}
