package pers.XiaoShadiao.skydiao.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import pers.XiaoShadiao.skydiao.utils.ClientRenderCrashFixer;

public class HUDCrashFixer extends XSDHUD {

    @Override
    public void runRegister() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("skydiao", "crash_fixer"), this);
    }

    @Override
    public void render(@NotNull GuiGraphicsExtractor context, @NotNull DeltaTracker tickCounter, boolean force) {
        ClientRenderCrashFixer.gg = context;
    }

    @Override
    public void renderEffect(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        ClientRenderCrashFixer.gg = context;
    }

    @Override
    public String getHudName() {
        return null;
    }

}
