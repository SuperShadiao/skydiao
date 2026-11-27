package pers.XiaoShadiao.skydiao.compat.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * NEU 怪物掉落表在 REI 中的展示数据。
 *
 * <p>输入为怪物伪物品，输出为全部掉落物，因此：
 * 对怪物按 U 可以看到掉落表，对掉落物按 R 可以看到来源怪物。
 */
public class MobDropDisplay implements Display {

    public static final Identifier CATEGORY_ID =
            Identifier.fromNamespaceAndPath("skydiao", "mob_drops");

    private final NeuItemRepository.MobDropRecipe mobDrop;

    public MobDropDisplay(NeuItemRepository.MobDropRecipe mobDrop) {
        this.mobDrop = mobDrop;
    }

    public NeuItemRepository.MobDropRecipe mobDrop() {
        return mobDrop;
    }

    public ItemStack sourceStack() {
        try {
            return NeuItemRepository.INSTANCE.find(mobDrop.sourceItem())
                    .map(NeuItemRepository.INSTANCE::createDisplayStack)
                    .orElse(ItemStack.EMPTY);
        } catch (Throwable throwable) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        ItemStack source = sourceStack();
        if (source.isEmpty() || source.is(Items.BARRIER)) {
            return List.of();
        }
        return List.of(EntryIngredient.of(EntryStacks.of(source)));
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        List<EntryIngredient> entries = new ArrayList<>(mobDrop.drops().size());
        for (NeuItemRepository.MobDrop drop : mobDrop.drops()) {
            ItemStack stack = MobLootStacks.dropStack(drop, false);
            if (!stack.isEmpty()) {
                entries.add(EntryIngredient.of(EntryStacks.of(stack)));
            }
        }
        return entries;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return CategoryIdentifier.of(CATEGORY_ID);
    }

    @Override
    public Optional<Identifier> getDisplayLocation() {
        return Optional.empty();
    }

    @Override
    public DisplaySerializer<? extends Display> getSerializer() {
        return null;
    }
}
