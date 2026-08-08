package pers.XiaoShadiao.skydiao.appendage.eventbuslistener.macro.mining;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import pers.XiaoShadiao.skydiao.appendage.utils.posrecord.PositionList;
import pers.XiaoShadiao.skydiao.appendage.utils.posrecord.RecordPos;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.customsounds.CustomSounds;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.IMacro;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.ArrayList;
import java.util.List;

import static pers.XiaoShadiao.skydiao.appendage.eventbuslistener.macro.mining.ObsidianListener.giwk;
import static pers.XiaoShadiao.skydiao.appendage.eventbuslistener.macro.mining.ObsidianListener.plist;

public class ObsidianWRListener extends AbstractListener implements IMacro {

    private static int timer = -1;
    private static boolean isstartyet = false;
    private static boolean isready = false;
    private static boolean isInCheck = false;
    private static int timer2 = 0;

    private static int checkPlayerCooldown = 0;
    private static int PlaySoundsAndStopTimer = -1;
    private static int StopTimer = -1;
    private static boolean hasPlayerClosed = false;
    private static boolean hasPlayerClosedYet = false;

    @Override
    public String getListenerName() {
        return "ObsidianWithRetryListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
    }

    @Override
    public boolean isMacroActive() {
        return ConfigManager.iAutoObsidianWR.getValue();
    }

    @Override
    public boolean onMacroCheck(PositionInfo beforeTP, PositionInfo afterTP) {
        isInCheck = true;
        return false;
    }

    @Override
    public boolean onMacroCheck(int beforeSlot, int afterSlot) {
        return true;
    }

    @Override
    public String getMacroName() {
        return "ObsidianWithRetry";
    }

