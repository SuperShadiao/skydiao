package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.loader.api.FabricLoader;
import net.hypixel.modapi.packet.ClientboundHypixelPacket;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import org.apache.commons.io.FileUtils;
import pers.XiaoShadiao.skydiao.SkyDiaoModClient;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.config.option.BooleanConfigOption;
import pers.XiaoShadiao.skydiao.config.option.ConfigOption;
import pers.XiaoShadiao.skydiao.eventbuslistener.bilibili.BLiveListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.irc.ChatPacket;
import pers.XiaoShadiao.skydiao.keybinds.KeyBindsManager;
import pers.XiaoShadiao.skydiao.screen.mircosoftaccount.AccountSelectScreen;
import pers.XiaoShadiao.skydiao.utils.*;
import pers.XiaoShadiao.skydiao.utils.autoupdater.AutoUpdater;
import pers.XiaoShadiao.skydiao.utils.blivesensitiveword.ServerIdSpoofer;
import pers.XiaoShadiao.skydiao.utils.mircosoftaccount.MinecraftLogin;
import pers.XiaoShadiao.skydiao.utils.mircosoftaccount.XSDSafeSession;
import pers.XiaoShadiao.skydiao.utils.musicplayer.PlayerThread;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;
import pers.XiaoShadiao.skydiao.utils.playerinput.XSDSimulatorInput;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.Gif;
import pers.XiaoShadiao.skydiao.utils.renderutils.ImageTexture;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
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
    private ServerData currentServerData;

    @Override
    public String getListenerName() {
        return "BasicListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(this::onWorldChange);
        CustomFabricEvents.HYPIXEL_PACKET_EVENT.register(this::onHypixelPacket);
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        ScreenEvents.AFTER_INIT.register(this::onGuiFinishedInit);
        ClientPlayConnectionEvents.JOIN.register(this::onJoinServer);
        ClientPlayConnectionEvents.DISCONNECT.register(this::onDisconnect);
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
        CustomFabricEvents.CLIENT_SEND_CANCELLABLE_PACKET_EVENT.register(this::onSendFirmPacket);
        ClientReceiveMessageEvents.MODIFY_GAME.register(this::onModifyChat);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        ClientLifecycleEvents.CLIENT_STARTED.register((mc) -> {
            PlayerThread.createThread();
            MinecraftLogin.checkSessionExpiredAndLogin();

            if (ConfigManager.blivelistener.getValue()) {
                BLiveListener.launch();
            }
        });
    }

    private static final Identifier firmModListPayload = Identifier.fromNamespaceAndPath("firmament", "mod_list");
    private boolean onSendFirmPacket(Packet<?> packet) {
        if(packet instanceof ServerboundCustomPayloadPacket(CustomPacketPayload payload)) {
            if (firmModListPayload.equals(payload.type().id())) {
                logger.info("已拦截Firmament的告状行为 (Connection)");
                return true;
            }
        }
        return false;
    }

    private ClientPacketListener packetSender;

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if(packet instanceof ClientboundResourcePackPushPacket packet2) {
            logger.info("自定义材质包: {}", packet2.url());
            boolean inSkyblock = false;
            if (packet2.prompt().isPresent()) {
                inSkyblock = packet2.prompt().get().getString().toLowerCase().contains("skyblock");
            }
            if(inSkyblock) {
                ToolList.addThreadedTask(() -> {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException _) {}
                    List<String> errorLines = new ArrayList<>();
                    try {
                        HashFunction hashFunction = null;
                        HashCode hashCode = null;
                        boolean disableHashCheck = false;
                        try {
                            hashFunction = Hashing.sha1();
                            hashCode = HashCode.fromString(packet2.hash());
                        } catch (Exception e) {
                            logger.catching(e);
                            disableHashCheck = true;
                        }

                        File temp = File.createTempFile("pack1", ".zip");
                        File target = new File(mc.getResourcePackDirectory().toFile(), "hypixel_resoucepack.zip");
                        ToolList.printChatMessage(Component.literal("§a[小沙雕] §eHypixel官材: " + packet2.url()));
                        if(disableHashCheck || !target.isFile() || !hashFunction.hashBytes(FileUtils.readFileToByteArray(target)).equals(hashCode)) {
                            XSDHUD.bigTitle.updateTitleMsg("§e正在获取Hypixel官材...如果长时间未完成, 请检查网络", 120000);
                            InputStream source = ToolList.getInstance().makeReqToURL(packet2.url());
                            URLFetchProcess process = new URLFetchProcess(source);
                            titleChanger.downloadProcess.addProcess(process);
                            FileUtils.writeByteArrayToFile(temp, process.readAllBytes());
                            // System.out.println(mc.getResourcePackRepository().getAvailableIds());
                            // [cardinal-components-base, cardinal-components-entity, dandelion, fabric-api, fabric-api-base, fabric-api-lookup-api-v1, fabric-biome-api-v1, fabric-block-api-v1, fabric-block-getter-api-v2, fabric-client-gametest-api-v1, fabric-command-api-v2, fabric-content-registries-v0, fabric-convention-tags-v2, fabric-crash-report-info-v1, fabric-creative-tab-api-v1, fabric-creative-tab-api-v1_programmer_art, fabric-data-attachment-api-v1, fabric-data-generation-api-v1, fabric-debug-api-v1, fabric-dimensions-v1, fabric-entity-events-v1, fabric-events-interaction-v0, fabric-game-rule-api-v1, fabric-gametest-api-v1, fabric-item-api-v1, fabric-key-mapping-api-v1, fabric-language-kotlin, fabric-lifecycle-events-v1, fabric-loot-api-v3, fabric-menu-api-v1, fabric-message-api-v1, fabric-model-loading-api-v1, fabric-networking-api-v1, fabric-object-builder-api-v1, fabric-particles-v1, fabric-permission-api-v1, fabric-recipe-api-v1, fabric-registry-sync-v0, fabric-renderer-api-v1, fabric-renderer-indigo, fabric-rendering-fluids-v1, fabric-rendering-v1, fabric-resource-conditions-api-v1, fabric-resource-loader-v0, fabric-resource-loader-v1, fabric-screen-api-v1, fabric-serialization-api-v1, fabric-sound-api-v1, fabric-tag-api-v1, fabric-transfer-api-v1, fabric-transitive-access-wideners-v1, fabricloader, file/hypixel_resoucepack.zip, forgeconfigapiport, high_contrast, hm-api, hypixel-mod-api, iris, modmenu, modmenu_high_contrast, modmenu_programmer_art, org_apache_commons_commons-math3, placeholder-api, programmer_art, skyblockaddons, skyblocker, skyblocker:recolored_dungeon_items, skydiao, skyhanni, sodium, sparkle_morpher, vanilla, yet_another_config_lib_v3]
                            if (!FileUtils.contentEquals(temp, target)) {
                                boolean executedRemove = false;
                                if(mc.getResourcePackRepository().removePack("file/hypixel_resoucepack.zip")) {
                                    mc.reloadResourcePacks().get();
                                    executedRemove = true;
                                }
                                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                if(executedRemove) {
                                    mc.getResourcePackRepository().addPack("file/hypixel_resoucepack.zip");
                                    mc.reloadResourcePacks().get();
                                }
                            }
                            XSDHUD.bigTitle.updateTitleMsg("§eHyp官材获取成功, 请前往材质包页面查看", 3000);
                        }
                    } catch (Exception e) {
                        errorLines.add("下载Hypixel官材失败, 请检查你的网络后重新进入Skyblock: " + e);
                        errorLines.add("如果提示文件已被占用, 则当前官方材质包已发生更新, 请前往材质包选择页面卸载材质包后重新进入Skyblock, 并在弹出消息后重新安装材质包!");
                        logger.catching(e);
                    }
                    try {
                        File temp = File.createTempFile("pack2", ".zip");
                        File target = new File(mc.getResourcePackDirectory().toFile(), "SkyBlock Legacy.zip");
                        InputStream source = ToolList.getInstance().makeReqToURL("https://xiaoshadiao.club/3rd_lib/pack/SkyBlockLegacy.zip");
                        URLFetchProcess process = new URLFetchProcess(source);
                        titleChanger.downloadProcess.addProcess(process);
                        FileUtils.writeByteArrayToFile(temp, process.readAllBytes());
                        if(!FileUtils.contentEquals(temp, target)) {
                            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            XSDHUD.bigTitle.updateTitleMsg("§eLegacy (原版) 材质包获取成功, 请前往材质包页面查看", 3000);
                        }
                    } catch (IOException e) {
                        errorLines.add("下载Legacy (原版) 材质包失败, 请检查你的网络后重新进入Skyblock: " + e);
                        errorLines.add("如果提示文件已被占用, 则当前官方材质包已发生更新, 请前往材质包选择页面卸载材质包后重新进入Skyblock, 并在弹出消息后重新安装材质包!");
                        logger.catching(e);
                    }

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException _) {

                    }
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §e注意: 小沙雕移除了Hypixel自带的资源包, 并将其写入了材质包文件夹, 如果你遇到黑紫色材质错误, 请在材质包选择页面手动安装 (优先级可以设置), 其中hypixel pack是官方材质包, legacy pack是将物品恢复为原版材质包"));
                    for (String errorLine : errorLines) {
                        ToolList.printChatMessage(Component.literal("§a[小沙雕] §c" + errorLine));
                    }
                }, null);
                mc.execute(() -> packetSender.send(new ServerboundResourcePackPacket(packet2.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED)));
                return true;
            }
        } else if(packet instanceof ClientboundDisconnectPacket(Component reason)) {
            ClientPacketListener connection = mc.getConnection();
            if(connection != null) {
                currentServerData = connection.getServerData();
            }
        }

        return false;
    }

    private Component onModifyChat(Component component, boolean b) {
        if(ToolList.getInstance().random.nextInt(10) == 5 && component.getString().contains("§aGalatea") && component instanceof MutableComponent m) {
            MutableComponent component1 = Component.literal(m.getString().replace("§aGalatea", "§aGalgame"));
            component1.getSiblings().forEach(m::append);
            component1.setStyle(m.getStyle());
            return component1;
        }
        return component;
    }

    private void onLastRender(LevelRenderContext context) {
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

        if(message.toLowerCase().contains(":") && !message.toLowerCase().startsWith("profile id:")) {
            Matcher m = Pattern.compile("[a-zA-Z]+").matcher(message);
            while(m.find()) {
                String words = m.group();
                if(words.toLowerCase().contains("discord") || words.toLowerCase().endsWith("dc") || (words.toLowerCase().contains("dc") && words.length() <= 3)) {
                    MutableComponent ic = Component.literal("§a[小沙雕] §c" + translate("features.antiscammer.notification"));
                    Style cs = Style.EMPTY
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://xiaoshadiao.club/antiscamming")))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(translate("features.antiscammer.click2"))));
                    MutableComponent ic2 = Component.literal(" §e" + translate("features.antiscammer.click")).setStyle(cs);

                    ToolList.printChatMessage(ic.append(ic2));

                    ToolList.printChatMessage(Component.literal(b + ": " + message));
                    break;
                }
            }
        }
    }

    private void onDisconnect(ClientPacketListener clientPacketListener, Minecraft minecraft) {
        logger.info("已断开服务器连接: " + clientPacketListener.getConnection().getRemoteAddress());
        isConnectedToServer = false;

        mc.execute(CustomRenderPipeline::closeAll);
        StatusManager.cleanHypixelPacket();
        StatusManager.destory();

        ToolList.destroy();

        if(ToolList.getInstance().isDevEnvironment()) Gif.clearCache();
    }

    private void onJoinServer(ClientPacketListener clientPacketListener, PacketSender packetSender, Minecraft minecraft) {
        this.packetSender = clientPacketListener;
        if(!isConnectedToServer) {
            isConnectedToServer = true;
            SkyblockBlacklistManager.updateBlacklist();
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
                        checkOtherUpdates(update);
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

    private Button reconnectButton;
    private Button autoReconnectSwitchButton;
    private int reconnectCountDown = 0;
    private long lastCheckSessionTime = System.currentTimeMillis();
    private void onGuiFinishedInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if(screen instanceof TitleScreen titleScreen) {
            List<AbstractWidget> buttons = Screens.getWidgets(titleScreen);
            int left = buttons.stream().min(Comparator.comparingInt(AbstractWidget::getX)).get().getX();
            if(Util.getPlatform() == Util.OS.WINDOWS) {
                buttons.add(Button.builder(
                        AccountSelectScreen.getTitle0(),
                        (button) -> ToolList.mc.setScreen(new AccountSelectScreen(titleScreen))
                ).bounds(scaledHeight < 270 ? 10 : scaledWidth / 2 - 50, scaledHeight - (scaledHeight < 270 ? 35 : 28), scaledHeight < 270 ? Math.min(100, left - 15) : 100, 20).build());
            }
        } else if(screen instanceof JoinMultiplayerScreen mpscreen) {
            if(System.currentTimeMillis() - lastCheckSessionTime > 60000) {
                lastCheckSessionTime = System.currentTimeMillis();
                MinecraftLogin.checkSessionExpiredAndLogin();
            }
            currentServerData = null;
        } else if(screen instanceof DeathScreen deathScreen) {
            List<AbstractWidget> buttons = Screens.getWidgets(deathScreen);
            int left = buttons.stream().min(Comparator.comparingInt(AbstractWidget::getX)).get().getX();

            buttons.add(Button.builder(
                    Component.literal("§a打开聊天栏"),
                    (button) -> ToolList.mc.setScreen(new ChatScreen("/l", false))
            ).bounds(scaledHeight < 270 ? 10 : scaledWidth / 2 - 50, scaledHeight - (scaledHeight < 270 ? 35 : 28), scaledHeight < 270 ? Math.min(100, left - 15) : 100, 20).build());
        } else if(screen instanceof DisconnectedScreen disconnectedScreen && currentServerData != null) {
            List<AbstractWidget> buttons = Screens.getWidgets(disconnectedScreen);
            buttons.remove(reconnectButton);
            buttons.remove(autoReconnectSwitchButton);
            AbstractWidget widget = buttons.stream().max(Comparator.comparingInt(AbstractWidget::getY)).get();
            int top = widget.getY();
            int left = widget.getX();

            int height = widget.getHeight();
            int width = widget.getWidth();

            buttons.add(reconnectButton = Button.builder(
                    Component.literal(translate("gui.disconnected.buttonreconnect", ""/*" (" + (int) (重连倒计时 / 40) + "s)"*/)),
                    (button) -> ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(currentServerData.ip), currentServerData, false, null)
            ).bounds(left, top + 25, width, height).build());

            buttons.add(autoReconnectSwitchButton = Button.builder(
                    Component.literal(ConfigManager.autoReconnect.getI18nName() + ": " + ConfigManager.autoReconnect.getI18nValue()),
                    (button) -> {
                        ConfigManager.autoReconnect.setValue(!ConfigManager.autoReconnect.getValue());
                        button.setMessage(Component.literal(ConfigManager.autoReconnect.getI18nName() + ": " + ConfigManager.autoReconnect.getI18nValue()));
                    }
            ).bounds(5, scaledHeight - 25, 100, 20).build());

            reconnectCountDown = 60;
        }
    }

    public void setCurrentServerData(ServerData serverData) {
        currentServerData = serverData;
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
            PartyManager.updatePartyMembers(partyInfoPacket.isInParty() ? new ArrayList<>(partyInfoPacket.getMemberMap().values()) : List.of());
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

        if(SkyblockBlacklistManager.getInstance().isDone() && !SkyblockBlacklistManager.getInstance().isAvaliable()) SkyblockBlacklistManager.updateBlacklist();

        if(ToolList.getInstance().isDevEnvironment()) Gif.clearCache();
    }

    private int afkHoldTick;

    private void onStartClientTick(Minecraft mc) {
        User user = mc.getUser();
        if(!(user instanceof XSDSafeSession)) MinecraftLogin.replaceSession(user);
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
        if(ConfigManager.keepSprint.getValue() && !MacroManagerListener.autoDojo.isDoingSwiftness()) {
            InputSimulator.setSprint(true);
        }
        while(KeyBindsManager.toggleKeepSprint.consumeClick()) {
            ConfigManager.keepSprint.setValue(!ConfigManager.keepSprint.getValue());
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
                if(booleanConfigOption.isMacroFeature() && booleanConfigOption.getValue()) {
                    float volume = mc.options.getSoundSourceOptionInstance(SoundSource.PLAYERS).get().floatValue();
                    if(volume < 0.2) {
                        booleanConfigOption.setValue(false);
                        ToolList.printChatMessage(Component.literal("§a[小沙雕] §c⚠ 警告: 若要开启Macro类功能, 你必须在声音设置里将玩家声音调整到§a20%§c以上! (当前为§e" + (volume * 100) + "%§c)"));
                        ToolList.printChatMessage(Component.literal("§a[小沙雕] §c⚠ 功能§e" + booleanConfigOption.getI18nName() + "§c暂时被关闭!"));
                        ToolList.printChatMessage(Component.literal("§a[小沙雕] §c⚠ 在你设置完后确保使用§a/skydiao playalert§c且能清楚听见声音!"));
                    }
                }
            }
        }

        if (!ConfigManager.blivelistener.getValue() && BLiveListener.isListening()) {
            BLiveListener.stopListen();
        }

        if(ConfigManager.autoReconnect.getValue()) {
            if (currentServerData != null && reconnectButton != null && reconnectCountDown > 0) {
                reconnectCountDown--;
                reconnectButton.setMessage(Component.literal(translate("gui.disconnected.buttonreconnect", " (" + (int) (reconnectCountDown / 20) + "s)")));
                if (reconnectCountDown == 0) {
                    ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(currentServerData.ip), currentServerData, false, null);
                }
            }
        } else {
            reconnectCountDown = 60;
            if (reconnectButton != null) {
                reconnectButton.setMessage(Component.literal(translate("gui.disconnected.buttonreconnect", "")));
            }
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

    private String spmVersion = null;

    public void setSPMVersion(String spmVersion) {
        this.spmVersion = spmVersion;
    }

    private void checkOtherUpdates(AutoUpdater update) {
        if (FabricLoader.getInstance().isModLoaded("sparkle_morpher") && spmVersion != null) {
            FabricLoader.getInstance().getModContainer("sparkle_morpher").ifPresent(c -> {
                if(!Objects.equals(spmVersion, c.getMetadata().getVersion().getFriendlyString())) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] Sparkle Morpher (YSM) 新版本可用: " + spmVersion));
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] 访问下载:§e https://5ixsd.top/spmmod"));
                }
            });
        }
    }
}
