package pers.XiaoShadiao.skydiao.screen.mircosoftaccount;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.SkyDiaoModClient;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;
import pers.XiaoShadiao.skydiao.utils.mircosoftaccount.MinecraftLogin;
import pers.XiaoShadiao.skydiao.utils.mircosoftaccount.VerifyHttpServer;
import pers.XiaoShadiao.skydiao.utils.mircosoftaccount.hwid.HWIDGenerator_v4;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class AccountSelectScreen extends Screen {

    private static String tipMessage = ToolList.getInstance().isDevEnvironment() ? "Test".repeat(300) : "";
    private static boolean isAccountChanged;
    private static boolean firstLoad = true;

    public static final File configFileDir = new File(ToolList.mc.gameDirectory, "XSDAccounts");
    public static final File configFileV4 = new File(configFileDir, "mircosoftaccounts_V4.badxsd");
    public static final File configLogin = new File(configFileDir, "xsdbrowser.exe");

    public static final File configCurrentFile = configFileV4;

    public static final Map<File, Callable<String>> configFileMap = new HashMap<>();

    static {
        configFileMap.put(configFileV4, HWIDGenerator_v4::generateHWID);
    }

    private VerifyHttpServer server;

    private static final Set<MinecraftLogin.MinecraftSessionContainer> accounts = new HashSet<>();

    private int scroll;
    private int maxScroll;

    public static Set<MinecraftLogin.MinecraftSessionContainer> getAccounts() {
        return accounts;
    }

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    private void saveConfig() {
        save();
    }

    private static JsonObject MSC_To_Json(MinecraftLogin.MinecraftSessionContainer in) {
        JsonObject json = new JsonObject();
        json.addProperty("accessToken", String.valueOf(in.accessToken));
        json.addProperty("refreshToken", String.valueOf(in.refreshToken));
        json.addProperty("expiresTime", in.expiresTime);
        json.addProperty("loginToken", String.valueOf(in.getDecodedLoginToken()));
        json.addProperty("name", String.valueOf(in.name));
        json.addProperty("uuid", String.valueOf(in.uuid));
        json.addProperty("isPurchased", in.isPurchased);
        json.addProperty("skinUrl", String.valueOf(in.skinUrl));
        return json;
    }

    private static MinecraftLogin.MinecraftSessionContainer Json_To_MSC(JsonObject json) {
        return new MinecraftLogin.MinecraftSessionContainer(
                json.get("loginToken").getAsString(),
                json.get("name").getAsString(),
                json.get("uuid").getAsString(),
                json.get("isPurchased").getAsBoolean(),
                json.get("accessToken").getAsString(),
                json.get("refreshToken").getAsString(),
                json.get("expiresTime").getAsLong(),
                json.get("skinUrl").getAsString()
        );
    }

    static {
        if(!configFileDir.exists()) {
            configFileDir.mkdirs();
        }
        if(!configCurrentFile.exists()) {
            try {
                configCurrentFile.createNewFile();
                loadFromOldFile();
            } catch (IOException e) {
            }
        }
    }

    public static void loadFromOldFile() {
        for (Map.Entry<File, Callable<String>> entry : configFileMap.entrySet()) {
            File file = entry.getKey();
            Callable<String> hwidCaller = entry.getValue();

            if(file == configCurrentFile) continue;

            if(file.exists()) {
                try {
                    String s = decodeString(FileUtils.readFileToByteArray(file), hwidCaller.call());
                    FileUtils.writeByteArrayToFile(configCurrentFile, encodeString(s, getCurrentHWID()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                file.delete();
            }
        }
    }

    private static String getCurrentHWID() {
        try {
            return configFileMap.get(configCurrentFile).call();
        } catch (Exception e) {
            return "unsupported";
        }
    }

    public static void load() {

        HashSet<MinecraftLogin.MinecraftSessionContainer> backup = new HashSet<>(accounts);
        try {
            String s = decodeString(FileUtils.readFileToByteArray(configCurrentFile), getCurrentHWID());// FileUtils.readFileToString(configFile, "UTF-8");

            JsonArray json = JsonParser.parseString(s).getAsJsonArray();
            accounts.clear();
            for (JsonElement je : json) {
                JsonObject jo = je.getAsJsonObject();
                MinecraftLogin.MinecraftSessionContainer msc = Json_To_MSC(jo);
                accounts.add(msc);
            }

        } catch (Exception e) {
            ToolList.getInstance().log.warn("Failed to load accounts");
            e.printStackTrace();
            accounts.clear();
            accounts.addAll(backup);
        }

        if(firstLoad) {
            firstLoad = false;

            for (MinecraftLogin.MinecraftSessionContainer account : accounts) {
                account.freshToken();
            }
        }

    }

    public static void save() {

        try {
            JsonArray json = new JsonArray();
            for (MinecraftLogin.MinecraftSessionContainer msc : accounts) {
                if(msc.isPurchased) json.add(MSC_To_Json(msc));
            }
            // FileUtils.writeStringToFile(configFile, json.toString(), "UTF-8");
            FileUtils.writeByteArrayToFile(configCurrentFile, encodeString(json.toString(), getCurrentHWID()));
        } catch (Exception e) {
            ToolList.getInstance().log.warn("Failed to save accounts");
            e.printStackTrace();
        }

    }

    public static void addAccount(MinecraftLogin.MinecraftSessionContainer msc) {
        accounts.remove(msc);
        accounts.add(msc);

        if(msc.isPurchased) {
            tipSuccess(/*"登录成功! "*/translate("gui.accountmanager.tipaccountloginsuccess") + " " + msc.name);
        } else {
            tipError(msc.name);
        }
        isAccountChanged = true;
    }

    public static void removeAccount(MinecraftLogin.MinecraftSessionContainer msc) {
        accounts.remove(msc);
        tipInfo(/*"删除" + msc.name + "成功!"*/translate("gui.accountmanager.tipdelete", msc.name));

        isAccountChanged = true;
    }

    public static void importConfig(String key) {

        File target;
        try {
            // 获取系统剪贴板
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            // 获取剪贴板内容
            Transferable contents = clipboard.getContents(null);

            if (contents != null && contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                // 获取文件列表
                List<File> fileList = (List<File>) contents.getTransferData(DataFlavor.javaFileListFlavor);

                // 遍历文件并处理
//                for (File file : fileList) {
//                    // System.out.println("Copied file: " + file.getAbsolutePath());
//                    // 这里可以进行其他操作，例如导入文件等
//                }
                target = fileList.get(0);
            } else {
                // System.out.println("剪贴板中没有文件。");
                return;
            }
        } catch (Exception e) {
            tipError(/*"无法导入这个文件! 请检查剪切板上的文件是否有效或密码是否正确!"*/translate("gui.accountmanager.tipimporterror"));
            ToolList.getInstance().log.error("Can't import file!", e);
            return;
        }

        HashSet<MinecraftLogin.MinecraftSessionContainer> backup = new HashSet<>(accounts);
        try {
            String s = decodeString(FileUtils.readFileToByteArray(target), key);// FileUtils.readFileToString(configFile, "UTF-8");

            JsonArray json = JsonParser.parseString(s).getAsJsonArray();
            accounts.clear();
            for (JsonElement je : json) {
                JsonObject jo = je.getAsJsonObject();
                MinecraftLogin.MinecraftSessionContainer msc = Json_To_MSC(jo);
                accounts.add(msc);
            }

            tipSuccess(/*"载入成功!"*/translate("gui.accountmanager.tipimportsuccess"));
        } catch (Exception e) {
            tipError(/*"无法导入这个文件! 请检查剪切板上的文件是否有效或密码是否正确!"*/translate("gui.accountmanager.tipimporterror"));
            ToolList.getInstance().log.warn("Failed to load accounts");
            e.printStackTrace();
            accounts.clear();
            accounts.addAll(backup);
        }

        if(firstLoad) {
            firstLoad = false;

            for (MinecraftLogin.MinecraftSessionContainer account : accounts) {
                account.freshToken();
            }
        }

    }

    public static void exportConfig(String key) {

        File desktop = new File(System.getProperty("user.home"), "Desktop");
        desktop = new File(desktop, "accounts.goodxsd");
        try {
            JsonArray json = new JsonArray();
            for (MinecraftLogin.MinecraftSessionContainer msc : accounts) {
                if(msc.isPurchased) json.add(MSC_To_Json(msc));
            }
            // FileUtils.writeStringToFile(configFile, json.toString(), "UTF-8");
            FileUtils.writeByteArrayToFile(desktop, encodeString(json.toString(), key));
            tipSuccess(/*"导出成功!"*/translate("gui.accountmanager.tipexportsuccess"));
        } catch (Exception e) {
            tipError(/*"导出失败!"*/translate("gui.accountmanager.tipexporterror"));
            ToolList.getInstance().log.warn("Failed to save accounts");
            e.printStackTrace();
        }

    }

    private static byte[] encodeString(String in, String key) {
        if(!ToolList.getInstance().stringHasContext(key)) return in.getBytes(StandardCharsets.UTF_8);
        String stage1 = in;// ToolList.getInstance().encodeString(in);

        byte[] stage2 = stage1.getBytes(StandardCharsets.UTF_8);

        byte[] keyBytes = (key + ToolList.getInstance().getMD5(key)).getBytes(StandardCharsets.UTF_8);

        for (int i = 0; i < stage2.length; i++) {
            stage2[i] ^= keyBytes[i % keyBytes.length];
        }
        // verifyByte(stage2, stage1.getBytes(StandardCharsets.UTF_8), stage1, key);
        String s = decodeString(stage2, key);
        if(!s.equals(in)) {
            throw new AssertionError("加密时出错: \nin:" + in + ", \nout: " + s);
        }
        return stage2;
    }

    private static String decodeString(byte[] in, String key) {
        if(!ToolList.getInstance().stringHasContext(key)) return new String(in, StandardCharsets.UTF_8);
        byte[] stage2 = new byte[in.length];
        System.arraycopy(in, 0, stage2, 0, in.length);

        byte[] keyBytes = (key + ToolList.getInstance().getMD5(key)).getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < stage2.length; i++) {
            stage2[i] ^= keyBytes[i % keyBytes.length];
        }
        String stage1 = new String(stage2, StandardCharsets.UTF_8);

        return stage1; // ToolList.getInstance().decodeString(stage1);
    }


    private static void verifyByte(byte[] in, byte[] encoded, String stage1, String key) {
        if(!ToolList.getInstance().stringHasContext(key)) return;
        byte[] stage2 = new byte[in.length];
        System.arraycopy(encoded, 0, stage2, 0, encoded.length);

        byte[] keyBytes = (key + ToolList.getInstance().getMD5(key)).getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < stage2.length; i++) {
            stage2[i] ^= keyBytes[i % keyBytes.length];
        }

        boolean isBroken = in.length != stage2.length;
        int i;
        for (i = 0; i < in.length; i++) {
            if (in[i] != stage2[i]) {
                isBroken = true;
                break;
            }
        }
        if(isBroken) throw new AssertionError("解密时出错: b1 = " + in[i] + ", b2" + stage2[i] + ", index = " + i);

        String str = ToolList.getInstance().decodeString(new String(stage2));
        for (int j = 0; j < stage1.length(); j++) {
            if (stage1.charAt(j) != str.charAt(j)) {
                throw new AssertionError("解密时出错:cb1 = " + stage1.charAt(j) + ",cb2 = " + str.charAt(j) + ", index = " + j + "\nstr1 = " + str + "\nstr2 = " + stage1);
            }
        }
    }

    public static void tipWarn(String msg) {
        tipMessage = "§e⚠ " + msg;
    }

    public static void tipError(String msg) {
        tipMessage = "§c✖ " + msg;
    }

    public static void tipSuccess(String msg) {
        tipMessage = "§a✔ " + msg;
    }

    public static void tipInfo(String msg) {
        tipMessage = "§b" + msg;
    }

    private final Screen lastScreen;
    private VerifyHttpServer verifyHttpServer;
    private Throwable verifyHttpServerException = null;

    public static Component getTitle0() {
        return Component.literal(translate("gui.accountmanager.buttonentrance"));
    }

    public AccountSelectScreen(Screen lastScreen) {
        super(getTitle0());
        this.lastScreen = lastScreen;
    }

    @Override
    public void onClose() {
        ToolList.mc.setScreen(lastScreen);
        removed();
    }

    @Override
    public void removed() {
        saveConfig();
        try {
            verifyHttpServer.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onClose(Button button) {
        onClose();
    }

    private static AccountList accountList;
    private LinearLayout footerButtonLayout;

    @Override
    protected void init() {
        if(verifyHttpServer == null) {
            verifyHttpServer = new VerifyHttpServer();
            try {
                verifyHttpServer.start();
            } catch (Exception e) {
                e.printStackTrace();
                verifyHttpServerException = e;
            }
        }

        LinearLayout linearLayout = footerButtonLayout =  this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        linearLayout.addChild(LinearLayout.horizontal().spacing(8));
        linearLayout.addChild(Button.builder(Component.literal(translate("gui.accountmanager.buttonback")), this::onClose).build());
        Button button1 = linearLayout.addChild(Button.builder(Component.literal(translate("gui.accountmanager.buttonaddmicrosoftaccount")), (button) -> {
            button.active = false;
            Runnable runnable = () -> {
                try {
                    if (!ToolList.getInstance().verifyFileWithMD5(configLogin, "6ac5548cff9ae693678111e6e101d48f")) {
                        String[] urls = new String[]{
                                "https://www.gitlink.org.cn/api/SuperShadiao/hypixelhelper/raw/xsdbrowser.exe?ref=main",
                                "https://xiaoshadiao.club/xsdbrowser.exe",
                                "https://github.com/SuperShadiao/hypixelhelper/raw/refs/heads/main/xsdbrowser.exe"
                        };
                        boolean flag = false;
                        for (String url : urls) {
                            try {
                                ToolList.getInstance().log.info(url);
                                FileUtils.writeByteArrayToFile(configLogin, ToolList.getInstance().downloadFileWithMD5(url, "6ac5548cff9ae693678111e6e101d48f"));
                                flag = false;
                                break;
                            } catch (Exception e) {
                                flag = true;
                            }
                        }
                        if (flag) throw new RuntimeException("Failed to download xsdbrowser.exe");
                    }
                    try {
                        tipInfo(/*"请完成登录操作..."*/translate("gui.accountmanager.tiploginstage"));
                        Runtime.getRuntime().exec("\"" + configLogin.getAbsolutePath() + "\"");
                    } catch (IOException e) {
                        configLogin.delete();
                    }
                } finally {
                    button.active = true;
                }
            };
            ToolList.addThreadedTask(runnable, null);
        }).build());
        button1.active = verifyHttpServerException == null;
        if(verifyHttpServerException != null) button1.setTooltip(Tooltip.create(Component.literal(CrowdinI18nManager.translate("服务启动失败: " + verifyHttpServerException + ", 若重试后问题仍然存在, 请尝试前往小沙雕の窝" + SkyDiaoModClient.CONST_QQGROUP_MAIN + "反馈!"))));
        linearLayout.setX(width / 2);
        linearLayout.setX(width / 2);
        accountList = new AccountList();
        this.layout.addToContents(accountList);
        this.layout.addTitleHeader(this.title, this.font);
        this.layout.visitWidgets(abstractWidget -> {
            abstractWidget.setTabOrderGroup(1);
            this.addRenderableWidget(abstractWidget);
        });
        this.repositionElements();
    }

    @Override
    public void tick() {
        if(isAccountChanged) {
            isAccountChanged = false;
            save();
            ToolList.mc.setScreen(new AccountSelectScreen(lastScreen));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int i, int j, float f) {
        super.extractRenderState(guiGraphics, i, j, f);
        if(ToolList.getInstance().stringHasContext(tipMessage)) {
            if(footerButtonLayout != null) RenderUtils.renderScrollingString(guiGraphics, font, Component.literal(tipMessage), 10, 10, footerButtonLayout.getRectangle().top(), footerButtonLayout.getRectangle().left() - 5, footerButtonLayout.getRectangle().bottom(), 0xFFFFFFFF);
        }
    }

    @Override
    public void repositionElements() {
        this.layout.arrangeElements();
        if(accountList != null) {
            accountList.updateSize(AccountSelectScreen.this.width, AccountSelectScreen.this.layout);
        }
    }

    public class AccountList extends ContainerObjectSelectionList<AccountList.AccountEntry> {

        public AccountList() {
            super(Minecraft.getInstance(), AccountSelectScreen.this.width, AccountSelectScreen.this.layout.getContentHeight(), AccountSelectScreen.this.layout.getHeaderHeight(), 30);
            for (MinecraftLogin.MinecraftSessionContainer account : accounts) {
                addEntry(new AccountEntry(account));
            }
        }

        public static class AccountEntry extends ContainerObjectSelectionList.Entry<AccountEntry> {
            private final MinecraftLogin.MinecraftSessionContainer account;
            private final Button login;
            private final Button delete;
            private final List<AbstractWidget> childs;

            public AccountEntry(MinecraftLogin.MinecraftSessionContainer account) {
                this.account = account;
                this.login = Button.builder(
                        Component.literal(translate("gui.accountmanager.buttonlogin")),
                        b -> {
                            String errorMsg = account.freshTokenAndLogin();
                            if(errorMsg == null) {
                                tipSuccess(/*"登录成功!"*/translate("gui.accountmanager.tipaccountloginsuccess"));
                            } else {
                                tipError(/*"登录失败! 请重试! 如果此问题频繁发生, 尝试删除该账号重新登录! 错误信息详见日志..."*/translate(/*"gui.accountmanager.tipaccountloginerror"*/errorMsg));
                            }
                        }
                ).bounds(50, 0, 50, 20).build();
                this.delete = Button.builder(
                        Component.literal(translate("gui.accountmanager.buttondelete")),
                        b -> {
                            accounts.remove(account);
                            accountList.removeEntry(this);
                            tipInfo(/*"删除" + msc.name + "成功!"*/translate("gui.accountmanager.tipdelete", account.name));
                        }
                ).bounds(0, 0, 50, 20).build();
                this.childs = List.of(login, delete);
            }

            @Override
            public @NotNull List<? extends NarratableEntry> narratables() {
                return childs;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor guiGraphics, int left, int top, boolean bl, float f) {
                String name = account.name;
                if (ToolList.mc.getUser().getName().equals(name)) {
                    name = "§a" + name;
                }


                // guiGraphics.drawString(Minecraft.getInstance().font, name, getContentX() - 5, getContentY() + 5, 0xFFFFFFFF);
                RenderUtils.renderScrollingString(guiGraphics, ToolList.mc.font, Component.literal(name), getContentX(), getContentX(), getContentY() - 25, getContentX() + 100, getContentY() + 44, 0xFFFFFFFF);
                login.setX(getContentRight() - login.getWidth() - 50);
                login.setY(getContentY());
                login.extractRenderState(guiGraphics, left, top, f);

                delete.setX(getContentRight() - delete.getWidth());
                delete.setY(getContentY());
                delete.extractRenderState(guiGraphics, left, top, f);

                PlayerFaceExtractor.extractRenderState(guiGraphics, account.getSkinRL(), getContentX() - 25, getContentY(), 20, true, false, -1);
            }

            @Override
            public @NotNull List<? extends GuiEventListener> children() {
                return childs;
            }
        }

        @Override
        protected void removeEntry(AccountEntry entry) {
            super.removeEntry(entry);
        }
    }

}