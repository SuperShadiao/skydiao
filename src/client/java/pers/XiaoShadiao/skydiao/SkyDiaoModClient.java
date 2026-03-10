package pers.XiaoShadiao.skydiao;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.fabric.FabricModAPI;
import net.hypixel.modapi.handler.ClientboundPacketHandler;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPingPacket;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPlayerInfoPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import pers.XiaoShadiao.skydiao.commands.CommandManager;
import pers.XiaoShadiao.skydiao.customsounds.CustomSounds;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.screen.mircosoftaccount.AccountSelectScreen;
import pers.XiaoShadiao.skydiao.utils.autoupdater.AutoUpdater;
import pers.XiaoShadiao.skydiao.utils.autoupdater.ExecuteOfflineThread;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;
import pers.XiaoShadiao.skydiao.utils.mircosoftaccount.MinecraftLogin;

import java.util.concurrent.TimeUnit;

public class SkyDiaoModClient implements ClientModInitializer {

    public static final String MOD_ID = "skydiao";
    public static final String VERSION = "0.0.4";

    public static final String CONST_QQGROUP_MAIN = "728972740";
    public static final String CONST_QQGROUP_OTHER1 = "1103539591";

    public static String currentNewVersion = VERSION;

    public static void setNewestVersion(String newVer) {
        currentNewVersion = newVer;
    }

    public static String getCurrentNewVersion() {
        return currentNewVersion;
    }

    @Override
	public void onInitializeClient() {
        AutoUpdater.checkUpdate();
        try {
            CrowdinI18nManager.initI18n(CrowdinI18nManager.LangCode.english).future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            CrowdinI18nManager.initI18n(CrowdinI18nManager.LangCode.chinese).future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        CrowdinI18nManager.initI18nFromConfig();

        MinecraftLogin.replaceSession(ToolList.mc.getUser());
        AccountSelectScreen.load();
        // System.out.println(getClass().getClassLoader());
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
        AbstractListener.initListeners();
        ChatClientManager.refreshChatClient();
        XSDHUD.init();
        CustomSounds.initialize();

        ClientboundPacketHandler handler = p -> {
            ToolList.getInstance().log.info(p.getIdentifier());
            ToolList.getInstance().log.info("Hyp Packet: " + p);

            CustomFabricEvents.HYPIXEL_PACKET_EVENT.invoker().onPacket(p);
        };

        HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket.class, handler);
        HypixelModAPI.getInstance().createHandler(ClientboundHelloPacket.class, handler);
        HypixelModAPI.getInstance().createHandler(ClientboundPingPacket.class, handler);
        HypixelModAPI.getInstance().createHandler(ClientboundPartyInfoPacket.class, handler);
        HypixelModAPI.getInstance().createHandler(ClientboundPlayerInfoPacket.class, handler);

        HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket.class);

        if(!FabricLoader.getInstance().isModLoaded("hypixel-mod-api")) {
            ToolList.getInstance().log.info("Hypixel Mod API未加载, 手动执行加载中...");
            new FabricModAPI().onInitializeClient();
        } else {
            ToolList.getInstance().log.info("Hypixel Mod API已安装&加载");
        }

        CommandManager.registerCommands();
        Runtime.getRuntime().addShutdownHook(new Thread(new ExecuteOfflineThread()));
	}
}