    private void onStartClientTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (ConfigManager.iAutoObsidianWR.getValue()) {
            if(!isstartyet) {
                PositionList pl = RecordPos.getLocal(ConfigManager.autoObsidianPositionsFile.getValue());
                if(pl==null) {
                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("[AutoObsidian] "+ giwk("positionsfilenotfind")).withColor(0xf22b30));
                    ConfigManager.iAutoObsidianWR.setValue(false);
                    return;
                }
                else plist = pl;
            }
            if(plist.positions().size()<=1){
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("[AutoObsidian] "+ giwk("positioncounterror")).withColor(0xf22b30));
                ConfigManager.iAutoObsidianWR.setValue(false);
                return;
            }
            if(!"combat_3".equals(StatusManager.get().getMode()) && ConfigManager.obsidianTheEndCheck.getValue()) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("[ObsidianWithRetry] "+giwk("notintheend")).withColor(0xf22b30));
                ConfigManager.iAutoObsidianWR.setValue(false);
                return;
            }

            if(isInCheck){
                timer2++;
                if(timer2>3){
                    ConfigManager.iAutoObsidianWR.setValue(false);
                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("[ObsidianWithRetry] Marco Check!").withColor(0xf22b30));
                }
            }

            if(ConfigManager.obsidianPlayerCheck.getValue()) checkNearbyPlayers();

            if(!isready) {
                isInCheck = false;
                isready = true;
                activeThisMacro();
                ConfigManager.iAutoObsidian.setValue(true);
                timer = -1;
            }
            //防止手动开关AutoObsidian带来的影响
            if(ObsidianListener.status == ObsidianListener.Status.ACTIVE){
                timer = -1;
            }
            if (timer == -1) {
                if (ObsidianListener.status == ObsidianListener.Status.SLOT_SWITCHED) {
                    timer = 15 * 20;
                    mc.player.sendSystemMessage(Component.literal("[ObsidianWithRetry] "+String.format(giwk("retry"),String.valueOf(15))).withColor(0x39e8df));
                }
                if (ObsidianListener.status == ObsidianListener.Status.SUITABLE_BLOCK_NOT_FOUND) {
                    timer = 2 * 20;
                    mc.player.sendSystemMessage(Component.literal("[ObsidianWithRetry] "+String.format(giwk("retry"),String.valueOf(2))).withColor(0x39e8df));
                }
                if(ObsidianListener.status == ObsidianListener.Status.SO_FAR_AWAY) {
                    ConfigManager.iAutoObsidianWR.setValue(false);
                    mc.player.sendSystemMessage(Component.literal("[ObsidianWithRetry] "+giwk("sofaraway2")).withColor(0xf22b30));
                }
            }else if (timer == 0) {
                ConfigManager.iAutoObsidian.setValue(true);
                mc.player.sendSystemMessage(Component.literal("[ObsidianWithRetry] "+giwk("retried")).withColor(0x39e8df));
                timer = -1;
            }
            else if (timer >= 1) {
                timer--;
            }
            isstartyet = true;
        }else if (isstartyet){
            ConfigManager.iAutoObsidian.setValue(false);
            isstartyet = false;
            isready = false;
            isInCheck = false;
            timer = -1;
            timer2 = 0;
            checkPlayerCooldown = 0;
            PlaySoundsAndStopTimer = -1;
            StopTimer = -1;
        }
    }

    private void checkNearbyPlayers(){
        LocalPlayer player = mc.player;

        int r = ConfigManager.obsidianPlayerCheckRange.getValue();
        int r2 = 7;

        List<RemotePlayer> players = new ArrayList<>();
        List<RemotePlayer> players2 = new ArrayList<>();
        player.level().players().forEach((p ->
            {
                if(p instanceof RemotePlayer){
                    AABB aabb = new AABB(
                            player.getX()-r, player.getY()-5, player.getZ()-r,
                            player.getX()+r, player.getY()+40, player.getZ()+r
                    );
                    AABB aabb2 = new AABB(
                            player.getX()-r2, player.getY()-5, player.getZ()-r2,
                            player.getX()+r2, player.getY()+40, player.getZ()+r2
                    );
                    if(aabb.contains(p.getPosition(1.0f))){
                        players.add((RemotePlayer) p);
                    }
                    if(aabb2.contains(p.getPosition(1.0f))){
                        players2.add((RemotePlayer) p);
                    }
                }
            }
        ));

        if(checkPlayerCooldown >0) checkPlayerCooldown--;
        if(PlaySoundsAndStopTimer >=0) {
            if(players2.isEmpty()) PlaySoundsAndStopTimer++;
            else PlaySoundsAndStopTimer += 4;
        }
        if(StopTimer>=0) StopTimer++;

        if(!players.isEmpty()) {
            hasPlayerClosed = true;
        }else {
            hasPlayerClosed = false;
        }


        if(hasPlayerClosed&&!hasPlayerClosedYet){
            hasPlayerClosedYet = true;
            PlaySoundsAndStopTimer = 0;
        }
        if(hasPlayerClosedYet&&!hasPlayerClosed){
            hasPlayerClosedYet = false;
            PlaySoundsAndStopTimer = -1;
        }

        if(hasPlayerClosed){
            if(checkPlayerCooldown ==0) {
                player.sendSystemMessage(Component.literal("[ObsidianWithRetry] "+giwk("playertooclosed")).withColor(0xf7a740));
                StringBuilder stringBuilder = new StringBuilder();
                for (RemotePlayer p : players) {
                    stringBuilder.append(p.getName().getString()).append(" ");
                }
                player.sendSystemMessage(Component.literal(stringBuilder.toString()).withColor(0xf7a740));
                checkPlayerCooldown = 60;
                ToolList.getInstance().playSound(CustomSounds.NPC);
            }
            if(PlaySoundsAndStopTimer >=10*20&&StopTimer==-1) {
                PlaySoundsAndStopTimer = 0;
                StopTimer = 0;
                ConfigManager.iAutoObsidian.setValue(false);
                mc.player.sendSystemMessage(Component.literal("[ObsidianWithRetry] "+giwk("playertooclosed2")).withColor(0x39e8df));

            }
        }

        if(StopTimer>=20*20) {
            StopTimer = -1;
            ConfigManager.iAutoObsidian.setValue(true);
        }


    }


}
