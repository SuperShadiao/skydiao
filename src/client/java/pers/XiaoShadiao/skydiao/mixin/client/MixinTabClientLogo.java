package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import pers.XiaoShadiao.skydiao.utils.OnlinePlayerList;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(PlayerTabOverlay.class)
public class MixinTabClientLogo {

    @Unique
    private static final Pattern PLAYERNAME_PATTERN = Pattern.compile("\\w+");

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I", ordinal = 0))
    private int widthOffset(Font instance, FormattedText text, Operation<Integer> original) {
        return original.call(instance, text) + (testIsSkydiaoUser(text) ? 10 : 0);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void renderOffset(GuiGraphicsExtractor instance, Font font, Component str, int x, int y, int color, Operation<Void> original) {
        boolean isSkydiaoUser = testIsSkydiaoUser(str);
        if(isSkydiaoUser) {
            instance.blit(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("skydiao", "xsdv2.png"), x, y, 0, 0, 8, 8, 8, 8);
        }
        original.call(instance, font, str, x + (isSkydiaoUser ? 10 : 0), y, color);
    }

    @Unique
    private boolean testIsSkydiaoUser(FormattedText str) {
        boolean isSkydiaoUser = false;

        String line = ToolList.getInstance().deleteColorCode(str.getString());
        Matcher matcher = PLAYERNAME_PATTERN.matcher(line);
        while(matcher.find()) {
            String player = matcher.group();
            if(OnlinePlayerList.isPlayerNameOnline(player)) {
                isSkydiaoUser = true;
                break;
            }
        }
        return isSkydiaoUser;
    }

}
