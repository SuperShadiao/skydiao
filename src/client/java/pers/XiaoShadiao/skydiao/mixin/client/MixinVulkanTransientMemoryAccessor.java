package pers.XiaoShadiao.skydiao.mixin.client;

import com.mojang.renderpearl.backend.vulkan.VulkanTransientMemory;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VulkanTransientMemory.class)
public interface MixinVulkanTransientMemoryAccessor {

    @Accessor("commandBuffer")
    VkCommandBuffer skydiao$getCommandBuffer();
}
