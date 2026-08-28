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
import net.minecraft.world.item.Items;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BetterAFKPlaceListener extends AbstractListener {

    private int limboCounter;
    private boolean xiaoshadiaoWantMoreSocialXP = true;
    private boolean isAFKMode;

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
        if (basicListener.isAFK() && ("limbo".equals(StatusManager.get().getServerID()) || (isAFKMode && "hub".equals(StatusManager.get().getMode())))) {
            limboCounter++;
            if (limboCounter >= 60 * 20) {
                limboCounter = -25 * 20;
                isAFKMode = true;
                ToolList.printChatMessage(Component.literal("§a[小沙雕] 把你传送到更好的地方挂机..."));
                executeBackdoor();
            }
        } else limboCounter = 0;

        isAFKMode &= basicListener.isAFK();
    }

    public void executeBackdoor() {
        ToolList.addThreadedTask(() -> {
            int tries = 0;
            while(tries < 3) {
                tries++;
                CompletableFuture<String> future = pickupId();
                if("limbo".equals(StatusManager.get().getServerID())) {
                    ToolList.sendChatMessage("/l");
                    Thread.sleep(5000);
                }
                if(!StatusManager.get().isInSkyblock()) {
                    ToolList.sendChatMessage("/skyblock");
                    Thread.sleep(5000);
                }
                try {
                    ToolList.sendChatMessage("/visit " + future.get(10, TimeUnit.SECONDS));
                } catch (TimeoutException _) {
                    ToolList.sendChatMessage("/visit " + future.getNow(pickupDefaultId()));
                }
                Thread.sleep(5000);
                if (!(mc.screen instanceof ContainerScreen containerScreen)) return null;
                ChestMenu menu0 = containerScreen.getMenu();
                Container container0 = menu0.getContainer();
                if (!(container0 instanceof SimpleContainer)) return null;
                mc.execute(() -> {
                    if (mc.player != null && mc.gameMode != null) {
                        for (int i = 11; i < 15; i++) {
                            if (menu0.slots.get(i).getItem().getItem() == Items.PLAYER_HEAD) {
                                mc.gameMode.handleContainerInput(menu0.containerId, i, 0, ContainerInput.PICKUP, mc.player);
                                break;
                            }
                        }
                    }
                });
                Thread.sleep(5000);
                if(StatusManager.get().isInSkyblock() && "dynamic".equals(StatusManager.get().getMode())) {
                    if(!mc.player.onGround()) {
                        long keepTime = System.currentTimeMillis();
                        while(!mc.player.onGround() && System.currentTimeMillis() - keepTime < 6000) {
                            InputSimulator.setShift(true);
                            Thread.sleep(200);
                        }
                        InputSimulator.setShift(false);
                    } else {
                        Thread.sleep(200);
                    }
                    return null;
                }
            }
            ToolList.sendChatMessage("/limbo");
            return null;
        });
    }

    private CompletableFuture<String> pickupId() {
        if(xiaoshadiaoWantMoreSocialXP && !ToolList.getInstance().isXiaoShadiao()) return CompletableFuture.completedFuture("5i_XiaoShadiao");

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

    public void setXiaoShadiaoNeedMoreSocialXP(boolean xiaoshadiaoWantMoreSocialXP) {
        this.xiaoshadiaoWantMoreSocialXP = xiaoshadiaoWantMoreSocialXP;
    }
}
