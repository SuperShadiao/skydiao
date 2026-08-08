package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.StatusManager;

public class DungeonReviveItemCD extends XSDHUD {

    @Override
    public void runRegister() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skydiao", "dungeon_revive_item_cd"), this);
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, boolean force) {
        if (!force && (!StatusManager.get().isInDungeon() || !ConfigManager.dungeonReviveItemCDRender.getValue())) return;
        context.text(mc.font, "§eBonzo§f: " + getCDMsg(AbstractListener.resurrectionItemListener.getBonzoCD()), context.guiWidth() / 2 + 25, context.guiHeight() / 2, 0xFFFFFFFF, true);
        context.text(mc.font, "§bSpirit§f: " + getCDMsg(AbstractListener.resurrectionItemListener.getSpiritCD()), context.guiWidth() / 2 + 25, context.guiHeight() / 2 + mc.font.lineHeight, 0xFFFFFFFF, true);
        context.text(mc.font, "§6Phoenix§f: " + getCDMsg(AbstractListener.resurrectionItemListener.getPhoenixCD()), context.guiWidth() / 2 + 25, context.guiHeight() / 2 + mc.font.lineHeight * 2, 0xFFFFFFFF, true);
    }

    @Override
    public void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter) {

    }

    @Override
    public @Nullable String getHudName() {
        return "DungeonReviveItemCD";
    }

    private String getCDMsg(int cd) {
        if(cd <= 0) {
            return "§a✔";
        }
        if(cd > 100) {
            return "§c" + cd / 20 + "s";
        }
        return "§c" + String.format("%.2f", cd / 20f) + "s";
    }

}
