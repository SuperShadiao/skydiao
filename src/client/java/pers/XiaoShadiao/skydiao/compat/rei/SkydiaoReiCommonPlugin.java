package pers.XiaoShadiao.skydiao.compat.rei;

import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;

/**
 * REI 公共阶段插件：注册按 NEU id 比较物品的比较器。
 */
public class SkydiaoReiCommonPlugin implements REICommonPlugin {

    @Override
    public void registerItemComparators(ItemComparatorRegistry registry) {
        registry.registerGlobal(new SkyblockItemComparator());
    }
}
