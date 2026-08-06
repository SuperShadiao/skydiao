package pers.XiaoShadiao.skydiao.mixin.client.debug;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuFence;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pers.XiaoShadiao.skydiao.utils.ToolList;

@Mixin(MappableRingBuffer.class)
public class MixinSyncGPU {

    @WrapOperation(method = "currentBuffer", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/GpuFence;awaitCompletion(J)Z"))
    public boolean awaitCompletion(GpuFence instance, long l, Operation<Boolean> original) {
        if(ToolList.getInstance().isXiaoShadiao()) return original.call(instance, l);
        return instance.awaitCompletion(3000);
    }

}
