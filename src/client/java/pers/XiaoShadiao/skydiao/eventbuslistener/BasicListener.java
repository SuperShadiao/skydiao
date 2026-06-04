package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.loader.api.FabricLoader;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.ClientboundHypixelPacket;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerSkin;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.SkyDiaoModClient;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.config.option.BooleanConfigOption;
import pers.XiaoShadiao.skydiao.config.option.ConfigOption;
import pers.XiaoShadiao.skydiao.eventbuslistener.bilibili.BLiveListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.irc.ChatPacket;
import pers.XiaoShadiao.skydiao.screen.mircosoftaccount.AccountSelectScreen;
import pers.XiaoShadiao.skydiao.utils.autoupdater.ExecuteOfflineThread;
import pers.XiaoShadiao.skydiao.utils.blivesensitiveword.ServerIdSpoofer;
import pers.XiaoShadiao.skydiao.utils.mircosoftaccount.MinecraftLogin;
import pers.XiaoShadiao.skydiao.utils.musicplayer.PlayerThread;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;
import pers.XiaoShadiao.skydiao.utils.autoupdater.AutoUpdater;
import pers.XiaoShadiao.skydiao.utils.HypixelRewardClaimer;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.XSDSimulatorInput;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.ImageTexture;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

public class BasicListener extends AbstractListener {
    public static final Pattern URL_PATTERN = Pattern.compile("(https?://)([\\w-]+\\.)?[\\w-]+\\.[\\w-]+(/[\\w.!&?-]+)*");

    public long lastOperationTime = System.currentTimeMillis();
    private boolean isAFK = false;
    private boolean isConnectedToServer;
    private boolean testOOM;

    public PlayerSkin selfPlayerSkin;

    public static final Identifier customCape = Identifier.fromNamespaceAndPath("skydiao", "custom_cape");

    @Override
    public String getListenerName() {
        return "BasicListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::onWorldChange);
        CustomFabricEvents.HYPIXEL_PACKET_EVENT.register(this::onHypixelPacket);
        ScreenEvents.AFTER_INIT.register(this::onGuiFinishedInit);
        ClientPlayConnectionEvents.JOIN.register(this::onJoinServer);
        ClientPlayConnectionEvents.DISCONNECT.register(this::onDisconnect);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
        ClientReceiveMessageEvents.MODIFY_GAME.register(this::onModifyChat);
        WorldRenderEvents.END_MAIN.register(this::onLastRender);
        ClientLifecycleEvents.CLIENT_STARTED.register((mc) -> {
            PlayerThread.createThread();
            MinecraftLogin.checkSessionExpiredAndLogin();

            if (ConfigManager.blivelistener.getValue()) {
                BLiveListener.launch();
            }
        });
    }

    private Component onModifyChat(Component component, boolean b) {
        if(ToolList.getInstance().random.nextInt(10) == 5 && component.getString().contains("§aGalatea") && component instanceof MutableComponent m) {
            MutableComponent component1 = Component.literal(m.getString().replace("§aGalatea", "§aGalagame"));
            component1.getSiblings().forEach(m::append);
            component1.setStyle(m.getStyle());
            return component1;
        }
        return component;
    }

    private void onLastRender(WorldRenderContext context) {
//        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
//        if(mc.level != null) {
//            for (Entity entity : mc.level.entitiesForRendering()) {
//                RenderUtils.renderESP(wr, entity, 1, 0, 0, 1, false);
//            }
//        }
    }

    private void onChat(Component component, boolean b) {
        String message = ToolList.getInstance().deleteColorCode(component.getString());

        Matcher matcher = URL_PATTERN.matcher(message);
        if(matcher.find()) {
            String s = matcher.group();
            if(s.contains("hypixel.net/claim-reward/")) {
                HypixelRewardClaimer.get(s).doConnect();
            }
        }

        if(message.equals("CLICK HERE to say gg!") && ConfigManager.autogg.getValue()) {
            ToolList.addThreadedTask(() -> {
                Thread.sleep(1000);
                ToolList.sendChatMessage("/ac gg");
                return null;
            });
        }

        Matcher m = Pattern.compile("[a-zA-Z]+").matcher(message);
        while(m.find()) {
            String words = m.group();
            if(words.toLowerCase().contains("discord") || words.toLowerCase().endsWith("dc") || (words.toLowerCase().contains("dc") && words.length() <= 3)) {
                MutableComponent ic = Component.literal("§a[小沙雕] §c请不要相信任何以免费rank, 语音 (vc) 为由邀请你加入Discord服务器的老外, 更不要相信Discord服务器内的\"微软账号验证\"。" +
                        "如果你信了, 相信小沙雕, 你会后悔终身!");
                Style cs = Style.EMPTY
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://xiaoshadiao.club/antiscamming")))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("点击查看骗子诈骗账号的方式")));
                MutableComponent ic2 = Component.literal(" §e[点击这里查看为什么]").setStyle(cs);

                ToolList.printChatMessage(ic.append(ic2));
                break;
            }
        }
    }

