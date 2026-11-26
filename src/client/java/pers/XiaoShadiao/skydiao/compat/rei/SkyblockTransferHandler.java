package pers.XiaoShadiao.skydiao.compat.rei;

import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import pers.XiaoShadiao.skydiao.utils.neu.NeuIds;

/**
 * 只在 Skyblock Crafting 配方上点击“+”时，发送 Hypixel 的 /viewrecipe。
 * Forge / NPC Shop / Kat 等配方没有对应指令，加号按钮会被禁用。
 */
public class SkyblockTransferHandler implements TransferHandler {

    @Override
    public double getPriority() {
        return 1;
    }

    @Override
    public ApplicabilityResult checkApplicable(Context context) {
        if (context.getDisplay() instanceof NpcTrackDisplay) {
            return ApplicabilityResult.createApplicable();
        }
        if (!isCraftingCategory(context.getDisplay().getCategoryIdentifier())) {
            return ApplicabilityResult.createNotApplicable();
        }
        String id = outputId(context);
        if (id.isEmpty()) {
            return ApplicabilityResult.createNotApplicable();
        }
        return ApplicabilityResult.createApplicable();
    }

    @Override
    public Result handle(Context context) {
        if (!context.isActuallyCrafting()) {
            return Result.createSuccessful();
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return Result.createFailed(net.minecraft.network.chat.Component.literal("无法发送指令"));
        }

        if (context.getDisplay() instanceof NpcTrackDisplay npcTrack) {
            minecraft.player.connection.sendCommand(
                    "skydiao tracknpc track " + npcTrack.npc().internalName());
            return Result.createSuccessful();
        }

        String id = outputId(context);
        if (id.isEmpty()) {
            return Result.createFailed(net.minecraft.network.chat.Component.literal("无法发送 /viewrecipe"));
        }
        minecraft.player.connection.sendCommand("viewrecipe " + id);
        return Result.createSuccessful();
    }

    private static boolean isCraftingCategory(CategoryIdentifier<?> identifier) {
        return identifier.equals(CategoryIdentifier.of(SkyblockRecipeDisplay.CRAFTING_ID));
    }

    private static String outputId(Context context) {
        EntryIngredient outputs = context.getDisplay().getOutputEntries().isEmpty()
                ? null : context.getDisplay().getOutputEntries().getFirst();
        if (outputs == null || outputs.isEmpty()) {
            return "";
        }
        EntryStack<?> stack = outputs.getFirst();
        if (!(stack.getValue() instanceof ItemStack itemStack)) {
            return "";
        }
        return NeuIds.of(itemStack);
    }
}
