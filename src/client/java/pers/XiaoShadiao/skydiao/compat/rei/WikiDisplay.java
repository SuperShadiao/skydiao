package pers.XiaoShadiao.skydiao.compat.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;

import java.util.List;
import java.util.Optional;

/**
 * 物品 Wiki 页面条目：卡片中心提供一个打开 Wiki 的按钮。
 */
public class WikiDisplay implements Display {

    public static final Identifier CATEGORY_ID = Identifier.fromNamespaceAndPath("skydiao", "wiki");

    private final NeuItemRepository.NeuItem item;
    private final ItemStack stack;

    public WikiDisplay(NeuItemRepository.NeuItem item) {
        this.item = item;
        this.stack = NeuItemRepository.INSTANCE.createDisplayStack(item);
    }

    public NeuItemRepository.NeuItem item() {
        return item;
    }

    public ItemStack stack() {
        return stack;
    }

    public String url() {
        return item.info().isEmpty() ? null : item.info().getFirst();
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        return stack.isEmpty() ? List.of() : List.of(EntryIngredient.of(EntryStacks.of(stack)));
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        return stack.isEmpty() ? List.of() : List.of(EntryIngredient.of(EntryStacks.of(stack)));
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
