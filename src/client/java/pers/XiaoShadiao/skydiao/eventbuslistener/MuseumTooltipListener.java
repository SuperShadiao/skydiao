package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.museum.MuseumDataManager;

import java.util.List;

/**
 * 博物馆 tooltip：可捐赠物品显示是否已捐赠，行为对齐 Skyblocker 的 MuseumTooltip。
 *
 * <p>判定优先级：
 * <ol>
 *     <li>物品 NBT 的 {@code custom_data.donated_museum}（手里这件确实捐过，零请求）；</li>
 *     <li>后端返回的 {@code collectedItemIds}（该物品 id 在档案里捐过任意一件）。</li>
 * </ol>
 * 数据未就绪时什么都不显示，避免误报。</p>
 */
public class MuseumTooltipListener extends AbstractListener {

    private static final ChatFormatting LABEL_COLOR = ChatFormatting.LIGHT_PURPLE;
    private static final ChatFormatting DONATED_COLOR = ChatFormatting.GREEN;
    private static final ChatFormatting NOT_DONATED_COLOR = ChatFormatting.RED;

    @Override
    public String getListenerName() {
        return "MuseumTooltipListener";
    }

    @Override
    public void registerListeners() {
        ItemTooltipCallback.EVENT.register(this::onItemTooltip);
    }

    private void onItemTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
        if (!ConfigManager.itemMuseumDonateStateTooltip.getValue()) return;
        if (stack.isEmpty() || !ToolList.isInSkyblock()) return;

        String itemId = ToolList.getInstance().tryGetSkyblockItemId(stack);
        if (itemId.isEmpty() || !MuseumDataManager.isEligible(itemId)) return;

        Boolean donated = isLocallyDonated(stack) ? Boolean.TRUE : MuseumDataManager.isDonated(itemId);
        if (donated == null) return;

        ChatFormatting color = donated ? DONATED_COLOR : NOT_DONATED_COLOR;
        lines.add(Component.literal("Museum: ").withStyle(LABEL_COLOR)
                .append(Component.literal(donated ? "✔ Donated" : "✖ Not Donated")
                        .withStyle(color, ChatFormatting.BOLD)));
    }

    /**
     * 手里这件物品自身的捐赠标记，捐赠过的物品会被 Hypixel 写入 {@code donated_museum:1b}。
     */
    private static boolean isLocallyDonated(ItemStack stack) {
        return stack.getComponents()
                .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getBooleanOr("donated_museum", false);
    }
}
