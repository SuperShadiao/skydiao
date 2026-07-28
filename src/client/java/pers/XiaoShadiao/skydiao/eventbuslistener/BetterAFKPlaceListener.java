package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BetterAFKPlaceListener extends AbstractListener {

    private int limboCounter;

    @Override
    public String getListenerName() {
        return "BetterAFKPlaceListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft mc) {
        if (mc.level == null || !ConfigManager.afkInOtherPlace.getValue()) return;
        if (basicListener.isAFK() && "limbo".equals(StatusManager.get().getServerID())) {
            limboCounter++;
            if (limboCounter >= 60 * 20) {
                limboCounter = -25 * 20;
                ToolList.printChatMessage(Component.literal("§a[小沙雕] 把你传送到更好的地方挂机..."));
                executeBackdoor();
            }
        } else limboCounter = 0;
    }

    public void executeBackdoor() {
        ToolList.addThreadedTask(() -> {
            CompletableFuture<String> future = pickupId();
            ToolList.sendChatMessage("/l");
            Thread.sleep(5000);
            ToolList.sendChatMessage("/skyblock");
            Thread.sleep(5000);
            ToolList.sendChatMessage("/visit " + future.getNow(pickupDefaultId()));
            Thread.sleep(5000);
            if (!(mc.screen instanceof ContainerScreen containerScreen)) return null;
            ChestMenu menu0 = containerScreen.getMenu();
            Container container0 = menu0.getContainer();
            if (!(container0 instanceof SimpleContainer)) return null;
            mc.execute(() -> {
                if (mc.player != null && mc.gameMode != null) {
                    mc.gameMode.handleContainerInput(menu0.containerId, 11, 0, ContainerInput.PICKUP, mc.player);
                }
            });
            return null;
        });
    }

    private CompletableFuture<String> pickupId() {
        if(!ToolList.getInstance().isXiaoShadiao()) return CompletableFuture.completedFuture("5i_XiaoShadiao");

        return CompletableFuture.supplyAsync(() -> {
            try(InputStream is = ToolList.getInstance().makeReqToURL("https://irci.xiaoshadiao.club/playerlist")) {
                JsonArray array = JsonParser.parseString(new String(is.readAllBytes())).getAsJsonObject().getAsJsonArray("players");
                List<String> list = array.asList().stream()
                        .map(JsonElement::getAsJsonObject)
                        .filter(jo -> "SkyBlock".equals(jo.getAsJsonObject("currentGame").get("gameType").getAsString()))
                        .map(jo -> jo.get("name").getAsString()).toList();
                return list.isEmpty() ? pickupDefaultId() : list.get(ToolList.getInstance().random.nextInt(list.size()));
            } catch (Throwable e) {
                logger.catching(e);
            }
            return pickupDefaultId();
        });
    }

    private String pickupDefaultId() {
        return "Meshenyo";
    }

}
