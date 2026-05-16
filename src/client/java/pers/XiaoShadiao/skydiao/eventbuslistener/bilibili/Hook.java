package pers.XiaoShadiao.skydiao.eventbuslistener.bilibili;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import pers.XiaoShadiao.skydiao.fabriccustomevent.BLiveEvent;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import top.mrxiaom.bili.live.runtime.data.*;

public class Hook extends pers.XiaoShadiao.blive.Hook {
    @Override
    public void onReceivedDanmaku(Dm dm) {
        ToolList.getInstance().log.info("[Bilibili] 收到弹幕 -> " + dm.userName + ": " + dm.msg);
        if(!BLiveEvent.ON_RECEIVED_DANMAKU.invoker().on(dm)) {
            String usernameAndRanks = "§a" + dm.userName;
            if(ToolList.getInstance().stringHasContext(dm.fansMedalName)) usernameAndRanks = "§b[" + dm.fansMedalLevel + "] [" + dm.fansMedalName + "] " + usernameAndRanks;
            if(dm.isAdmin) usernameAndRanks = "§c[A] " + usernameAndRanks;
            if(dm.fansMedalWearingStatus) usernameAndRanks = "§a✔ " + usernameAndRanks;

            ToolList.printChatMessage(Component.literal("§d[Bilibili] " + usernameAndRanks + "§7: §f" + dm.msg));
        }
    }

    @Override
    public void onReceivedGift(SendGift sendGift) {
        ToolList.getInstance().log.info("[BiliBili] 收到" + sendGift.userName + "的礼物: " + sendGift.giftName + " x" + sendGift.giftNum + " (总价值: " + (sendGift.price / 1000f * sendGift.giftNum) + "r)");
        if(!BLiveEvent.ON_RECEIVED_GIFT.invoker().on(sendGift)) {
            ToolList.printChatMessage(Component.literal("§d[BiliBili] §a收到§6" + sendGift.userName + "§a的礼物: " + sendGift.giftName + " §ex" + sendGift.giftNum + " (总价值: " + (sendGift.price / 1000f * sendGift.giftNum) + "r)"));
        }
    }

    @Override
    public void onReceivedGuardBuy(Guard guard) {
        ToolList.getInstance().log.info("[BiliBili] GUARD! 收到" + guard.userInfo.userName + "的大航海" + guard.guardNum + guard.guardUnit + "!");
        if(!BLiveEvent.ON_RECEIVED_GUARD.invoker().on(guard)) {
            // 1总督 2提督 3舰长
            String guardName = switch ((int) guard.guardLevel) {
                case 1 -> "总督";
                case 2 -> "提督";
                case 3 -> "舰长";
                default -> "未知";
            };

            ToolList.printChatMessage(Component.literal("§d[BiliBili] "));
            ToolList.printChatMessage(Component.literal("§d[BiliBili] §f====================================="));
            ToolList.printChatMessage(Component.literal("§d[BiliBili] §6§kAAA"));
            ToolList.printChatMessage(Component.literal("§d[BiliBili] §6§kAAA§a > GUARD! 收到§6" + guard.userInfo.userName + "§a的"));
            ToolList.printChatMessage(Component.literal("§d[BiliBili] §6§kAAA§a > 大航海§c" + guard.guardNum + guard.guardUnit + " §a(身份为§b" + guardName + "§a)! §cGG WP!"));
            ToolList.printChatMessage(Component.literal("§d[BiliBili] §6§kVVV"));
            ToolList.printChatMessage(Component.literal("§d[BiliBili] §f====================================="));
            ToolList.printChatMessage(Component.literal("§d[BiliBili] "));

            ToolList.getInstance().playSound(SoundEvents.ENDER_DRAGON_GROWL);
            ToolList.getInstance().playSound(SoundEvents.ENDER_DRAGON_GROWL);

        }
    }

    @Override
    public void onReceivedSuperChat(SuperChat superChat) {
        BLiveEvent.ON_RECEIVED_SUPER_CHAT.invoker().on(superChat);
    }

    @Override
    public void onReceivedSuperChatDel(SuperChatDel superChatDel) {
        BLiveEvent.ON_RECEIVED_SUPER_CHAT_DEL.invoker().on(superChatDel);
    }

    @Override
    public void onReceivedLike(Like like) {
        ToolList.getInstance().log.info("[BiliBili] 收到" + like.userName + "的点赞: " + like.likeText + " x" + like.likeCount);
        if (!BLiveEvent.ON_RECEIVED_LIKE.invoker().on(like)) {
            ToolList.printChatMessage(Component.literal("§d[BiliBili] §a收到§6" + like.userName + "§a的点赞: " + like.likeText + " §ex" + like.likeCount));
        }
    }

    // {"data":{"uid":0,"timestamp":1737113164,"uname":"威化o","uface":"https://i0.hdslb.com/bfs/face/07f38f76c27b291e2b09f9c974df9dbb0aa633ad.jpg","open_id":"6fcb93f1399048d28e06c61e2c527916","msg_id":"44c9d31c-df2a-4219-9953-8eae9b9e0488","room_id":32674116},"cmd":"LIVE_OPEN_PLATFORM_LIVE_ROOM_ENTER"}
    @Override
    public void onReceivedRawNotice(String s, JsonObject jsonObject) {
        ToolList.getInstance().log.info(jsonObject);

        JsonObject jo = jsonObject.get("data").getAsJsonObject();
        String cmd = jsonObject.get("cmd").getAsString();
        switch (cmd) {
            case "LIVE_OPEN_PLATFORM_LIVE_ROOM_ENTER":
                BLiveEvent.MemberJoinLive event = new BLiveEvent.MemberJoinLive(jo.get("uname").getAsString());
                if(!BLiveEvent.ON_RECEIVED_MEMBER_JOIN_LIVE.invoker().on(event)) {
                    ToolList.getInstance().log.info("[BiliBili] " + event.name() + " 进入了直播间!");
                    ToolList.printChatMessage(Component.literal("§d[Bilibili] §6" + event.name() + "§a进入了直播间!"));
                }
                break;
        }

    }

    @Override
    public void onPopularityUpdate(int i) {
        BLiveListener.getInstance().keepAlive();
        ToolList.getInstance().log.info("onPopularityUpdate: " + i + ", $KeepAlive");
    }
}
