package pers.XiaoShadiao.skydiao.utils.partyfinder;

import pers.XiaoShadiao.skydiao.irc.XSDIRCConfig;
import pers.XiaoShadiao.skydiao.ircinner.api.v1.ChatClientManager;
import pers.XiaoShadiao.skydiao.ircinner.api.v1.ChatPacket;

public class PartyFinderManager {

    public static final PartyFinderData selfPartyData = new PartyFinderData();

    public static boolean isPublic = false;

    public static void joinPlayerNameParty(String leaderPlayerName) {
        ChatPacket packet = new ChatPacket();
        packet.packetType = "find_party_let_me_in";
        packet.message = leaderPlayerName;
        packet.initSender();
        ChatClientManager.trySendOrWarning(packet, XSDIRCConfig.INSTANCE);
    }

}
