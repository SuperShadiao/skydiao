package pers.XiaoShadiao.skydiao.compat.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;

import java.util.List;
import java.util.Optional;

/**
 * NPC 位置追踪条目：展示 NPC 及坐标，点“+”发送 /skydiao tracknpc track。
 */
public class NpcTrackDisplay implements Display {

    public static final Identifier CATEGORY_ID = Identifier.fromNamespaceAndPath("skydiao", "npc_track");

    private final NeuItemRepository.NeuItem npc;
    private final ItemStack npcStack;
    private final ItemStack trackStack;

    public NpcTrackDisplay(NeuItemRepository.NeuItem npc) {
        this.npc = npc;
        this.npcStack = NeuItemRepository.INSTANCE.createDisplayStack(npc);

        this.trackStack = new ItemStack(Items.COMPASS);
        this.trackStack.set(DataComponents.CUSTOM_NAME,
                Component.literal("Track " + npc.cleanName()).withStyle(ChatFormatting.GOLD));
        this.trackStack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(npc.locationText()).withStyle(ChatFormatting.GRAY),
                Component.literal("点击 + 发送 /skydiao tracknpc track").withStyle(ChatFormatting.DARK_GRAY)
        )));
    }

    public NeuItemRepository.NeuItem npc() {
        return npc;
    }

    public ItemStack npcStack() {
        return npcStack;
    }

    public ItemStack trackStack() {
        return trackStack;
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        return npcStack.isEmpty() ? List.of() : List.of(EntryIngredient.of(EntryStacks.of(npcStack)));
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        return List.of(EntryIngredient.of(EntryStacks.of(trackStack)));
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
