package pers.XiaoShadiao.skydiao.irc;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.ICustomSkinModelLoader;
import pers.XiaoShadiao.skydiao.screen.partyfinder.PartyListScreen;
import pers.XiaoShadiao.skydiao.utils.OnlinePlayerList;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicInfo;
import pers.XiaoShadiao.skydiao.utils.musicplayer.MusicListManager;
import pers.XiaoShadiao.skydiao.utils.partyfinder.PartyFinderData;
import pers.XiaoShadiao.skydiao.utils.partyfinder.PartyFinderManager;

import java.util.Arrays;
import java.util.Iterator;

public class ClientReceiveHandler {

    public void handle(ChatPacket packet, ClientListener sender) {
        if(Arrays.stream(ConfigManager.ircBlockPlayers.getValue().split(" ")).filter(a -> !a.isBlank()).anyMatch(s -> s.equalsIgnoreCase(packet.sender))) return;

        switch(packet.packetType) {
            case "chat":
                Style style = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal("点击来@" + packet.sender))).withClickEvent(new ClickEvent.SuggestCommand("/xsdc @" + packet.sender));
                ToolList.printChatMessage(Component.literal("§a[XSDChat] " + packet.getRank(true) + "§d" + packet.sender + "§7: §f" + packet.message).withStyle(style));
                if((packet.message.toLowerCase().contains("@" + ToolList.mc.getUser().getName().toLowerCase()) || (packet.message.toLowerCase().contains("@all") && packet.sender.equals("5i_XiaoShadiao"))) && !packet.getRank(false).contains("[离线]")) {
                    ToolList.getInstance().playSound(SoundEvents.EXPERIENCE_ORB_PICKUP);
                    ToolList.printChatMessage(Component.literal("§a[XSDChat] §e" + CrowdinI18nManager.translate("xsdchat.at")));
                }
                if(ConfigManager.enablexsdccommandtip.getValue()) ToolList.printChatMessage(Component.literal("§a[XSDChat] " + CrowdinI18nManager.translate("xsdchat.xsdctip")));
                break;
            case "system":
                ToolList.printChatMessage(Component.literal("§a[XSDChat] §c[SYSTEM] §f" + packet.message));
                break;
            case "join":
                if(ConfigManager.enableircjointip.getValue()) {
                    sender.announceAuthors();
                    ToolList.printChatMessage(Component.literal("§a[XSDChat] §7[§b+§7] " + packet.getRank(true) + "§b" + packet.sender + "..."));
                    if(ConfigManager.enablexsdccommandtip.getValue()) ToolList.printChatMessage(Component.literal("§a[XSDChat] " + CrowdinI18nManager.translate("xsdchat.xsdctip")));
                }
                if(!packet.getRank(false).contains("[离线]")) {
                    OnlinePlayerList.addPlayer(packet.sender, packet.senderUUID, packet.message);
                }
                break;
            case "leave":
                if(ConfigManager.enableircjointip.getValue()) ToolList.printChatMessage(Component.literal("§a[XSDChat] §7[§c-§7] " + packet.getRank(true) + "§c" + packet.sender + "..."));
                OnlinePlayerList.removePlayerByUUID(packet.senderUUID);
                break;
            case "heartbeat":
                sender.flagHeartbeat();
                break;
            case "afk":
                if(ConfigManager.enableircafktip.getValue()) ToolList.printChatMessage(Component.literal("§a[XSDChat] " + packet.getRank(true) + "§d" + packet.sender + (Boolean.parseBoolean(packet.message) ? " §7" + CrowdinI18nManager.translate("xsdchat.afk.in") : " §e" + CrowdinI18nManager.translate("xsdchat.afk.out"))));
                break;
            case "glacite_mineshaft_share":
                AbstractListener.mineshaftShareListener.onIRCMineshaftSharePacket(packet);
                break;
            case "macro_check":
                if(ConfigManager.enableircmacrochecktip.getValue()) {
                    MutableComponent component = Component.literal("§a[XSDChat] " + packet.getRank(true) + "§d" + packet.sender + " §c触发了Macro Check警报! §e[HOVER]");
                    style = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal(packet.message)));
                    ToolList.printChatMessage(component.setStyle(style));
                }
                break;
            case "hyp_party":
                ToolList.getInstance().updatePartyInfo();
                break;
            case "slayer_together":
                AbstractListener.slayerTogetherListener.onSlayerTogetherPacket(packet);
                break;
            case "showitem":
                if(ToolList.mc.level != null) {
                    try {
                        ItemStack is = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, ToolList.mc.level.registryAccess()), JsonParser.parseString(packet.message)).getOrThrow(JsonSyntaxException::new);
                        MutableComponent component = Component.literal("§a[XSDChat] " + packet.getRank(true) + "§d" + packet.sender + " §a展示容错 §e[HOVER]")
                                .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(is))));
                        ToolList.printChatMessage(component);
                    } catch (JsonSyntaxException e) {
                        e.printStackTrace();
                    }
                }
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
            case "getplayers":
                OnlinePlayerList.readFromPacket(packet);
                break;
            case "find_party_check_list":
                if(PartyFinderManager.isPublic && StatusManager.get().hasStatus()) {
                    PartyFinderData clone = PartyFinderManager.selfPartyData.clone();
                    clone.requestKey = packet.message;
                    ChatPacket packet2 = new ChatPacket();
                    packet2.packetType = "find_party_check_callbackinfo";
                    packet2.message = clone.toJson().toString();
                    packet2.initSender();
                    ChatClientManager.trySendOrWarning(packet2);
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
                    ChatClientManager.trySendOrWarning(packet2);
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
        }
    }

}
