package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanTransientMemory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * VulkanCommandEncoder.submit() 的执行顺序是：
 * endCommandBuffer -> transientMemory.endSubmit()（内部把 commandBuffer 置空）
 * -> 提交队列 -> destroyQueue.rotate() -> transientMemory.beginSubmit()（重新分配 commandBuffer）
 * <p>
 * 只要在 endSubmit() 之后、beginSubmit() 之前抛出异常（例如销毁队列里某个回调抛错），
 * transientMemory 的 commandBuffer 就会永久为 null，之后每次 submit() 都会在
 * vkEndCommandBuffer(null) 上抛 NullPointerException，并且 currentSubmitIndex 不再递增，
 * 下一帧的 MappableRingBuffer 又会抛 "Cannot wait on a fence for the current submit"，
 * 形成每帧必崩的死循环。
 * <p>
 * 这里在 submit() 入口处补一次 beginSubmit()，把被打断的状态修复回正常，避免永久失灵。
 */
@Mixin(VulkanCommandEncoder.class)
public class MixinVulkanCommandEncoder {

    @Inject(method = "submit", at = @At("HEAD"))
    private void skydiao$repairTransientMemory(CallbackInfo ci) {
        VulkanTransientMemory transientMemory = (VulkanTransientMemory) ((VulkanCommandEncoder) (Object) this).transientMemory();
        if (((MixinVulkanTransientMemoryAccessor) (Object) transientMemory).skydiao$getCommandBuffer() == null) {
            transientMemory.beginSubmit();
        }
    }
}