    private void onDisconnect(ClientPacketListener clientPacketListener, Minecraft minecraft) {
        logger.info("已断开服务器连接: " + clientPacketListener.getConnection().getRemoteAddress());
        isConnectedToServer = false;

        mc.execute(CustomRenderPipeline::closeAll);
        StatusManager.cleanHypixelPacket();
        StatusManager.destory();
    }

    private void onJoinServer(ClientPacketListener clientPacketListener, PacketSender packetSender, Minecraft minecraft) {
        if(!isConnectedToServer) {
            isConnectedToServer = true;
            reloadCustomCape();
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
                            if(update.downlanded) {
                                ToolList.printChatMessage(Component.literal("§a[小沙雕] 若重启后自动更新程序未弹出, 请§e点击这里§a尝试手动更新 :>").withStyle(Style.EMPTY
                                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§e点击这里打开链接")))
                                        .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://5ixsd.top/skydiao")))
                                ));
                            }
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
            List<AbstractWidget> buttons = Screens.getButtons(titleScreen);
            int left = buttons.stream().min(Comparator.comparingInt(AbstractWidget::getX)).get().getX();
            if(Util.getPlatform() == Util.OS.WINDOWS) {
                buttons.add(Button.builder(
                        AccountSelectScreen.getTitle0(),
                        (button) -> ToolList.mc.setScreen(new AccountSelectScreen(titleScreen))
                ).bounds(scaledHeight < 270 ? 10 : scaledWidth / 2 - 50, scaledHeight - (scaledHeight < 270 ? 35 : 28), scaledHeight < 270 ? Math.min(100, left - 15) : 100, 20).build());
            }
        } else if(screen instanceof JoinMultiplayerScreen mpscreen) {
            MinecraftLogin.checkSessionExpiredAndLogin();
        } else if(screen instanceof DeathScreen deathScreen) {
            List<AbstractWidget> buttons = Screens.getButtons(deathScreen);
            int left = buttons.stream().min(Comparator.comparingInt(AbstractWidget::getX)).get().getX();

            buttons.add(Button.builder(
                    Component.literal("§a打开聊天栏"),
                    (button) -> ToolList.mc.setScreen(new ChatScreen("/l", false))
            ).bounds(scaledHeight < 270 ? 10 : scaledWidth / 2 - 50, scaledHeight - (scaledHeight < 270 ? 35 : 28), scaledHeight < 270 ? Math.min(100, left - 15) : 100, 20).build());
        }
    }

    private void onHypixelPacket(ClientboundHypixelPacket packet) {
        if(packet instanceof ClientboundLocationPacket) StatusManager.updateStatusByHypixelPacket((ClientboundLocationPacket) packet);
        else if(packet instanceof ClientboundPartyInfoPacket partyInfoPacket) {
            JsonArray ja = new JsonArray();
            for (Map.Entry<UUID, ClientboundPartyInfoPacket.PartyMember> entry : partyInfoPacket.getMemberMap().entrySet()) {
                JsonObject jo = new JsonObject();
                jo.addProperty("uuid", entry.getValue().getUuid().toString().replace("-", ""));
                jo.addProperty("role", entry.getValue().getRole().ordinal());
                ja.add(jo);
            }
            JsonObject jo = new JsonObject();
            jo.addProperty("inParty", partyInfoPacket.isInParty());
            jo.add("members", ja);

            ChatPacket cp = new ChatPacket();
            cp.packetType = "hyp_party";
            cp.message = jo.toString();
            cp.initSender();
            ChatClientManager.trySendOrWarning(cp);
        }
    }

    private void onWorldChange(Minecraft mc, ClientLevel clientLevel) {
        ChatClientManager.getChatClient();
        reloadCustomCape();
        InputSimulator.unpressAllKey();
        ServerIdSpoofer.generateServerIds();

        ToolList.getInstance().updatePartyInfo();
        if (ConfigManager.blivelistener.getValue()) {
            BLiveListener.launch();
        }
        MacroManagerListener.pathFinderExecutor.stopExecution();
    }

