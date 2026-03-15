package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.realmsclient.client.RealmsClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RealmsClient.class)
public interface MixinRealmsClientReseter {

    @Accessor("realmsClientInstance")
    public static void setRealmsClientInstance(RealmsClient instance) {};

}
