package pers.XiaoShadiao.skydiao.eventbuslistener.foraging;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.hud.DynamicSpotHUD;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ForagingSweepToDynamicSpotListener extends AbstractForagingListener {

    // Sweep Details: 519.95 Sweep
    // Fig Tree Toughness: 1.5 20 Logs
    private static final Pattern SWEEP = Pattern.compile("Sweep Details: ([\\d.]+)\uE023 Sweep");
    // Wrong Style: -50% Sweep 7 Logs Cut branches and trunk first!!
    private static final Pattern LOG = Pattern.compile(".*Toughness: [\\d.]+ ([\\d.]+) Logs");
    private static final Pattern PRO_TIP = Pattern.compile(".*Wrong Style: .* ([\\d.]+) Logs (.*)");

    // Axe throw: -50% Sweep 6 Logs
    private static final Pattern THROW_AXE = Pattern.compile(".*Axe throw: .* ([\\d.]+) Logs");

    private String sweep;
    private String logs;
    private String proTip;

    @Override
    public String getListenerName() {
        return "ForagingSweepToDynamicSpotListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.ALLOW_GAME.register(this::onAllowChat);
    }

    private boolean onAllowChat(Component component, boolean b) {
        if(!isInForagingArea()) return true;

        String message = ToolList.getInstance().deleteColorCode(component.getString());

        boolean allow = true;
        Matcher matcher;
        matcher = SWEEP.matcher(message);
        if(matcher.find()) {
            allow = false;
            if(sweep != null) {
                sweep = matcher.group(1);
            } else {
                sweep = matcher.group(1);
                return false;
            }
        }
        matcher = LOG.matcher(message);
        if(matcher.find()) {
            logs = matcher.group(1);
            return false;
        }
        matcher = PRO_TIP.matcher(message);
        if(matcher.find()) {
            allow = false;
            logs = matcher.group(1);
            proTip = matcher.group(2);
        }
        matcher = THROW_AXE.matcher(message);
        if(matcher.find()) {
            allow = false;
            logs = matcher.group(1) + " (Throw Axe)";
        }

        if(sweep != null && logs != null) {
            DynamicSpotHUD.replaceIdlePart(
                    DynamicSpotHUD.USERNAME_PART_ID,
                    DynamicSpotHUD.textPart(null, "§2\uE023" + sweep + " §f-> " + (proTip == null ? "§a" : "§e") + logs + " logs", 0xFFFFFFFF, null),
                    3500L
            );
            DynamicSpotHUD.replaceIdlePart(
                    DynamicSpotHUD.MOD_NAME_PART_ID,
                    (DynamicSpotHUD.IdlePart) null,
                    3500L
            );
            if(proTip == null) {
                DynamicSpotHUD.removeReplaceIdlePart(DynamicSpotHUD.FPS_PART_ID);
                DynamicSpotHUD.removeReplaceIdlePart(DynamicSpotHUD.TPS_PART_ID);
            }
            sweep = logs = null;
        }
        if(proTip != null) {
            DynamicSpotHUD.replaceIdlePart(
                    DynamicSpotHUD.FPS_PART_ID,
                    DynamicSpotHUD.textPart(null, "§c⚠ " + proTip, 0xFFFFFFFF, null),
                    3500L
            );
            DynamicSpotHUD.replaceIdlePart(
                    DynamicSpotHUD.TPS_PART_ID,
                    (DynamicSpotHUD.IdlePart) null,
                    3500L
            );
            proTip = null;
        }
        return allow;
    }

}
