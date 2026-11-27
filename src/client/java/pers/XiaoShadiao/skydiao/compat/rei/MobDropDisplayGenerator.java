package pers.XiaoShadiao.skydiao.compat.rei;

import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.world.item.ItemStack;
import pers.XiaoShadiao.skydiao.utils.neu.NeuIds;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;

import java.util.List;
import java.util.Optional;

/**
 * 根据 NEU 的 drops 配方提供 Mob Drops 展示。
 *
 * <p>对掉落物按 R：返回包含该掉落物的怪物掉落表；
 * 对怪物伪物品按 U：返回该怪物的掉落表。
 */
public class MobDropDisplayGenerator implements DynamicDisplayGenerator<MobDropDisplay> {

    @Override
    public Optional<List<MobDropDisplay>> getRecipeFor(EntryStack<?> entry) {
        if (!(entry.getValue() instanceof ItemStack stack)) {
            return Optional.empty();
        }
        String neuId = NeuIds.of(stack);
        if (neuId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(NeuItemRepository.INSTANCE.mobDropsForDrop(neuId)
                .stream()
                .map(MobDropDisplay::new)
                .toList());
    }

    @Override
    public Optional<List<MobDropDisplay>> getUsageFor(EntryStack<?> entry) {
        if (!(entry.getValue() instanceof ItemStack stack)) {
            return Optional.empty();
        }
        String neuId = NeuIds.of(stack);
        if (neuId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(NeuItemRepository.INSTANCE.mobDropsForMob(neuId)
                .stream()
                .map(MobDropDisplay::new)
                .toList());
    }

    @Override
    public Optional<List<MobDropDisplay>> generate(ViewSearchBuilder builder) {
        return Optional.empty();
    }
}
