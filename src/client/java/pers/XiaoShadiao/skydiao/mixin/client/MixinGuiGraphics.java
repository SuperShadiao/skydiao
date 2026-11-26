package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;

@Mixin(GuiGraphicsExtractor.class)
public class MixinGuiGraphics {

    @WrapOperation(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemCount(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void replaceText(GuiGraphicsExtractor instance, Font font, ItemStack itemStack, int x, int y, String countText, Operation<Void> original) {
        original.call(instance, font, itemStack, x, y, CustomFabricEvents.RENDER_ITEM_COUNT_OVERRIDE.invoker().overrideItemCount(itemStack, countText));
    }

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V", at = @At(value = "HEAD"))
    private void onItemRender(LivingEntity owner, ItemStack itemStack, int x, int y, int seed, CallbackInfo ci) {
        CustomFabricEvents.ON_RENDER_ITEM.invoker().renderSlots((GuiGraphicsExtractor) (Object) this, itemStack, x, y);
    }

}
