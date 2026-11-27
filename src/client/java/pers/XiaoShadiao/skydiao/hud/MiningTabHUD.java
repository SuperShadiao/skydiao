package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.tab.MiningTabData;
import pers.XiaoShadiao.skydiao.utils.tab.TabReader;

import java.util.Locale;

public class MiningTabHUD extends XSDHUD {

    private static final int BAR_WIDTH = 142;
    private MiningTabData.MineshaftProgress progress;

    @Override
    public void runRegister() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("skydiao", "mining_tab_hud"), this);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> progress = null);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isEnabled()) {
                progress = null;
            } else if (client.player.tickCount % 5 == 0) {
                progress = MiningTabData.read(TabReader.getOrderedNoColorLines()).mineshaft();
            }
        });
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force) {
        if (!force && !isEnabled()) return;

        MiningTabData.MineshaftProgress display = force ? MiningTabData.PREVIEW.mineshaft() : progress;
        if (display == null) return;

        int x = 8;
        int y = context.guiHeight() / 2 - 20;
        context.text(mc.font, "Mineshaft Pity", x, y, 0xFF55FFFF, true);
        String count = String.format(Locale.ROOT, "%,d/%,d", display.current(), display.maximum());
        context.text(mc.font, count, x + BAR_WIDTH - mc.font.width(count), y, 0xFFFFFFFF, true);

        int filled = Math.round(display.fraction() * BAR_WIDTH);
        context.fill(x, y + 11, x + BAR_WIDTH, y + 16, 0xAA222222);
        if (filled > 0) context.fill(x, y + 11, x + filled, y + 16, 0xFF55FFFF);
        String remaining = String.format(Locale.ROOT, "%,d to pity", display.remaining());
        context.text(mc.font, remaining, x, y + 19, 0xFFAAAAAA, true);
    }

    private boolean isEnabled() {
        return ConfigManager.miningTabHUD.getValue() && mc.player != null && StatusManager.get().isInSkyblock();
    }

    @Override
    public void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force, HudOffsetAndScale settings) {
    }

    @Override
    public String getHudName() {
        return "MiningTabHUD";
    }
}
