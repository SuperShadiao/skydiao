package pers.XiaoShadiao.skydiao.compat.rei;

import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.world.item.ItemStack;
import pers.XiaoShadiao.skydiao.utils.neu.NeuIds;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;

import java.util.List;
import java.util.Optional;

/**
 * 根据物品的 NEU id 动态提供配方和用途。
 */
public class SkyblockRecipeDisplayGenerator implements DynamicDisplayGenerator<SkyblockRecipeDisplay> {

    @Override
    public Optional<List<SkyblockRecipeDisplay>> getRecipeFor(EntryStack<?> entry) {
        return recipes(entry, false);
    }

    @Override
    public Optional<List<SkyblockRecipeDisplay>> getUsageFor(EntryStack<?> entry) {
        return recipes(entry, true);
    }

    private Optional<List<SkyblockRecipeDisplay>> recipes(EntryStack<?> entry, boolean usage) {
        if (!(entry.getValue() instanceof ItemStack stack)) {
            return Optional.empty();
        }
        String neuId = NeuIds.of(stack);
        if (neuId.isEmpty()) {
            return Optional.empty();
        }

        NeuItemRepository repository = NeuItemRepository.INSTANCE;
        List<NeuItemRepository.Recipe> found = usage ? repository.usagesFor(neuId) : repository.recipesFor(neuId);
        return Optional.of(found.stream()
                .map(SkyblockRecipeDisplay::new)
                .filter(SkyblockRecipeDisplay::isSupported)
                .toList());
    }
}
