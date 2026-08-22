package pers.XiaoShadiao.skydiao.eventbuslistener;

import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.BLiveEvent;
import pers.XiaoShadiao.skydiao.utils.PartyManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import top.mrxiaom.bili.live.runtime.data.Dm;
import top.mrxiaom.bili.live.runtime.data.Guard;
import top.mrxiaom.bili.live.runtime.data.SendGift;

public class AutoBoardcastBliveDankmu extends AbstractListener {

    @Override
    public String getListenerName() {
        return "AutoBoardcastBliveDankmu";
    }

    @Override
    public void registerListeners() {
        BLiveEvent.ON_RECEIVED_DANMAKU.register(this::onReceivedDanmu);
        BLiveEvent.ON_RECEIVED_GIFT.register(this::onReceivedGift);
        BLiveEvent.ON_RECEIVED_GUARD.register(this::onReceivedGuard);
    }

    private boolean onReceivedGuard(Guard guard) {
        if(!ConfigManager.bliveboardcastdankumu.getValue() || !PartyManager.isInParty()) return false;

        // 1总督 2提督 3舰长
        String guardName = switch ((int) guard.guardLevel) {
            case 1 -> "总督";
            case 2 -> "提督";
            case 3 -> "舰长";
            default -> "未知";
        };

        ToolList.sendChatMessage("/pc " + "[BLive] GUARD! 收到" + guard.userInfo.userName + "的大航海" + guard.guardNum + guard.guardUnit + " (身份为§b" + guardName + "§a)! GG WP!");

        return false;
    }

    private boolean onReceivedGift(SendGift sendGift) {
        if(!ConfigManager.bliveboardcastdankumu.getValue() || !PartyManager.isInParty()) return false;

        ToolList.sendChatMessage("/pc " + "[BLive] 收到" + sendGift.userName + "的礼物: " + sendGift.giftName + " x" + sendGift.giftNum + " (总价值: " + (sendGift.price / 1000f * sendGift.giftNum) + "r)");

        return false;
    }

    private boolean onReceivedDanmu(Dm dm) {
        if(!ConfigManager.bliveboardcastdankumu.getValue() || !PartyManager.isInParty()) return false;
        String chatMessage = buildChatMessage(dm);
        if(chatMessage == null) return false;
        ToolList.sendChatMessage("/pc " + chatMessage);
        return false;
    }

    private String buildChatMessage(Dm dm) {
        if(dm.msg.matches("(.*\\d+.*\\d+.*\\d+.*|\\.)")) return null;
        String usernameAndRanks = "§a" + dm.userName;
        if(ToolList.getInstance().stringHasContext(dm.fansMedalName)) usernameAndRanks = "§b[" + dm.fansMedalLevel + "] [" + dm.fansMedalName + "] " + usernameAndRanks;
        if(dm.isAdmin) usernameAndRanks = "§c[A] " + usernameAndRanks;
        if(dm.fansMedalWearingStatus) usernameAndRanks = "§a✔ " + usernameAndRanks;

        return ToolList.getInstance().deleteColorCode("§d[BLive] " + usernameAndRanks + "§7: §f" + dm.msg);
    }

}
