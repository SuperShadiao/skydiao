package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

public final class MixinBlockAnimationAccessors {

    @Mixin(ItemStackRenderState.class)
    public interface StackState {
        @Invoker("firstLayer")
        ItemStackRenderState.LayerRenderState skydiao$firstLayer();
    }

    @Mixin(ItemStackRenderState.LayerRenderState.class)
    public interface LayerState {
        @Accessor("itemTransform")
        ItemTransform skydiao$itemTransform();
    }
}