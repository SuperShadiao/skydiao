package pers.XiaoShadiao.skydiao.utils;

import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.UUID;

public class Banned {

    public static void ban(BanReason reason, BanTime time, long delay) {
        ToolList.addThreadedTask(() -> {

            Thread.sleep(delay);

            ToolList.sendChatMessage("/limbo");
            MutableComponent msg = Component.literal("§cAn exception occurred in your connection, so you have been routed to the limbo!");

            Thread.sleep(500);
            ToolList.printChatMessage(msg);
            Thread.sleep(1000);
            ToolList.printChatMessage(msg);
            Thread.sleep(1500);

            MutableComponent c;
            if (time == BanTime.PERMANENT) {
                c = Component.literal("§cYou are permanently banned from this server!");
            } else {
                c = Component.literal("§cYou are temporarily banned for §f" + (time.day - 1) + "d 23h 59m 58s §cfrom this server!");
            }
            c.append("\n");
            c.append("\n§7Reason: §f" + reason.reason);
            c.append("\n§7Find out more: §b§nhttps://www.hypixel.net/appeal");
            c.append("\n");
            c.append("\n§7Ban ID: §f#" + (UUID.randomUUID().toString().toUpperCase().substring(0, 8)));
            c.append("\n§7Sharing your Ban ID may affect the processing of your appeal!");
            ToolList.mc.execute(() -> ToolList.mc.player.connection.onDisconnect(new DisconnectionDetails(c)));
            return null;

        });
    }

    public enum BanReason {
        CHEATING("Cheating through the use of unfair game advantages."),
        BOOSTING("Boosting your account to improve your stats."),
        SBBOOSTING("Boosting detected on one or multiple Skyblock profiles."),
        LOSTTTT("Lost Tic Tac Toc in Dungeon."),
        ;

        public final String reason;

        BanReason(String reason) {
            this.reason = reason;
        }
    }

    public enum BanTime {
        PERMANENT(-1),
        _30_DAYS(30),
        _90_DAYS(90),
        _180_DAYS(180),
        _360_DAYS(360),
        ;

        public final int day;

        BanTime(int d) {
            day = d;
        }
    }

}
