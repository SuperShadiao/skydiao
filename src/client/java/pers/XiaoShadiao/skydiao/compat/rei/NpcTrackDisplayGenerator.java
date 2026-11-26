package pers.XiaoShadiao.skydiao.compat.rei;

import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.world.item.ItemStack;
import pers.XiaoShadiao.skydiao.utils.neu.NeuIds;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;

import java.util.List;
import java.util.Optional;

/**
 * 为带坐标的 NPC 提供“位置追踪”条目。
 */
public class NpcTrackDisplayGenerator implements DynamicDisplayGenerator<NpcTrackDisplay> {

    @Override
    public Optional<List<NpcTrackDisplay>> getRecipeFor(EntryStack<?> entry) {
        return display(entry);
    }

    @Override
    public Optional<List<NpcTrackDisplay>> getUsageFor(EntryStack<?> entry) {
        return display(entry);
    }

    private Optional<List<NpcTrackDisplay>> display(EntryStack<?> entry) {
        if (!(entry.getValue() instanceof ItemStack stack)) {
            return Optional.empty();
        }
        String neuId = NeuIds.of(stack);
        if (neuId.isEmpty()) {
            return Optional.empty();
        }
        return NeuItemRepository.INSTANCE.find(neuId)
                .filter(NeuItemRepository.NeuItem::hasLocation)
                .map(npc -> Optional.of(List.of(new NpcTrackDisplay(npc))))
                .orElse(Optional.empty());
    }
}
