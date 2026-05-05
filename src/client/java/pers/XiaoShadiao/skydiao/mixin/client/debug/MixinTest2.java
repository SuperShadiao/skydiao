package pers.XiaoShadiao.skydiao.mixin.client.debug;

import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.ToolList;

// issue from 19minutes

@Mixin(GlCommandEncoder.class)
public class MixinTest2 {

    @Inject(method = "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;IIIIIIII)V", at = @At("HEAD"))
    public void check(GpuTexture gpuTexture, NativeImage nativeImage, int i, int j, int k, int l, int m, int n, int o, int p, CallbackInfo ci) {
        if (nativeImage.getPointer() == 0) {
            IllegalArgumentException e = new IllegalArgumentException("Texture " + gpuTexture.getLabel() + " -> NativeImage pointer is 0");
            ToolList.mc.delayCrash(new CrashReport("Critical error in loading texture", e));
            throw e;
        }
    }

}
