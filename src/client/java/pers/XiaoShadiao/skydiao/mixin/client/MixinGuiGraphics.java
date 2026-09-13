package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.OptionalInt;

@Mixin(GuiGraphicsExtractor.class)
public class MixinGuiGraphics {

    @WrapOperation(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemCount(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void starCount(GuiGraphicsExtractor instance, Font font, ItemStack itemStack, int x, int y, String countText, Operation<Void> original) {
        if(ConfigManager.itemStarCountRender.getValue() && ToolList.getInstance().tryGetSkyblockItemId(itemStack) != null) {
            OptionalInt stars = ToolList.getInstance().tryGetSkyblockItemAttributeAsNumber(itemStack, "upgrade_level");

            if(stars.isEmpty()) {
                stars = ToolList.getInstance().tryGetSkyblockItemAttributeAsNumber(itemStack, "dungeon_item_level");
            }

            if(stars.isPresent()) {
                original.call(instance, font, itemStack, x, y, ((stars.getAsInt() > 5 ? ConfigManager.itemStarCountRenderColor2 : ConfigManager.itemStarCountRenderColor1).getCurrentDisplayString().replace("&", "§")) + stars.getAsInt());
            } else {
                original.call(instance, font, itemStack, x, y, countText);
            }
        } else {
            original.call(instance, font, itemStack, x, y, countText);
        }
    }

}
