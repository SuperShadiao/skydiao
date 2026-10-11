package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.hud.DynamicSpotHUD;

import java.util.List;

/** Keeps vanilla TabList content and morphs it inside the DynamicSpot shell. */
@Mixin(PlayerTabOverlay.class)
public abstract class DynamicSpotPlayerTabOverlay {
    private boolean skydiao$tabTransformActive;

    @Shadow @Final private Minecraft minecraft;
    @Shadow private Component header;
    @Shadow private Component footer;
    @Shadow public abstract Component getNameForDisplay(PlayerInfo playerInfo);

    @Shadow
    private List<PlayerInfo> getPlayerInfos() {
        throw new AssertionError();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void skydiao$beginTabMorph(GuiGraphicsExtractor graphics, int width, Scoreboard scoreboard,
                                       Objective objective, CallbackInfo callbackInfo) {
        skydiao$tabTransformActive = DynamicSpotHUD.shouldReplaceVanillaTab();
        if (!skydiao$tabTransformActive) return;
        TabBounds bounds = measureTab(width, scoreboard, objective);
        DynamicSpotHUD.drawTabShell(graphics, width, bounds.top(), bounds.width(), bounds.height());
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void skydiao$finishTabMorph(GuiGraphicsExtractor graphics, int width, Scoreboard scoreboard,
                                        Objective objective, CallbackInfo callbackInfo) {
        if (skydiao$tabTransformActive) {
            skydiao$tabTransformActive = false;
            DynamicSpotHUD.finishTabContent(graphics);
        }
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0))
    private void skydiao$hideBackground0(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        if (!skydiao$tabTransformActive) graphics.fill(x1, y1, x2, y2, color);
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1))
    private void skydiao$hideBackground1(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        if (!skydiao$tabTransformActive) graphics.fill(x1, y1, x2, y2, color);
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 2))
    private void skydiao$hideBackground2(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        if (!skydiao$tabTransformActive) graphics.fill(x1, y1, x2, y2, color);
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 3))
    private void skydiao$hideBackground3(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        if (!skydiao$tabTransformActive) graphics.fill(x1, y1, x2, y2, color);
    }

    private TabBounds measureTab(int screenWidth, Scoreboard scoreboard, Objective objective) {
        // Use the same sorted, capped player list as vanilla when measuring the shell.
        List<PlayerInfo> players = getPlayerInfos();
        int columns = Math.max(1, (players.size() + 19) / 20);
        int rows = players.isEmpty() ? 1 : (players.size() + columns - 1) / columns;
        int nameWidth = 0;
        int scoreWidth = 0;
        if (objective != null && objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS) {
            scoreWidth = 90;
        } else if (objective != null) {
            var numberFormat = objective.numberFormatOrDefault(StyledFormat.PLAYER_LIST_DEFAULT);
            for (PlayerInfo player : players) {
                nameWidth = Math.max(nameWidth, minecraft.font.width(getNameForDisplay(player)));
                var score = scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(player.getProfile()), objective);
                Component formatted = ReadOnlyScoreInfo.safeFormatValue(score, numberFormat);
                if (formatted != null) {
                    int formattedWidth = minecraft.font.width(formatted);
                    if (formattedWidth > 0) {
                        scoreWidth = Math.max(scoreWidth, minecraft.font.width(" ") + formattedWidth);
                    }
                }
            }
        }
        for (PlayerInfo player : players) {
            nameWidth = Math.max(nameWidth, minecraft.font.width(getNameForDisplay(player)));
        }

        boolean showFaces = minecraft.isLocalServer() || minecraft.getConnection() != null
                && minecraft.getConnection().getConnection().isEncrypted();
        int columnWidth = Math.min(columns * ((showFaces ? 9 : 0) + nameWidth + scoreWidth + 13),
                Math.max(1, screenWidth - 50)) / columns;
        int tableWidth = columnWidth * columns + 5 * (columns - 1);
        int wrapWidth = Math.max(1, screenWidth - 50);
        int textWidth = maxLineWidth(header, wrapWidth);
        textWidth = Math.max(textWidth, maxLineWidth(footer, wrapWidth));

        int headerRows = header == null ? 0 : Math.max(1, minecraft.font.split(header, wrapWidth).size());
        int footerRows = footer == null ? 0 : Math.max(1, minecraft.font.split(footer, wrapWidth).size());
        int naturalWidth = Math.max(tableWidth, textWidth) + 2;
        int naturalHeight = 1 + 9 * (headerRows + rows + footerRows)
                + (header == null ? 0 : 1) + (footer == null ? 0 : 1);
        return new TabBounds(9, naturalWidth, naturalHeight);
    }

    private int maxLineWidth(Component text, int wrapWidth) {
        if (text == null) return 0;
        int max = 0;
        for (var line : minecraft.font.split(text, wrapWidth)) {
            max = Math.max(max, minecraft.font.width(line));
        }
        return max;
    }

    private record TabBounds(int top, int width, int height) {
    }
}
