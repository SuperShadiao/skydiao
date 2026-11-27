package pers.XiaoShadiao.skydiao.compat.rei;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;
import pers.XiaoShadiao.skydiao.utils.text.LegacyText;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 {@link NeuItemRepository.MobDrop} 转成 REI 展示用的 ItemStack。
 */
final class MobLootStacks {

    private static final Pattern LEADING_PERCENT =
            Pattern.compile("^([0-9]*\\.?[0-9]+%)");

    private MobLootStacks() {
    }

    static ItemStack dropStack(NeuItemRepository.MobDrop drop, boolean withLootLore) {
        ItemStack stack;
        try {
            stack = RecipeStacks.resolve(drop.id());
        } catch (Throwable throwable) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty() || !withLootLore) {
            return stack;
        }

        List<Component> lines = new ArrayList<>();
        ItemLore existing = stack.get(DataComponents.LORE);
        if (existing != null) {
            lines.addAll(existing.lines());
        }
        if (drop.chance() != null && !drop.chance().isBlank()) {
            lines.add(Component.literal("Chance: " + drop.chance())
                    .withStyle(ChatFormatting.GOLD));
        }
        for (String extra : drop.extra()) {
            lines.add(LegacyText.parse(extra));
        }
        if (!drop.alternatives().isEmpty()) {
            lines.add(Component.literal("Alternatives:").withStyle(ChatFormatting.DARK_GRAY));
            for (NeuItemRepository.MobDropAlternative alternative : drop.alternatives()) {
                ItemStack alternativeStack = RecipeStacks.resolve(alternative.id());
                String name = alternativeStack.isEmpty() || alternativeStack.is(Items.BARRIER)
                        ? alternative.id()
                        : alternativeStack.getHoverName().getString();
                String chance = alternative.chance() == null || alternative.chance().isBlank()
                        ? ""
                        : " (" + alternative.chance() + ")";
                lines.add(Component.literal(" - " + name + chance)
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
        return stack;
    }

    /**
     * 槽位下方只能放下很短的概率文字；复杂概率（例如 "10% + 10% per hit"）
     * 只取开头百分比，完整字符串仍在 tooltip 里。
     */
    static String shortChance(String chance) {
        if (chance == null || chance.isBlank()) {
            return "";
        }
        Matcher matcher = LEADING_PERCENT.matcher(chance.trim());
        return matcher.find() ? matcher.group(1) : "";
    }
}
