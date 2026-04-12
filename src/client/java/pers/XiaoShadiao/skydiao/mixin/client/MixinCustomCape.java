package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.eventbuslistener.BasicListener;

import java.util.Objects;

@Mixin(PlayerSkin.class)
public class MixinCustomCape {

    @Inject(at = @At("HEAD"), method = "cape", cancellable = true)
    public void cape(CallbackInfoReturnable<ClientAsset.Texture> cir) {
        if(ConfigManager.skydiaocustomcape.getValue() && Objects.equals(this, BasicListener.basicListener.selfPlayerSkin)) {
            cir.setReturnValue(new ClientAsset.Texture() {
                @Override
                public @NotNull Identifier texturePath() {
                    return BasicListener.customCape;
                }

                @Override
                public @NotNull Identifier id() {
                    return BasicListener.customCape;
                }
            });
        }
    }

}
