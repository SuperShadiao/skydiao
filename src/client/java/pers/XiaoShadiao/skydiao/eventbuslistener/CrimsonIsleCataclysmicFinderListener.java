package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.tab.TabReader;

import java.util.Objects;
import java.util.Optional;

public class CrimsonIsleCataclysmicFinderListener extends AbstractListener {

    private static final Object2LongMap<String> visitedServer = new Object2LongOpenHashMap<>();
    private boolean inIsle;
    private String currentVolcanoStatus;
    private String visitedServerMessage;

    private boolean cataOnlyMode;

    private boolean isAutoFindWorking;

    private BooleanConsumer onFindVolcanoOrStop;

    @Override
    public String getListenerName() {
        return "CrimsonIsleCataclysmicFinderListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft mc) {
        boolean temp = isInIsle();
        if(!temp) {
            currentVolcanoStatus = null;
        }
        if(temp != inIsle) {
            long time = visitedServer.getOrDefault(StatusManager.get().getServerID(), -1);
            if(time != -1 && ConfigManager.isleDupServerTip.getValue()) {
                visitedServerMessage = "§a你在§e" + ToolList.getInstance().timeToString(System.currentTimeMillis() - time) + "§a之前拜访过这个服务器!";
            }
        }
        if(temp) {
            visitedServer.put(StatusManager.get().getServerID(), System.currentTimeMillis());
        }
        inIsle = temp;
        Optional<String> volcano = TabReader.findLineStartsWith("Volcano");
        MutableObject<String> mute = new MutableObject<>();
        volcano.ifPresentOrElse(mute::setValue, () -> mute.setValue(null));
        if(!Objects.equals(currentVolcanoStatus, mute.get())) {
            currentVolcanoStatus = mute.get();
            String finalTipMessage = visitedServerMessage == null ? "" : visitedServerMessage;
            visitedServerMessage = null;
            if(isAutoFindWorking || ConfigManager.isleVolcanoFinder.getValue()) {
                boolean foundVolcano = false;
                if (currentVolcanoStatus != null) {
                    if (isAutoFindWorking ? cataOnlyMode : ConfigManager.isleVolcanoFinderCataOnlyMode.getValue()) {
                        if (currentVolcanoStatus.contains("CATACLYSMIC")) {
                            foundVolcano = true;
                        }
                    } else {
                        if (!currentVolcanoStatus.contains("INACTIVE")) {
                            foundVolcano = true;
                        }
                    }
                }
                if (foundVolcano) {
                    if (finalTipMessage.isBlank()) {
                        finalTipMessage = "§e" + currentVolcanoStatus;
                    } else {
                        finalTipMessage = "§f | §e" + currentVolcanoStatus;
                    }

                    if (onFindVolcanoOrStop != null) {
                        onFindVolcanoOrStop.accept(true);
                    }
                }
            }

            if(!finalTipMessage.isBlank()) {
                XSDHUD.bigTitle.updateTitleMsg(finalTipMessage, 3000);
            }
        }
    }

    private boolean isInIsle() {
        return "crimson_isle".equals(StatusManager.get().getMode());
    }

    // =========================== Auto Find Volcano ============================

    @Override
    public void run() {
        while (true) {
            try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException _) {}

            try {
                executeThread();
            } catch (Exception e) {
                logger.catching(e);
            }
        }
    }

    private void executeThread() {
        MutableBoolean finished = new MutableBoolean(false);
        MutableBoolean found = new MutableBoolean(false);

        isAutoFindWorking = true;
        while(!finished.booleanValue()) {
            if("limbo".equals(StatusManager.get().getServerID())) {
                ToolList.sendChatMessage("/l");
                try { Thread.sleep(5000); } catch (InterruptedException _) {}
            }
            if(!StatusManager.get().isInSkyblock()) {
                ToolList.sendChatMessage("/skyblock");
                try { Thread.sleep(5000); } catch (InterruptedException _) {}
            }
            ToolList.sendChatMessage("/warp " + (isInIsle() ? "spider" : "isle"));
            onFindVolcanoOrStop = (f) -> {
                finished.setTrue();
                found.setValue(f);
            };

            try { Thread.sleep(6000); } catch (InterruptedException _) {}
        }
        isAutoFindWorking = false;

        if(found.booleanValue()) {
            if(basicListener.isAFK()) {
                while(basicListener.isAFK()) {
                    XSDHUD.bigTitle.updateTitleMsg("§c找到火山咯!", 10000, SoundEvents.WITHER_SPAWN);
                    try { Thread.sleep(4000); } catch (InterruptedException _) {}
                }
            }
        }
    }

    public void startAutoFindVolcano(boolean cataOnlyMode) {
        this.cataOnlyMode = cataOnlyMode;
        interrupt();
    }

    public void stopAutoFindVolcano() {
        if(onFindVolcanoOrStop != null) {
            onFindVolcanoOrStop.accept(false);
        }
    }

}