    private int afkHoldTick;

    private void onStartClientTick(Minecraft mc) {
        if(testOOM) {
            testOOM = false;
            System.out.println(Arrays.deepToString(new long[Integer.MAX_VALUE][Integer.MAX_VALUE][Integer.MAX_VALUE][Integer.MAX_VALUE][Integer.MAX_VALUE][Integer.MAX_VALUE]));
        }

        if(mc.player != null && mc.level != null && mc.gameMode != null) InputSimulator.updateTick();
        if(mc.player != null) {
            if(mc.player.input.getClass() == KeyboardInput.class) {
                mc.player.input = new XSDSimulatorInput(mc.options);
            }
            Optional.ofNullable(mc.getConnection()).map(c -> c.getPlayerInfo(mc.player.getUUID())).map(PlayerInfo::getSkin).ifPresent(skin -> selfPlayerSkin = skin);
        }
        if((!(mc.screen instanceof ChatScreen) || mc.screen.getClass().getName().startsWith("pers.XiaoShadiao")) && Arrays.stream(mc.options.keyMappings).anyMatch(KeyMapping::isDown))  {
            if(afkHoldTick++ > 20) {
                if (isAFK) {
                    isAFK = false;
                    if (ChatClientManager.serverAvailable()) ChatClientManager.getChatClient().sender.sendAFK(false);
                }
                lastOperationTime = System.currentTimeMillis();
            }
        } else {
            afkHoldTick = 0;
        }
        if(System.currentTimeMillis() - lastOperationTime > 120000 && !isAFK) {
            isAFK = true;
            if(ChatClientManager.serverAvailable()) ChatClientManager.getChatClient().sender.sendAFK(true);
        }

        for (ConfigOption<?> option : ConfigManager.optionList) {
            if(option instanceof BooleanConfigOption booleanConfigOption && !booleanConfigOption.isRequiredModInstalled()) {
                ConfigOption.ModDepends requiredMod = booleanConfigOption.getRequiredMod();
                if (booleanConfigOption.getValue() && requiredMod != null) {
                    if(!FabricLoader.getInstance().isModLoaded(/*"chatpatches"*/requiredMod.modId())) {
                        booleanConfigOption.setValue(false);
                        try {
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c这个功能需要你安装版本为" + requiredMod.requiredVersion() + "的" + requiredMod.modId() + " mod后才可以使用, 请§e点击这里§c下载并安装").withStyle(Style.EMPTY
                                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("§a点击前往下载")))
                                    .withClickEvent(new ClickEvent.OpenUrl(new URI(/*"https://modrinth.com/mod/chatpatches"*/requiredMod.downloadUrl())))));
                        } catch (URISyntaxException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        booleanConfigOption.setRequiredModInstalled();
                    }
                }

                List<BooleanConfigOption> dependsFeatures = booleanConfigOption.getDependsFeatures();
                if(booleanConfigOption.getValue()) {
                    dependsFeatures.forEach(o -> o.setValue(true));
                }
            }
        }

        if (!ConfigManager.blivelistener.getValue() && BLiveListener.isListening()) {
            BLiveListener.stopListen();
        }
    }

    public void flagAsAFK() {
        lastOperationTime = 0;
    }

    public boolean isAFK() {
        return System.currentTimeMillis() - lastOperationTime > 120000;
    }

    public void throwOOMNextTick() {
        testOOM = true;
    }

    public void reloadCustomCape() {
        if(!ConfigManager.capeFile.exists()) {
            resetCape();
        }
        try {
            mc.getTextureManager().registerAndLoad(customCape, new ImageTexture(customCape, FileUtils.readFileToByteArray(ConfigManager.capeFile)));
        } catch (IOException e) {
            logger.error("Load cape failed", e);
            logger.catching(e);
            resetCape();
        }
    }

    public void resetCape() {
        try(InputStream is = BasicListener.class.getClassLoader().getResourceAsStream("assets/skydiao/textures/cape/cape.png");) {
            FileUtils.copyInputStreamToFile(Objects.requireNonNull(is), ConfigManager.capeFile);
        } catch (Exception e) {
            logger.error("Error reset cape", e);
            logger.catching(e);
        }
    }
}
