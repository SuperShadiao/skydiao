package pers.XiaoShadiao.skydiao.compat.rei;

import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.world.item.ItemStack;
import pers.XiaoShadiao.skydiao.utils.neu.NeuIds;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;

import java.util.List;
import java.util.Optional;

/**
 * 为带 Wiki 链接的 Skyblock 物品提供 Wiki 分类条目。
 */
public class WikiDisplayGenerator implements DynamicDisplayGenerator<WikiDisplay> {

    @Override
    public Optional<List<WikiDisplay>> getRecipeFor(EntryStack<?> entry) {
        if (!(entry.getValue() instanceof ItemStack stack)) {
            return Optional.empty();
        }
        String neuId = NeuIds.of(stack);
        if (neuId.isEmpty()) {
            return Optional.empty();
        }
        return NeuItemRepository.INSTANCE.find(neuId)
                .filter(item -> !item.info().isEmpty())
                .map(item -> Optional.of(List.of(new WikiDisplay(item))))
                .orElse(Optional.empty());
    }

    @Override
    public Optional<List<WikiDisplay>> getUsageFor(EntryStack<?> entry) {
        return Optional.empty();
    }
}
