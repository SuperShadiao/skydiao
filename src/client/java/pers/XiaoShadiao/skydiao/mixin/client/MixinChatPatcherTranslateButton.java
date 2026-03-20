package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.TextTranslator;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.function.Consumer;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

@Mixin(targets = "obro1961.chatpatches.gui.ContextMenu")
public class MixinChatPatcherTranslateButton {

    @Final
    @Shadow
    private GuiMessage selectedLine;

    @Inject(at = @At(value = "INVOKE", target = "updateButtonMetadata"), method = "init", locals = LocalCapture.CAPTURE_FAILHARD)
    public void init(Consumer<AbstractButton> addSelectableChild, CallbackInfo ci, Component text, Component timestamp, boolean timestamped, Component counter, boolean duped, int strRow) {
        if(ConfigManager.chatbutton.getValue()) {
            registerActionButton(Component.literal(/*"翻译消息"*/translate("skydiao.feature.chatbutton.translate")), 0, Items.OAK_SIGN, (button) -> {
                String str = ToolList.getInstance().deleteColorCode(selectedLine.content().getString());
                new TextTranslator(str).execute();
            });
        }
    }

    @Shadow
    private void registerActionButton(Component id, int localRow, Object icon, Button.OnPress pressAction) {}

}
