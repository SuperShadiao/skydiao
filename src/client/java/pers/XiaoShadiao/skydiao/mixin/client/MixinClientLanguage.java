package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static pers.XiaoShadiao.skydiao.utils.i18n.CrowdinI18nManager.translate;

@Mixin(ClientLanguage.class)
public class MixinClientLanguage {

    @Inject(at = @At("HEAD"), method = "getOrDefault", cancellable = true)
    private static void get(String string, String string2, CallbackInfoReturnable<String> cir) {
        String result = translate(string);
        if(result != string) {
            cir.setReturnValue(result);
        }
    }

}
