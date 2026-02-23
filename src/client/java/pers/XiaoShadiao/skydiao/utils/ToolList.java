package pers.XiaoShadiao.skydiao.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class ToolList {

    public static final Minecraft mc = Minecraft.getInstance();

    private static ToolList instance;

    private final boolean imXiaoShadiao = System.getenv().get("USERNAME").equals("小沙雕");
    public Random random = new Random();
    private DevelopmentEnvironmentDetector devDetectorInstance;
    public Logger log = LogManager.getLogger("XSD Utils");

    public static ToolList getInstance() {
        if(instance == null) instance = new ToolList();
        return instance;
    }

    public static void destroy() {
        instance = null;
    }

    public boolean isXiaoShadiao() {
        return imXiaoShadiao;
    }

    public InputStream makeReqToURL(String url) {
        return makeReqToURL(url, false, null, null);
    }

    public InputStream makeReqToURL(String url, boolean allowErrorStream) {
        return makeReqToURL(url, allowErrorStream, null, null);
    }

    public InputStream makeReqToURL(String url, boolean allowErrorStream, Consumer<URLConnection> ucin, Consumer<String> onRedirect) {
        return makeReqToURL(url, allowErrorStream, ucin, onRedirect, false);
    }

    public InputStream makeReqToURL(String url, boolean allowErrorStream, Consumer<URLConnection> ucin, Consumer<String> onRedirect, boolean disableSSL) {

        if(url.contains("hypixelhelper.pages.dev")) url = url.replace("hypixelhelper.pages.dev", "xiaoshadiao.club");

        if(url.contains("mojang")) disableSSL = true;

        if(!url.contains("hypixel") || isDevEnvironment()) log.info(url);
        HttpURLConnection uc = null;
        try {
            uc = (HttpURLConnection) new URL(url/*.replace("http://", "https://")*/).openConnection();
            uc.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:101.0) Gecko/20100101 Firefox/101.0");

            if(url.contains("https://www.gitlink.org.cn")) uc.addRequestProperty("Referer", "https://www.gitlink.org.cn/SuperShadiao/hypixelhelper");

            uc.setInstanceFollowRedirects(true);
            if(uc instanceof HttpsURLConnection) {
                if(disableSSL) {
                    ((HttpsURLConnection) uc).setSSLSocketFactory(HttpSSLDisabler.getTrustAll());
                    ((HttpsURLConnection) uc).setHostnameVerifier((h, s) -> true);
                } else {
                    ((HttpsURLConnection) uc).setSSLSocketFactory(HttpSSLDisabler.getDefault());
                }
            }
            uc.setConnectTimeout(30000);
            uc.setReadTimeout(30000);

            if(ucin != null) ucin.accept(uc);

            uc.connect();

            if (uc.getResponseCode() == 302) {
                log.info("网页重定向");
                String newUrl = uc.getHeaderField("Location");
                if(onRedirect != null) onRedirect.accept(newUrl);
                return makeReqToURL(newUrl, allowErrorStream, ucin, onRedirect);
            }
            return allowErrorStream && uc.getResponseCode() != 200 ? uc.getErrorStream() : uc.getInputStream();

        } catch(Exception e) {

            if(url.contains("//api.hypixel.net")) {
                try {
                    return makeReqToURL("https://xiaoshadiao.club/datagetter?url=" + URLEncoder.encode(url, "UTF-8"), allowErrorStream, ucin, onRedirect);
                } catch (UnsupportedEncodingException ex) {
                    throw new RuntimeException(ex);
                }
            }

//			else if(e.toString().contains("unable to find valid certification path to requested target")) {
//				log.warn("出现了非常逆天的问题: " + url + " -> " + e);
//				log.warn("使用外置程序请求网站!");
//				return 请求网站E(url);
//			}
            if(e instanceof javax.net.ssl.SSLException) {
                if (e.toString().contains("plaintext connection")) {
                    log.warn("出现了非常逆天的问题: " + url + " -> " + e);
                    log.warn("尝试强转http重试...");
                    return makeReqToURL(url.replace("https://", "http://"), allowErrorStream, ucin, onRedirect, true);
                }

                if(e instanceof javax.net.ssl.SSLHandshakeException && !disableSSL) {
                    log.warn("出现了非常逆天的问题: " + url + " -> " + e);
                    log.warn("尝试禁用SSL重试...");
                    return makeReqToURL(url, allowErrorStream, ucin, onRedirect, true);
                }
            }

            throw new RuntimeException("Exception in visiting \"" + url + "\" because " + e, e);
        }

    }

    public boolean stringHasContext(String str) {
        return str != null && !str.isEmpty();
    }

    public boolean isDevEnvironment() {

        if(devDetectorInstance == null) {

            if(true) {
                boolean temp = FabricLoader.getInstance().isDevelopmentEnvironment();
                if(temp) {
                    log.info("Develop环境");
                } else {
                    log.info("非Develop环境, 正式游戏");
                }
                return (devDetectorInstance = new DevelopmentEnvironmentDetector(temp)).isDevMode;
            }

            try {
                Minecraft.class.getDeclaredField("instance");
                log.info("Develop环境");
                devDetectorInstance = new DevelopmentEnvironmentDetector(true);
                return true;
            } catch (Exception e) {
                log.info("非Develop环境, 正式游戏");
                devDetectorInstance = new DevelopmentEnvironmentDetector(false);
            }
            return false;

        } else return devDetectorInstance.isDevMode;

    }

    //    public static class DevelopmentEnvironmentDetector {
    //
    //        public final boolean isDevMode;
    //
    //        public DevelopmentEnvironmentDetector(boolean b) {
    //            isDevMode = b;
    //        }
    //
    //    }
    public record DevelopmentEnvironmentDetector(boolean isDevMode) {}

    private static final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public static class ThreadedTask<R> {

        public final Future<R> future;
        public final Callable<R> callable;
        public final Runnable runnable;
        public final R value;

        public ThreadedTask(Future<R> f, Callable<R> c) {
            future = Objects.requireNonNull(f);
            callable = Objects.requireNonNull(c);
            runnable = null;
            value = null;
        }

        public ThreadedTask(Future<R> f, Runnable c, R v) {
            future = Objects.requireNonNull(f);
            callable = null;
            runnable = Objects.requireNonNull(c);
            value = v;
        }

        public Object getOriginalTask() {
            if(runnable != null) return runnable;
            if(callable != null) return callable;
            throw new IllegalStateException("R & C both null");
        }

        public void reRun(Collection<ThreadedTask<?>> ttl) {
            reRunWithDelayMS(ttl, 0);
        }

        public void reRunWithDelayMS(Collection<ThreadedTask<?>> ttl, long ms) {
            ttl.add(addThreadedTask(() -> {
                R v;
                if(ms > 0) try { Thread.sleep(ms); } catch (InterruptedException e) {};

                if(runnable != null) {
                    runnable.run();
                    v = value;
                } else if(callable != null) {
                    v = callable.call();
                } else {
                    throw new IllegalStateException("R & C both null");
                }
                return v;
            }));
        }

    }

    public static <R> ThreadedTask<R> addThreadedTask(Callable<R> c) {
        return new ThreadedTask<>(executor.submit(c), c);
    }

    public static <R> ThreadedTask<R> addThreadedTask(Runnable r, R obj) {
        return new ThreadedTask<>(executor.submit(r, obj), r, obj);
    }

    @SuppressWarnings("ConstantConditions")
    public static void printChatMessage(Component msg) {
        if(ToolList.mc != null && ToolList.mc.gui != null && ToolList.mc.gui.getChat() != null) {
            ToolList.mc.execute(() -> ToolList.mc.gui.getChat().addMessage(msg));
        }
    }

}
