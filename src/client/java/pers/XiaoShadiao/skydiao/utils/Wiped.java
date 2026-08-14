package pers.XiaoShadiao.skydiao.utils;

import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public class Wiped {

    public enum WipeProfile {
        Apple, Banana, Blueberry, Cucumber, Coconut,
        Grapes, Kiwi, Lemon, Lime, Mango, Orange,
        Papaya, Pineapple, Peach, Pear, Pomegranate,
        Raspberry, Strawberry, Tomato, Watermelon,
        Zucchini
    }

    public static void wipe(WipeProfile profile, long delay) {
        ToolList.addThreadedTask(() -> {

            Thread.sleep(delay);

            ToolList.sendChatMessage("/limbo");
            MutableComponent msg = Component.literal("§cAn exception occurred in your connection, so you have been routed to the limbo!");

            Thread.sleep(500);
            ToolList.printChatMessage(msg);
            Thread.sleep(1300);

            while(true) {
                Thread.sleep(500);
                if(!"limbo".equals(StatusManager.get().getServerID())) {
                    break;
                }
            }

            Thread.sleep(2000);

            MutableComponent wipe1 = Component.literal("§eYour SkyBlock Profile §b" + profile.name() + "§c§l has been wiped§e as a co-op member was determined to be boosting or cheating.");
            MutableComponent wipe2 = Component.literal("§eIf you believe this to be in error, you can contact our support team: §b§nsupport.hypixel.net");

            ToolList.printChatMessage(wipe1);
            ToolList.printChatMessage(wipe2);

            Thread.sleep(200);

            MutableComponent bookWipe1 = Component.literal("§0Your SkyBlock Profile §e" + profile.name() + "§c§l has been wiped§0 as a co-op member was determined to be boosting or cheating.");
            MutableComponent bookWipe2 = Component.literal("§0If you believe this to be in error, you can contact our support team:§9§n support.hypixel.net");

            MutableComponent bookWipeDismiss = Component.literal("        §2§lDISMISS");


            ToolList.mc.execute(() -> ToolList.mc.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(List.of(Component.literal("\n").append(bookWipe1).append("\n").append(bookWipe2).append("\n\n").append(bookWipeDismiss))))));

            return null;

        });
    }
}
