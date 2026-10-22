package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;

@Mixin(BowItem.class)
public class MixinBow {

    @WrapOperation(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getProjectile(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    public ItemStack getProjectile(Player instance, ItemStack heldWeapon, Operation<ItemStack> original) {
        if(ConfigManager.preventShortbowDrawing.getValue()) {
            List<Component> list = heldWeapon.getTooltipLines(Item.TooltipContext.of(ToolList.mc.level), ToolList.mc.player, TooltipFlag.NORMAL);
            for (Component component : list) {
                if (ToolList.getInstance().deleteColorCode(component.getString()).equals("Shortbow: Instantly shoots!")) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return original.call(instance, heldWeapon);
    }

}
