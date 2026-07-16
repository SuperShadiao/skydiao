package pers.XiaoShadiao.skydiao.eventbuslistener.bilibili;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import pers.XiaoShadiao.blive.XSDBLiveClient;
import pers.XiaoShadiao.skydiao.SkyDiaoModClient;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import top.mrxiaom.bili.live.client.BApi;
import top.mrxiaom.bili.live.client.BApiClient;
import top.mrxiaom.bili.live.runtime.utils.SignHolder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BLiveListener extends Thread {

    public XSDBLiveClient getClient() {
        return client;
    }

    private XSDBLiveClient client;

    private long aliveTime = System.currentTimeMillis();
    private long startTime = System.currentTimeMillis();
    private boolean reconnectFlag = false;

    public static boolean isListening() {
        return isListening;
    }

    private static int staticToken;
    private final int token;
    private static boolean isListening = false;

    public static BLiveListener getInstance() {
        if(instance != null && !instance.isAlive()) {
            isListening = false;
            instance = null;
        }
        return instance;
    }

    private static long userUID = -1;

    private static BLiveListener instance;
    public static void launch() {
        if(getInstance() == null) {
            instance = new BLiveListener();
        }
    }

    public static void stopListen() {
        isListening = false;
        instance.interrupt();
        instance = null;
    }

    public BLiveListener() {
        staticToken = token = ToolList.getInstance().random.nextInt();

        setName("B站直播间监听$" + token);
        keepAlive();
        start();
    }

    public static final String accessKey;

    public static final String accessSecret;

    public static final String appid;

    static {
        accessKey = "pEfsCp7X0Cd04wM6jIXlnLfg";
        accessSecret = "WlCFrGRESOdli3xGLMlWfBYJFRIYGE";
        appid = "3548397740970404";
        SignHolder.push(accessKey, accessSecret);
    }

    public void run() {

        String uname = null;
        String temp = null;
        try {
            String code = ConfigManager.blivelistenercode.getValue();
            String appId = appid; // "3548394682547600";

            String url = "https://live-open.biliapi.com/v2/app/start";
            HttpsURLConnection uc = (HttpsURLConnection) new URL(url).openConnection();
            uc.setReadTimeout(60000);
            uc.setConnectTimeout(10000);
            uc.setRequestMethod("POST");

            String data = "{\"code\":\"" + code + "\",\"app_id\":" + appId + "}";
            uc.setRequestProperty("Accept","application/json");
            uc.setRequestProperty("Content-Type","application/json");
            uc.setRequestProperty("x-bili-content-md5",ToolList.getInstance().getMD5(data));
            long l = System.currentTimeMillis() / 1000;
            uc.setRequestProperty("x-bili-timestamp",String.valueOf(l));
            uc.setRequestProperty("x-bili-signature-method","HMAC-SHA256");
            String randKey = "XSD_" + Math.abs(new Random().nextLong()) + "_xiaoshadiao.club";
            uc.setRequestProperty("x-bili-signature-nonce", randKey);
            uc.setRequestProperty("x-bili-accesskeyid","pEfsCp7X0Cd04wM6jIXlnLfg");
            uc.setRequestProperty("x-bili-signature-version","1.0");


            StringBuilder sb = new StringBuilder();
            sb.append("x-bili-accesskeyid:").append("pEfsCp7X0Cd04wM6jIXlnLfg").append("\n");
            sb.append("x-bili-content-md5:").append(ToolList.getInstance().getMD5(data)).append("\n");
            sb.append("x-bili-signature-method:HMAC-SHA256").append("\n");
            sb.append("x-bili-signature-nonce:").append(randKey).append("\n");
            sb.append("x-bili-signature-version:1.0").append("\n");
            sb.append("x-bili-timestamp:").append(l);

            String signature = getHMACSha256Str(sb.toString(), accessSecret);
            System.out.println(sb);
            System.out.println(signature);

            uc.setRequestProperty("Authorization",signature);

        /*
                "x-bili-accesskeyid:$accesskeyidValue" + "\n" +
                "x-bili-content-md5:$contentMd5Value" + "\n" +
                "x-bili-signature-method:HMAC-SHA256" + "\n" +
                "x-bili-signature-nonce:$signatureNonceValue" + "\n" +
                "x-bili-signature-version:1.0" + "\n" +
                "x-bili-timestamp:$timestamp"
        */

            uc.setDoOutput(true);
            uc.setDoInput(true);
            uc.getOutputStream().write(data.getBytes(StandardCharsets.UTF_8));
            uc.connect();

            InputStream is = uc.getResponseCode() < 300 ? uc.getInputStream() : uc.getErrorStream();

            ByteArrayOutputStream os = new ByteArrayOutputStream();

            int i;
            while((i = is.read()) != -1) os.write(i);

            String s = os.toString(StandardCharsets.UTF_8);
            System.out.println(s);

            JsonObject jo = JsonParser.parseString(s).getAsJsonObject();

            JsonElement je = jo.get("data");
            if(je.isJsonNull()) {
                throw new RuntimeException("Bad return: " + s);
            }
            JsonObject jo1 = je.getAsJsonObject();
            String room_id = jo1.getAsJsonObject("anchor_info").get("room_id").getAsString();
            String uid = jo1.getAsJsonObject("anchor_info").get("uid").getAsString();
            uname = jo1.getAsJsonObject("anchor_info").get("uname").getAsString();

            userUID = jo1.getAsJsonObject("anchor_info").get("uid").getAsLong();

            String game_id = jo1.getAsJsonObject("game_info").get("game_id").getAsString();
            String auth_body = jo1.getAsJsonObject("websocket_info").get("auth_body").getAsString();

            List<String> wss_link = new ArrayList<>();
            jo1.getAsJsonObject("websocket_info").get("wss_link").getAsJsonArray().iterator().forEachRemaining(a->wss_link.add(a.getAsString()));

            System.out.println("room_id: " + room_id);
            System.out.println("uid: " + uid);
            System.out.println("uname: " + uname);
            System.out.println("game_id: " + game_id);
            System.out.println("wss_link: " + wss_link);
            System.out.println("auth_body: " + auth_body);

            startTime = System.currentTimeMillis();
            client = new XSDBLiveClient(game_id, wss_link, auth_body, accessSecret, accessKey, new Hook());
            keepAlive();
            client.connect();
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §aB站直播间监听器启动成功!"));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §a欢迎回来, §d" + uname + "§a!"));

            isListening = true;
        } catch (Exception e) {
            e.printStackTrace();
            stopListen();
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §cB站直播间监听器启动失败! 请检查你的身份码! " + e));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §d点击这里§c查看身份码").withStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create("https://play-live.bilibili.com/"))).withHoverEvent(new HoverEvent.ShowText(Component.literal("点击这里查看身份码")))));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c若问题没有解决, 请联系小沙雕! (进群" + SkyDiaoModClient.CONST_QQGROUP_MAIN + ")"));
            return;
        }

        long sleepTime = 4000;
        try {
            while(true) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {

                }

                String heartbeatFeedback = BApi.heartBeatInteractivePlay(client.gameId);
                if (JsonParser.parseString(heartbeatFeedback).getAsJsonObject().get("code").getAsInt() != 0) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §c当前GameID无效, 尝试进行重连..."));
                    ToolList.addThreadedTask(() -> {
                        Thread.sleep(1000);
                        stopListen();
                        launch();
                        return null;
                    });
                }

                if(isListening && staticToken == token) {
                    try {
                        sleepTime = 20000;
                        if(!client.getStatus()) {
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c小沙雕B站直播间监听器连接已断开或连接失败, 请检查网络或身份码是否正确! 如果你刚刚关闭监听器, 请等待1分钟后再试; 如果你开了加速器, 关闭加速器5秒后即可重新启用"));
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §d点击这里§c查看身份码").withStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create("https://play-live.bilibili.com/"))).withHoverEvent(new HoverEvent.ShowText(Component.literal("点击这里查看身份码")))));
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c若问题没有解决, 请联系小沙雕! (进群" + SkyDiaoModClient.CONST_QQGROUP_MAIN + ")"));
                            client.connect();
                            sleepTime = 5000;
                        } else if(reconnectFlag) {
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e为了防止B站直播间连接假死, 执行自动断开后重连"));
                            client.connect();
                            sleepTime = 5000;
                            reconnectFlag = false;
                            startTime = System.currentTimeMillis();
                            keepAlive();
                        } else if(System.currentTimeMillis() - aliveTime > 120000) {
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e已经很久没收到直播间的包了, 是不是断了, 自动重连中"));
                            client.connect();
                            sleepTime = 5000;
                            keepAlive();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } else {
//                    System.out.println("断开直播间链接中...");
//                    BApiClient.endInteractivePlay(appid, client.gameId);
//                    client.disconnect();
//                    System.out.println("断开成功!");
                    return;
                }

            }
        } finally {
            System.out.println("断开直播间链接中...");
            BApiClient.endInteractivePlay(appid, client.gameId);
            client.disconnect();
            System.out.println("断开成功!");

            if (uname != null) {
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §cB站直播间监听器断开成功!"));
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §c再见!, §d" + uname + "§a!"));
            }
        }

    }

    public void keepAlive() {
        if(System.currentTimeMillis() - startTime < 1000 * 60 * 60) {
            aliveTime = System.currentTimeMillis();
        } else {
            reconnectFlag = true;
        }
    }

    public static String getHMACSha256Str(String str, String token) throws Exception {

        StringBuilder encodeStr = new StringBuilder();
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secretKey);
            byte[] hash = sha256_HMAC.doFinal(str.getBytes(StandardCharsets.UTF_8));

            // encodeStr = byte2Hex(messageDigest.digest());
            for (byte bb : hash) {
                int i = bb & 255;
                if (i < 16 && i >= 0) encodeStr.append("0").append(Integer.toHexString(i));
                else encodeStr.append(Integer.toHexString(i));
            }

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return encodeStr.toString();

    }

    public static long getUserUID() {
        return userUID;
    }
}

