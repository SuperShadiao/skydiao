package pers.XiaoShadiao.skydiao.compat.rei;

import me.shedaniel.rei.api.common.entry.comparison.ComparisonContext;
import me.shedaniel.rei.api.common.entry.comparison.EntryComparator;
import net.minecraft.world.item.ItemStack;
import pers.XiaoShadiao.skydiao.utils.neu.NeuIds;

/**
 * 按 NEU internalname 比较物品，避免 REI 把共享原版底材的物品合并。
 */
public class SkyblockItemComparator implements EntryComparator<ItemStack> {

    @Override
    public long hash(ComparisonContext context, ItemStack itemStack) {
        String neuId = NeuIds.of(itemStack);
        if (neuId.isEmpty()) {
            return EntryComparator.noop().hash(context, itemStack);
        }
        return neuId.hashCode();
    }
}
