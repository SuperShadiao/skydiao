package pers.XiaoShadiao.skydiao.compat.rei;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import pers.XiaoShadiao.skydiao.utils.neu.NeuItemRepository;

import java.util.List;
import java.util.Locale;

/**
 * 把配方里的 {@code INTERNAL_NAME:count} token 解析成用于展示的 ItemStack。
 */
final class RecipeStacks {

    private RecipeStacks() {
    }

    static ItemStack resolve(String token) {
        if (token == null || token.isBlank()) {
            return ItemStack.EMPTY;
        }

        String id = token.trim();
        int count = 1;
        int colon = id.lastIndexOf(':');
        if (colon > 0) {
            String countPart = id.substring(colon + 1);
            try {
                count = Math.max(1, (int) Math.round(Double.parseDouble(countPart)));
                id = id.substring(0, colon);
            } catch (NumberFormatException ignored) {
                // 没有数量，整个 token 当作 id
            }
        }

        String normalized = id.toUpperCase(Locale.ROOT);
        if (normalized.equals("SKYBLOCK_COIN") || normalized.equals("COINS") || normalized.equals("COIN")) {
            return coinStack(count);
        }

        final String itemId = id;
        ItemStack stack = NeuItemRepository.INSTANCE.find(itemId)
                .map(NeuItemRepository.INSTANCE::createDisplayStack)
                .orElseGet(() -> {
                    ItemStack placeholder = new ItemStack(Items.BARRIER);
                    placeholder.set(DataComponents.CUSTOM_NAME,
                            Component.literal(itemId).withStyle(ChatFormatting.RED));
                    return placeholder;
                });

        if (stack.isEmpty()) {
            return stack;
        }
        if (count > 1) {
            stack.setCount(count);
            stack.set(DataComponents.MAX_STACK_SIZE, count);
        }
        return stack;
    }

    static ItemStack resolveOutput(String token, int count) {
        ItemStack stack = resolve(token);
        if (!stack.isEmpty() && count > 1 && stack.getCount() <= 1) {
            stack.setCount(count);
            stack.set(DataComponents.MAX_STACK_SIZE, count);
        }
        return stack;
    }

    private static ItemStack coinStack(int count) {
        ItemStack stack = new ItemStack(Items.GOLD_NUGGET, Math.min(64, Math.max(1, count)));
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("Skyblock Coins").withStyle(ChatFormatting.GOLD));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(String.format("%,d", count)).withStyle(ChatFormatting.GOLD)
        )));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }
}
