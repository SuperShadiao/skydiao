package pers.XiaoShadiao.skydiao.mixin.client.debug;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.utils.ToolList;

// issue from 19minutes

@Mixin(GlStateManager.class)
public class MixinTest {

    @Inject(at = @At("HEAD"), method = "_texSubImage2D(IIIIIIIIJ)V")
    private static void print(int i, int j, int k, int l, int m, int n, int o, int p, long q, CallbackInfo ci) {
        // System.out.println(String.format("_texSubImage2D(%d, %d, %d, %d, %d, %d, %d, %d, %d)", i, j, k, l, m, n, o, p, q));
        if(q == 0) {
            RuntimeException e = new IllegalArgumentException("pixel pointer is 0");
            ToolList.mc.delayCrash(new CrashReport("Critical error in loading texture", e));
            throw e;
        }
    }

}
