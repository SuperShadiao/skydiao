package pers.XiaoShadiao.skydiao.mixin.client.debug;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;

// issue from 19minutes

@Mixin(NativeImage.class)
public class MixinTest3 {

//    @Unique
//    private boolean closed;
//
//    @Unique
//    private Throwable throwable;
//
//    @Inject(method = "<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZJ)V", at = @At("HEAD"))
//    private static void check(NativeImage.Format format, int i, int j, boolean bl, long l, CallbackInfo ci) {
//        if(l == 0) {
//            IllegalArgumentException e = new IllegalArgumentException("Pixel is 0");
//            ToolList.mc.delayCrash(new CrashReport("Critical error in loading NativeImage", e));
//            throw e;
//        }
//    }
//
//    @Inject(method = "<init>*", at = @At("TAIL"))
//    private void flag(CallbackInfo ci) {
//        throwable = new Throwable();
//    }
//
//    @Inject(method = "close", at = @At("HEAD"))
//    private void close(CallbackInfo ci) {
//        Thread.dumpStack();
//    }
//
//    @Inject(method = "getPointer", at = @At("HEAD"))
//    private void checkClose(CallbackInfoReturnable<Long> cir) {
//        if(closed) {
//            throwable.printStackTrace();
//            throw new IllegalStateException("NativeImage already closed");
//        }
//    }

}
