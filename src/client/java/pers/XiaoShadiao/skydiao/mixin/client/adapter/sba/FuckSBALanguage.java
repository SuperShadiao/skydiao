package pers.XiaoShadiao.skydiao.mixin.client.adapter.sba;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(targets = "com.fix3dll.skyblockaddons.core.Language")
public class FuckSBALanguage {

    @Mutable
    @Final
    @Shadow
    private Identifier identifier;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(String par3, int par2, String path, CallbackInfo ci) {
        if(path.toLowerCase(Locale.ENGLISH).equals("zh_tw")) {
            this.identifier = Identifier.fromNamespaceAndPath("skyblockaddons", "flags/zh_cn.png");
        }
    }

}
