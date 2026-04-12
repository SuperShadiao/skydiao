package pers.XiaoShadiao.skydiao.utils.mircosoftaccount;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.util.UndashedUuid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.apache.commons.io.FileUtils;
import pers.XiaoShadiao.skydiao.mixin.client.MixinMinecraftSessionAccessor;
import pers.XiaoShadiao.skydiao.screen.mircosoftaccount.AccountSelectScreen;
import pers.XiaoShadiao.skydiao.utils.HttpSSLDisabler;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.ImageTexture;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class MinecraftLogin {

    public static class MinecraftLoginException extends Exception {
        public MinecraftLoginException(String message) {
            super(message);
        }
    }

    private static final String MICROSOFT_TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String XBOX_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_AUTH_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    public static MinecraftLogin instance = new MinecraftLogin();

    private MinecraftLogin() {  }

    public MinecraftSessionContainer initializeAccount(String key, boolean refresh) throws Exception {
        String[] accessToken = null;

        // Step 1: Get Microsoft Access Token  
        if (refresh) {
            accessToken = fetchMicrosoftToken(key, true);
        } else {
            accessToken = fetchMicrosoftToken(key, false);
        }

        // Step 2: Authenticate with Xbox  
        String[] xboxToken = authenticateXbox(accessToken[0]);
        String uhs = getUhsFromXboxResponse(xboxToken[0]);

        // Step 3: Authenticate with XSTS  
        String xstsToken = authenticateXsts(xboxToken[1], uhs);

        // Step 4: Authenticate with Minecraft  
        String mcAccessToken = authenticateMinecraft(uhs, xstsToken);

        // Step 5: Check if the user has purchased Minecraft
        MinecraftSessionContainer msc = checkMinecraftProfile(mcAccessToken);
        return new MinecraftSessionContainer(mcAccessToken, msc.name, msc.uuid, msc.isPurchased, accessToken[0], accessToken[1], System.currentTimeMillis() + 86400 * 1000, msc.skinUrl);

    }

    private String[] fetchMicrosoftToken(String key, boolean isRefresh) throws Exception {
        String params;
        if (isRefresh) {
            params = String.format("client_id=00000000402b5328&refresh_token=%s&grant_type=refresh_token&redirect_uri=https://login.live.com/oauth20_desktop.srf&scope=service::user.auth.xboxlive.com::MBI_SSL", URLEncoder.encode(key, "UTF-8"));
        } else {
            params = String.format("client_id=00000000402b5328&code=%s&grant_type=authorization_code&redirect_uri=https%%3A%%2F%%2Flogin.live.com%%2Foauth20_desktop.srf&scope=service%%3A%%3Auser.auth.xboxlive.com%%3A%%3AMBI_SSL", URLEncoder.encode(key, "UTF-8"));
        }

        String response = sendPostRequest(MICROSOFT_TOKEN_URL, params, conn -> {
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        });
        JsonObject jsonObject = new JsonParser().parse(response).getAsJsonObject();
        try {
            return new String[] {jsonObject.get("access_token").getAsString(), jsonObject.get("refresh_token").getAsString()};
        } catch (Exception e) {
            // System.out.println("Can't parse response: " + response);
            String customErrorMsg = null;
            if(jsonObject.has("error")) {
                switch (jsonObject.get("error").getAsString()) {
                    case "invalid_grant":
                        customErrorMsg = "gui.accountmanager.tipaccountloginerror.expired";
                        break;
                }
            }
            // throw jsonObject.has("error") && jsonObject.get("error").getAsString().equals("invalid_grant") ? new MinecraftLoginException("gui.accountmanager.tipaccountloginerror.expired") : e;
            throw customErrorMsg != null ? new MinecraftLoginException(translate(customErrorMsg)) : e;
        }
    }

    private String[] authenticateXbox(String token) throws Exception {
        String payload = String.format("{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"%s\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}", token);
        String response = sendPostRequest(XBOX_AUTH_URL, payload, conn -> {
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
        });
        JsonObject jsonObject = new JsonParser().parse(response).getAsJsonObject();
        return new String[] {response, jsonObject.get("Token").getAsString()};
    }

    private String getUhsFromXboxResponse(String xboxToken) throws Exception {
        JsonObject jsonObject = new JsonParser().parse(xboxToken).getAsJsonObject();
        JsonObject displayClaims = jsonObject.getAsJsonObject("DisplayClaims");
        JsonObject xui = displayClaims.getAsJsonArray("xui").get(0).getAsJsonObject();
        return xui.get("uhs").getAsString();
    }

    private String authenticateXsts(String xboxToken, String uhs) throws Exception {
        String payload = String.format("{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"%s\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}", xboxToken);
        String response = sendPostRequest(XSTS_AUTH_URL, payload, null);
        JsonObject jsonObject = new JsonParser().parse(response).getAsJsonObject();
        return jsonObject.get("Token").getAsString();
    }

    private String authenticateMinecraft(String uhs, String xstsToken) throws Exception {
        String payload = String.format("{\"identityToken\":\"XBL3.0 x=%s;%s\"}", uhs, xstsToken);
        String response = sendPostRequest(MC_AUTH_URL, payload, conn -> {
            conn.setRequestProperty("Content-Type", "application/json");
        });
        JsonObject jsonObject = new JsonParser().parse(response).getAsJsonObject();
        return jsonObject.get("access_token").getAsString();
    }

    private MinecraftSessionContainer checkMinecraftProfile(String accessToken) throws Exception {
        String response = sendGetRequest(MC_PROFILE_URL, accessToken);
        JsonObject jsonObject = new JsonParser().parse(response).getAsJsonObject();

        System.out.println(response);

        if (jsonObject.has("name") && jsonObject.has("id")) {
            String username = jsonObject.get("name").getAsString();
            String userId = jsonObject.get("id").getAsString();
            // System.out.println("用户名: " + username);
            // System.out.println("用户ID: " + userId);
            String skinUrl = "https://textures.minecraft.net/texture/3a361129b9138c0ba865d75255c9b6003858dd8ee0ba74fcec35612074b73279";
            for (JsonElement je : jsonObject.get("skins").getAsJsonArray()) {
                JsonObject jo = je.getAsJsonObject();
                if(jo.get("state").getAsString().equals("ACTIVE")) {
                    skinUrl = jo.get("url").getAsString();
                    break;
                }
            }

            return new MinecraftSessionContainer("", username, userId, true, "", "", 0, skinUrl);
        } else {
            // System.out.println("当前账户没有购买 Minecraft！");
            // 这里可以提示用户访问购买链接
            String s = translate("gui.accountmanager.tipaccountloginerror.notpurchased");
            // "此账号未购买MC Java Edition, 若确定已经购买, 请前往minecraft.net登录一次账号后再试";
            return new MinecraftSessionContainer(
                    s,
                    s,
                    s,
                    false,
                    s,
                    s,
                    0,
                    s);
        }
    }

    private String sendPostRequest(String requestUrl, String postParams, Consumer<HttpURLConnection> consumer) throws Exception {
        URL url = new URL(requestUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        if(consumer != null) consumer.accept(conn);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(postParams.getBytes());
            os.flush();
        }

        return getResponse(conn);
    }

    private String sendGetRequest(String requestUrl, String accessToken) throws Exception {
        URL url = new URL(requestUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);

        return getResponse(conn);
    }

    private String getResponse(HttpURLConnection conn) throws Exception {
        StringBuilder response = new StringBuilder();
        InputStream in1;
        try {
            in1 = conn.getInputStream();
        } catch (Exception e) {
            in1 = conn.getErrorStream();
        }
        try (BufferedReader in = new BufferedReader(new InputStreamReader(in1))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }
        return response.toString();
    }

    public static class MinecraftSessionContainer {
        @Deprecated
        public String loginToken;
        private String encodedLoginToken;

        public String name;
        public String uuid;
        public boolean isPurchased;

        public String accessToken, refreshToken;

        public long expiresTime;
        public String skinUrl;
        public File skinFile;
        private final Identifier skinRL;

        public MinecraftSessionContainer(String loginToken, String name, String uuid, boolean isPurchased, String accessToken, String refreshToken, long expiresTime, String skinUrl) {
            this.isPurchased = isPurchased;
            // this.loginToken = loginToken;
            this.encodedLoginToken = ToolList.getInstance().encodeString(loginToken);
            this.name = name;
            this.uuid = uuid;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresTime = expiresTime;
            this.skinUrl = skinUrl;
            this.skinFile = new File(AccountSelectScreen.configFileDir, uuid + "_" + name + ".png");
            try {
                if(!skinFile.exists()) {
                    skinFile.createNewFile();
                    downloadSkin();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.skinRL = Identifier.fromNamespaceAndPath("skydiao", (uuid + "_" + name).toLowerCase());

        }

        public Identifier getSkinRL() {
            if(ToolList.mc.getTextureManager() != null) {
                try {
                    ToolList.mc.getTextureManager().registerAndLoad(skinRL, new ImageTexture(skinRL, FileUtils.readFileToByteArray(skinFile)));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return skinRL;
        }

        public void downloadSkin() throws IOException {
            if(skinUrl != null && skinUrl.startsWith("http")) FileUtils.copyInputStreamToFile(ToolList.getInstance().makeReqToURL(skinUrl), skinFile);
        }

        public void replace(MinecraftSessionContainer msc) {
            this.isPurchased = msc.isPurchased;
            // this.loginToken = msc.loginToken;
            this.encodedLoginToken = msc.encodedLoginToken;
            this.name = msc.name;
            this.uuid = msc.uuid;
            this.accessToken = msc.accessToken;
            this.refreshToken = msc.refreshToken;
            this.expiresTime = msc.expiresTime;
            if(skinUrl != null && skinUrl.startsWith("http")) this.skinUrl = msc.skinUrl;
        }

        public String getEncodedLoginToken() {
            return encodedLoginToken;
        }

        public String getDecodedLoginToken() {
            return ToolList.getInstance().decodeString(encodedLoginToken);
        }

        public String freshTokenAndLogin() {

            boolean flag = true;
            try {
                if(expiresTime > System.currentTimeMillis()) {
                    ToolList.getInstance().log.warn(name + ": Token is not expired...");
                    return null;
                } else {
                    try {
                        replace(instance.initializeAccount(this.refreshToken, true));
                        downloadSkin();
                        ToolList.getInstance().log.info("Refresh token (" + name + ") success!");
                    } catch(MinecraftLoginException e) {
                        ToolList.getInstance().log.warn("Refresh token (" + name + ") failed!", e);
                        flag = false;
                        return e.getMessage();
                    } catch (Exception e) {
                        ToolList.getInstance().log.warn("Refresh token (" + name + ") failed!", e);
                        flag = false;
                        return "gui.accountmanager.tipaccountloginerror.exception";
                    }
                }
            } finally {
                if(flag) replaceSession(new XSDSafeSession(name, UndashedUuid.fromString(uuid), getDecodedLoginToken()));
            }

            return null;
        }

        public void freshToken() {

            if(expiresTime > System.currentTimeMillis()) {
                ToolList.getInstance().log.warn(name + ": Token is not expired...");
                return;
            }

            new Thread(() -> {
                try {
                    replace(instance.initializeAccount(this.refreshToken, true));
                    downloadSkin();
                    ToolList.getInstance().log.info("Refresh token (" + name + ") success!");
                } catch (Exception e) {
                    ToolList.getInstance().log.warn("Refresh token (" + name + ") failed!", e);
                }
            }).start();

        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MinecraftSessionContainer that = (MinecraftSessionContainer) o;
            return Objects.equals(uuid, that.uuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uuid);
        }

        public void flagInvaildToken() {
            expiresTime = 0L;
        }
    }

    public static void replaceSession(XSDSafeSession session) {
        ((MixinMinecraftSessionAccessor) ToolList.mc).setUser(session);
    }

    public static void replaceSession(User session) {
        ((MixinMinecraftSessionAccessor) ToolList.mc).setUser(new XSDSafeSession(session));
    }

} 