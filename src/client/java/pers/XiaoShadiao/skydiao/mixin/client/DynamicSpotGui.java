package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.hud.DynamicSpotHUD;

/** Continues extracting the player list until its shell has folded back into the HUD. */
@Mixin(Gui.class)
public abstract class DynamicSpotGui {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private PlayerTabOverlay tabList;

    @Inject(method = "extractTabList", at = @At("HEAD"), cancellable = true)
    private void skydiao$extractClosingTab(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                                          CallbackInfo callbackInfo) {
        if (minecraft.player == null || minecraft.level == null
                || !DynamicSpotHUD.shouldKeepTabOpen()) return;

        // Gui otherwise skips extraction entirely as soon as Tab is released;
        // keeping PlayerTabOverlay.visible true alone cannot draw a closing frame.
        Scoreboard scoreboard = minecraft.level.getScoreboard();
        graphics.nextStratum();
        tabList.extractRenderState(graphics, graphics.guiWidth(), scoreboard,
                scoreboard.getDisplayObjective(DisplaySlot.LIST));
        callbackInfo.cancel();
    }
}
