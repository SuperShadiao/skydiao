package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(KeyMapping.class)
public class MixinKeyMappingFix {

    // https://github.com/SuperShadiao/skydiao/issues/25
    @Inject(method = "forAllKeyMappings", at = @At("HEAD"), cancellable = true)
    private static void blockUnknownKeyTrigger(InputConstants.Key key, Consumer<KeyMapping> operation, CallbackInfo ci) {
        if (key.getValue() == 0) ci.cancel();
    }

}
