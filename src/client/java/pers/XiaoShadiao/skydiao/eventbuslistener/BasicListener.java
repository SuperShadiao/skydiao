package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.hypixel.modapi.packet.ClientboundHypixelPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import pers.XiaoShadiao.skydiao.SkyDiaoModClient;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.screen.mircosoftaccount.AccountSelectScreen;
import pers.XiaoShadiao.skydiao.utils.AutoUpdater;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;

import java.net.URI;
import java.util.Arrays;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class BasicListener extends AbstractListener {

    public long lastOperationTime = System.currentTimeMillis();
    private boolean isAFK = false;
    private boolean isConnectedToServer;

    @Override
    public String getListenerName() {
        return "BasicListener";
    }

    @Override
    protected void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::onWorldChange);
        CustomFabricEvents.HYPIXEL_PACKET_EVENT.register(this::onHypixelPacket);
        ScreenEvents.AFTER_INIT.register(this::onGuiFinishedInit);
        ClientPlayConnectionEvents.JOIN.register(this::onJoinServer);
        ClientPlayConnectionEvents.DISCONNECT.register(this::onDisconnect);
    }

    private void onDisconnect(ClientPacketListener clientPacketListener, Minecraft minecraft) {
        logger.info("已断开服务器连接: " + clientPacketListener.getConnection().getRemoteAddress());
        isConnectedToServer = false;

        mc.execute(CustomRenderPipeline::closeAll);
    }

    private void onJoinServer(ClientPacketListener clientPacketListener, PacketSender packetSender, Minecraft minecraft) {
        if(!isConnectedToServer) {
            isConnectedToServer = true;
            logger.info("已连接到服务器: " + clientPacketListener.getConnection().getRemoteAddress());
            ToolList.addThreadedTask(() -> {
                Thread.sleep(3000);
                mc.execute(() -> {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] 欢迎使用SkyDiao Mod, 使用/skydiao指令打开菜单看看有什么吧!"));
                    AutoUpdater update = AutoUpdater.getInstance();
                    if (null != update) {
                        if (!SkyDiaoModClient.VERSION.equals(update.newVer)) {
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] 新版本可用: " + update.newVer));
                            ToolList.printChatMessage(update.downlanded ?
                                    Component.literal("§a[小沙雕] 新版本已下载完成, 本次重启后自动更新") :
                                    Component.literal("§a[小沙雕] §c新版本未能成功下载, 但你可以§e点击这里§c尝试手动更新").withStyle(Style.EMPTY
                                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§e点击这里打开链接")))
                                            .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://5ixsd.top/skydiao")))
                                    ));
                        }
                    }
                    if (FabricLoader.getInstance().isModLoaded("modmenu")) {
                        ToolList.printChatMessage(Component.literal("§a[小沙雕] 你已安装ModMenu, 你可以§e点击这里§a中查看SkyDiao Mod的配置, 或者按 §eESC -> 模组 §a查看").withStyle(Style.EMPTY
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§a点击这里打开ModMenu菜单")))
                                .withClickEvent(new ClickEvent.RunCommand("/hhm modmenumod"))
                        ));
                    }
                });
                return null;
            });
        }
    }

    private void onGuiFinishedInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if(screen instanceof TitleScreen titleScreen) {
            Screens.getButtons(titleScreen).add(Button.builder(
                    AccountSelectScreen.getTitle0(),
                    (button) -> ToolList.mc.setScreen(new AccountSelectScreen(titleScreen))
            ).bounds(scaledHeight < 270 ? 10 : scaledWidth / 2 - 50, scaledHeight - (scaledHeight < 260 ? 35 : 28), 100, 20).build());
        }
    }

    private void onHypixelPacket(ClientboundHypixelPacket packet) {
        if(packet instanceof ClientboundLocationPacket) StatusManager.updateStatusByHypixelPacket((ClientboundLocationPacket) packet);
    }

    private void onWorldChange(Minecraft mc, ClientLevel clientLevel) {
        ChatClientManager.getChatClient();
    }

    private void onStartClientTick(Minecraft mc) {
        if((!(mc.screen instanceof ChatScreen) || mc.screen.getClass().getName().startsWith("pers.XiaoShadiao")) && Arrays.stream(mc.options.keyMappings).anyMatch(KeyMapping::isDown))  {
            if(isAFK) {
                isAFK = false;
                if(ChatClientManager.serverAvailable()) ChatClientManager.getChatClient().sender.sendAFK(false);
            }
            lastOperationTime = System.currentTimeMillis();
        }
        if(System.currentTimeMillis() - lastOperationTime > 120000 && !isAFK) {
            isAFK = true;
            if(ChatClientManager.serverAvailable()) ChatClientManager.getChatClient().sender.sendAFK(true);
        }
    }

    public boolean isAFK() {
        return System.currentTimeMillis() - lastOperationTime > 120000;
    }

}
