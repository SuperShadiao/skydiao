package pers.XiaoShadiao.skydiao.irc;

import com.google.gson.JsonParser;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import pers.XiaoShadiao.skydiao.SkyDiaoModClient;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.ICustomSkinModelLoader;
import pers.XiaoShadiao.skydiao.ircinner.api.v1.ChatClientManager;
import pers.XiaoShadiao.skydiao.ircinner.api.v1.ChatPacket;
import pers.XiaoShadiao.skydiao.ircinner.api.v1.IIRCConfig;
import pers.XiaoShadiao.skydiao.screen.partyfinder.PartyListScreen;
import pers.XiaoShadiao.skydiao.utils.OnlinePlayerList;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;
import pers.XiaoShadiao.skydiao.utils.mircosoftaccount.XSDSafeSession;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicInfo;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicListManager;
import pers.XiaoShadiao.skydiao.utils.partyfinder.PartyFinderData;
import pers.XiaoShadiao.skydiao.utils.partyfinder.PartyFinderManager;

import java.util.Arrays;
import java.util.Iterator;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class XSDIRCConfig implements IIRCConfig {

    public static final XSDIRCConfig INSTANCE = new XSDIRCConfig();

    @Override
    public String getClientNameAndVersion() {
        return "hhskb " + SkyDiaoModClient.VERSION;
    }

    @Override
    public Consumer<UUID> getAuthConsumer() {
        return uuid -> {
            if(ToolList.mc.getUser() instanceof XSDSafeSession xdsafeSession) {
                xdsafeSession.checkTokenVaild(uuid).join();
            }
        };
    }

    @Override
    public Runnable getOnInit() {
        return () -> {
            ToolList.getInstance().updatePartyInfo();
            for (ICustomSkinModelLoader modelLoaderAdapter : AbstractListener.modelLoaderAdapters) {
                if(modelLoaderAdapter.isSupportYSM()) modelLoaderAdapter.resendSwitchPacket();
            }
        };
    }

    @Override
    public Consumer<ChatPacket> getExtraPacketConsumer() {
        return this::handleExtraPacket;
    }

    @Override
    public Runnable getOnClose() {
        return OnlinePlayerList::clearPlayers;
    }

    @Override
    public IHypixelOnlineInfo getHypixelOnlineInfo() {
        return StatusManager.get();
    }

    @Override
    public boolean enableSpecialLog() {
        return ToolList.getInstance().isXiaoShadiao();
    }

    @Override
    public boolean isPlayerBlocked(String sender) {
        return Arrays.stream(ConfigManager.ircBlockPlayers.getValue().split(" ")).filter(a -> !a.isBlank()).anyMatch(s -> s.equalsIgnoreCase(sender));
    }

    @Override
    public boolean enableCommandTip() {
        return ConfigManager.enablexsdccommandtip.getValue();
    }

    @Override
    public boolean enableJoinTip() {
        return ConfigManager.enableircjointip.getValue();
    }

    @Override
    public boolean enableAFKTip() {
        return ConfigManager.enableircafktip.getValue();
    }

    @Override
    public boolean enableMacroCheckTip() {
        return ConfigManager.enableircmacrochecktip.getValue();
    }

    @Override
    public Function<String, String> getTranslator() {
        return CrowdinI18nManager::translate;
    }

    @Override
    public BiFunction<String, String[], String> getTranslatorWithArgs() {
        return CrowdinI18nManager::translate;
    }

    @Override
    public Consumer<ChatPacket> getPlayerListConsumer() {
        return packet -> {
            switch(packet.packetType) {
                case "join":
                    if(!packet.getRank(false).contains("[离线]")) OnlinePlayerList.addPlayer(packet.sender, packet.senderUUID, packet.message);
                    break;
                case "leave":
                    OnlinePlayerList.removePlayerByUUID(packet.senderUUID);
                    break;
                case "getplayers":
                    OnlinePlayerList.readFromPacket(packet);
                    break;
                default:
                    break;
            }
        };
    }

    @Override
    public Runnable getMentionNotifier() {
        return () -> ToolList.getInstance().playSound(SoundEvents.EXPERIENCE_ORB_PICKUP);
    }

    @Override
    public boolean isAFK() {
        return AbstractListener.basicListener.isAFK();
    }

    private void handleExtraPacket(ChatPacket packet) {
        switch(packet.packetType) {
            case "glacite_mineshaft_share":
                AbstractListener.mineshaftShareListener.onIRCMineshaftSharePacket(packet);
                break;
            case "hyp_party":
                ToolList.getInstance().updatePartyInfo();
                break;
            case "slayer_together":
                AbstractListener.slayerTogetherListener.onSlayerTogetherPacket(packet);
                break;
            case "ysm":
                for (ICustomSkinModelLoader modelLoaderAdapter : AbstractListener.modelLoaderAdapters) {
                    if(modelLoaderAdapter.isSupportYSM()) modelLoaderAdapter.handleIRCPacket(packet);
                }
                break;
            case "sharemusic":
                MusicInfo musicInfo = MusicListManager.jsonToMI(JsonParser.parseString(packet.message).getAsJsonObject());
                int shareId = MusicListManager.storeTempSharingMusic(musicInfo);
                MutableComponent component = Component.literal("§a[XSDChat] " + packet.getRank(true) + "§d" + packet.sender + " §a分享了音乐 §6" + musicInfo.name + " - " + musicInfo.singer);
                MutableComponent component2 = Component.literal("§a[XSDChat] §e点击这里§a可以收听!");

                Style style1 = Style.EMPTY
                        .withClickEvent(new ClickEvent.RunCommand("/skydiao listensharemusic " + shareId))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("/skydiao listensharemusic " + shareId)));
                ToolList.printChatMessage(component.withStyle(style1));
                ToolList.printChatMessage(component2.withStyle(style1));
                break;
            case "find_party_check_list":
                if(PartyFinderManager.isPublic && StatusManager.get().hasStatus()) {
                    PartyFinderData clone = PartyFinderManager.selfPartyData.clone();
                    clone.requestKey = packet.message;
                    ChatPacket packet2 = new ChatPacket();
                    packet2.packetType = "find_party_check_callbackinfo";
                    packet2.message = clone.toJson().toString();
                    packet2.initSender();
                    ChatClientManager.trySendOrWarning(packet2, true, this);
                }
                break;
            case "find_party_check_callbackinfo":
                PartyFinderData data = PartyFinderData.fromJson(JsonParser.parseString(packet.message).getAsJsonObject());
                if(ToolList.mc.gui.screen() instanceof PartyListScreen partyListScreen) {
                    partyListScreen.addData(data);
                }
                break;
            case "find_party_let_me_in":
                if(ToolList.mc.getUser().getName().equalsIgnoreCase(packet.message) && StatusManager.get().hasStatus() && PartyFinderManager.isPublic && !PartyFinderManager.selfPartyData.isPartyFull() && !PartyFinderManager.selfPartyData.players.contains(packet.sender)) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §e" + packet.sender + "§a请求加入你的队伍!"));
                    ToolList.sendChatMessage("/p invite " + packet.sender);

                    ChatPacket packet2 = new ChatPacket();
                    packet2.packetType = "find_party_please_join";
                    packet2.message = packet.sender;
                    packet2.initSender();
                    ChatClientManager.trySendOrWarning(packet2, true, this);
                    PartyFinderManager.selfPartyData.players.add(packet.sender);
                    try { Thread.sleep(2000); } catch (InterruptedException e) {}
                    ToolList.getInstance().updatePartyInfo();
                }
                break;
            case "find_party_please_join":
                if(ToolList.mc.getUser().getName().equalsIgnoreCase(packet.message)) {
                    ToolList.addThreadedTask(() -> {
                        ToolList.sendChatMessage("/p leave");
                        Thread.sleep(1000);
                        ToolList.sendChatMessage("/p join " + packet.sender);
                        return null;
                    });
                }
                break;
            case "find_party_broadcast":
                data = PartyFinderData.fromJson(JsonParser.parseString(packet.message).getAsJsonObject());
                MutableComponent msg = Component.literal("§a[小沙雕] §e" + packet.sender + "§a的§r")
                        .append(Component.literal(data.partyType.displayName).withColor(data.partyType.color.getRGB()))
                        .append("§a队伍[");
                Iterator<PartyFinderData.SubType> iterator = data.getOrderedPartySubTypes().iterator();
                while(iterator.hasNext()) {
                    PartyFinderData.SubType subType = iterator.next();
                    msg.append(Component.literal(subType.displayName).withColor(subType.color.getRGB()));
                    if(iterator.hasNext()) msg.append(Component.literal("§r, "));
                }
                ToolList.printChatMessage(
                        msg.append("§a]可以加入, 当前人数(" + data.players.size() + "/" + data.maxPlayers + "), 使用§e/skydiaofp join " + data.leaderPlayerName + "§a加入队伍!")
                                .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal("队伍备注: " + data.description + "\n\n§e点击快速填充指令"))).withClickEvent(new ClickEvent.SuggestCommand("/skydiaofp join " + data.leaderPlayerName)))
                );
                break;
            default:
                break;
        }
    }

}
