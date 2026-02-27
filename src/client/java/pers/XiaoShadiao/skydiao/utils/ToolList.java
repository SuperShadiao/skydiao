package pers.XiaoShadiao.skydiao.utils;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class ToolList {

    public static final Minecraft mc;
    static {
         mc = Minecraft.getInstance();
         if(mc == null) throw new AssertionError("不允许在Minecraft实例启动前加载ToolList");
    }

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

    public static boolean isInSkyblock() {
        return "SkyBlock".equals(StatusManager.get().getType());
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

    public boolean verifyFileWithMD5(File file, String md5) {
        if (!file.exists()) return false;
        String realMD5 = getMD5(file);
        return realMD5.equals(md5);
    }

    public byte[] downloadFileWithMD5(String url, String md5) throws IOException {
        try(InputStream is = makeReqToURL(url, true)) {
            byte[] bytes = is.readAllBytes();

            String realMD5 = getMD5(bytes);
            if (realMD5.equals(md5)) {
                return bytes;

            } else {
                throw new IOException("MD5校验失败, Current: " + realMD5 + ", required: " + md5);
            }
        } catch (Exception e) {
            throw new IOException("无法下载文件", e);
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

    public String getMD5(File file) {
        try {
            return getMD5(Files.newInputStream(file.toPath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getMD5(InputStream inputStream) {
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

    public String getMD5(byte[] bl) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return byteToHex(md5.digest(bl));
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getMD5(String s, boolean utf8) {

        if(utf8) {
            return getMD5(s.getBytes(StandardCharsets.UTF_8));
        } else return getMD5(s.getBytes());

    }

    public String getMD5(String s) {
        return getMD5(s,true);
    }

    public String deleteColorCode(String string) {
        return string.replaceAll("§.", "");
    }

    public String getMidOfText(String target, String left, String right) {
        try {

            int j = 0,k = 0;
            boolean b = false;

            for(int i = 0; i < target.length() - (b ? right : left).length() + 1; i++) {
                if(target.startsWith(b ? right : left, i)) { //if(target.substring(i,i + (b ? right : left).length()).equals(b ? right : left)) {
                    if(!b) {
                        j = i + left.length();
                        i += left.length() - 1;
                        b = true;
                    } else {
                        k = i;
                        break;
                    }
                }
            }
            return k * j == 0 ? "" : target.substring(j,k);

        } catch(Exception e) {
            e.printStackTrace();
            return "";
        }
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

    public static void sendChatMessage(String msg) {
        if(mc.player != null) {
            if (msg.startsWith("/")) {
                mc.player.connection.sendCommand(msg.substring(1));
            } else {
                mc.player.connection.sendChat(msg);
            }
        }
    }

    public String encodeString(String message) {
        char key = (char) random.nextInt(0x10000);
        char key2 = (char) (key ^ 2888);
        char[] bl = message.toCharArray();
        for(int i = 0;i < bl.length;i++) {
            bl[i] = (char) (bl[i] ^ key ^ ((i * key2 + 100) % 0x10000));
        }

        String s = key + new String(bl) + key2;
        if(!decodeString(s).equals(message)) {
            throw new AssertionError("加密时出错");
        }
        return s;
    }

    public String decodeString(String message) {

        try {
            String decodeString = message;
            if(decodeString == null) return null;
            if(decodeString.isEmpty()) return "";
            char key1 = decodeString.substring(0,1).charAt(0);
            decodeString = decodeString.substring(1);
            char key2 = decodeString.substring(decodeString.length() - 1,decodeString.length()).charAt(0);
            // log.info(key1);

            if((key1 ^ key2) != 2888) return null;

            char[] newchars = new char[decodeString.length() - 1];
            char[] oldchars = decodeString.toCharArray();
            for(int i = 0;i < decodeString.length() - 1;i++) {
                newchars[i] = (char) (oldchars[i] ^ key1 ^ ((i * key2 + 100) % 0x10000));
            }

            return new String(newchars);
        } catch(Exception e) {
            return null;
        }

    }

}
