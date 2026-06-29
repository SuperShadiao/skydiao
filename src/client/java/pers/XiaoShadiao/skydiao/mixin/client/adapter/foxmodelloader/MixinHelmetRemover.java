package pers.XiaoShadiao.skydiao.mixin.client.adapter.foxmodelloader;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import pers.XiaoShadiao.skydiao.utils.ToolList;

@Mixin(targets = "com.elfmcys.yesstevemodel.client.renderer.layer.CustomPlayerArmorLayer")
public class MixinHelmetRemover {

    @WrapMethod(method = "isArmorItem")
    private boolean isArmorItem(ItemStack stack, Operation<Boolean> original) {
        boolean result = original.call(stack);
        if(stack.getItem() instanceof BlockItem && ToolList.getInstance().stringHasContext(ToolList.getInstance().tryGetSkyblockItemId(stack))) return true;
        return result;
    }

}
