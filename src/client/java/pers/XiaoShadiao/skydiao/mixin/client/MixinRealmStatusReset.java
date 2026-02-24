package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.realmsclient.RealmsAvailability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(RealmsAvailability.class)
public interface MixinRealmStatusReset {

    @Accessor("future")
    public static void setFuture(CompletableFuture<RealmsAvailability.Result> future) {};

}
