package pers.XiaoShadiao.skydiao.mixin.client.odinfix;

import com.odtheking.odin.utils.skyblock.dungeon.terminals.terminalhandler.SelectAllHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(SelectAllHandler.class)
public class MixinOdinPickColorTerminalFixer {

    @Final
    @Shadow
    private DyeColor color;

    @Overwrite
    public @NotNull List<Integer> solve(List<ItemStack> items) {
        List<Integer> result = new ArrayList<>();
        String colorLowCase = color.name().replace("_", " ").toLowerCase();
        for (int index = 0; index < items.size(); index++) {
            ItemStack item = items.get(index);
            String s = ToolList.getInstance().deleteColorCode(item.getHoverName().getString());
            String itemLowCase = s.toLowerCase();
            System.out.println(s);
            if (!hasGlint(item)
                    && item.getItem() != Items.BLACK_STAINED_GLASS_PANE
                    && (itemLowCase.startsWith(colorLowCase) || itemLowCase.endsWith(colorLowCase)
                    || matchesSpecialCase(item))) {
                result.add(index);
            }
        }
        return result;
    }

    @Unique
    private static boolean hasGlint(ItemStack stack) {
        return Optional.ofNullable(stack.getComponentsPatch().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)).map(Optional::isPresent).isPresent();
    }

    @Unique
    private boolean matchesSpecialCase(ItemStack item) {
        return switch (color) {
            case BLACK -> item.getItem() == Items.INK_SAC;
            case BLUE -> item.getItem() == Items.LAPIS_LAZULI;
            case BROWN -> item.getItem() == Items.COCOA_BEANS;
            case WHITE -> item.getItem() == Items.BONE_MEAL;
            default -> false;
        };
    }

}

