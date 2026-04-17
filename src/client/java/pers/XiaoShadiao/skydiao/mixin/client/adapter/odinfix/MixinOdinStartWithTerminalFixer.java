package pers.XiaoShadiao.skydiao.mixin.client.adapter.odinfix;

import com.odtheking.odin.utils.ItemUtilsKt;
import com.odtheking.odin.utils.skyblock.dungeon.terminals.terminalhandler.StartsWithHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.*;

@Mixin(StartsWithHandler.class)
public class MixinOdinStartWithTerminalFixer {

    @Final
    @Shadow
    private String letter;
    @Final
    @Shadow
    private Set<Integer> clickedSlots;
    @Shadow
    private int lastContainerId;


    @Overwrite
    public @NotNull List<Integer> solve(List<ItemStack> items) {
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            ItemStack item = items.get(index);
            if (item.getHoverName() != null) {
                String s = ToolList.getInstance().deleteColorCode(item.getHoverName().getString());
                if (s.toLowerCase().startsWith(letter.toLowerCase())
                        && !hasGlint(item)
                        && !clickedSlots.contains(index)) {
                    result.add(index);
                }
            }
        }
        return result;
    }

    @Unique
    private static boolean hasGlint(ItemStack stack) {
        return Optional.ofNullable(stack.getComponentsPatch().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)).map(Optional::isPresent).isPresent();
    }
}
