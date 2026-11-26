package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.tab.MiningTabData;
import pers.XiaoShadiao.skydiao.utils.tab.PickaxeAbilityCountdown;
import pers.XiaoShadiao.skydiao.utils.tab.TabReader;

import java.util.List;

public class PickaxeAbilityHUD extends XSDHUD {

    private static final long TAB_SYNC_INTERVAL_MILLIS = 5_000;

    private final PickaxeAbilityCountdown countdown = new PickaxeAbilityCountdown();
    private long nextTabSyncMillis;

    @Override
    public void runRegister() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("skydiao", "pickaxe_ability_hud"), this);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> resetCountdown());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isEnabled()) {
                resetCountdown();
                return;
            }

            long nowMillis = Util.getMillis();
            if (nowMillis >= nextTabSyncMillis) {
                countdown.sync(MiningTabData.read(TabReader.getOrderedNoColorLines()).pickaxeAbility(), nowMillis);
                nextTabSyncMillis = nowMillis + TAB_SYNC_INTERVAL_MILLIS;
            }
        });
    }

    private void resetCountdown() {
        countdown.sync(List.of(), 0);
        nextTabSyncMillis = 0;
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force) {
        if (!force && !isEnabled()) return;

        List<String> lines = force ? MiningTabData.PREVIEW.pickaxeAbility() : countdown.lines(Util.getMillis());
        if (lines.isEmpty()) return;

        int x = 8;
        int y = context.guiHeight() / 2 + 13;
        context.text(mc.font, "Pickaxe Ability", x, y, 0xFFFFCC55, true);
        for (String line : lines) {
            y += 10;
            context.text(mc.font, line, x, y, 0xFFFFFFFF, true);
        }
    }

    private boolean isEnabled() {
        return ConfigManager.pickaxeAbilityHUD.getValue() && mc.player != null && StatusManager.get().isInSkyblock();
    }

    @Override
    public void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force, HudOffsetAndScale settings) {
    }

    @Override
    public String getHudName() {
        return "PickaxeAbilityHUD";
    }
}
