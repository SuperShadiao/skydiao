package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.OptionalInt;

public class ItemStarCount extends AbstractListener {

    @Override
    public String getListenerName() {
        return "ItemStarCount";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.RENDER_ITEM_COUNT_OVERRIDE.register(this::replaceAsStarCount);
    }

    private String replaceAsStarCount(ItemStack itemStack, @Nullable String original) {
        if(ConfigManager.itemStarCountRender.getValue() && ToolList.getInstance().tryGetSkyblockItemId(itemStack) != null) {
            OptionalInt stars = ToolList.getInstance().tryGetSkyblockItemAttributeAsNumber(itemStack, "upgrade_level");

            if(stars.isEmpty()) {
                stars = ToolList.getInstance().tryGetSkyblockItemAttributeAsNumber(itemStack, "dungeon_item_level");
            }

            if(stars.isPresent()) {
                return ((stars.getAsInt() > 5 ? ConfigManager.itemStarCountRenderColor2 : ConfigManager.itemStarCountRenderColor1).getCurrentDisplayString().replace("&", "§")) + stars.getAsInt();
            }
        }
        return original;
    }

}
