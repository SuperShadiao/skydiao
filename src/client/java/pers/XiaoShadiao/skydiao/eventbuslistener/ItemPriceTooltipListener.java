package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.itemprice.AttributeShardRegistry;
import pers.XiaoShadiao.skydiao.utils.itemprice.NetworthDataManager;
import pers.XiaoShadiao.skydiao.utils.itemprice.PriceDataManager;

import java.util.List;
import java.util.Locale;

/**
 * 物品价格 tooltip：Bazaar / Lowest BIN / 均价。
 *
 * <p>数据由 {@link PriceDataManager} 在后台线程定时刷新，这里只做内存查表。
 * 渲染线程里不要发 HTTP 请求，也不要做重量级 NBT 解析。</p>
 *
 * <p>当前是初版：
 * <ul>
 *     <li>数量直接用 {@code stack.getCount()}，没有解析 Sack / Stash / Superpairs 的数量；</li>
 *     <li>Bazaar 物品只显示 Buy / Sell，不再显示 Lowest BIN；</li>
 *     <li>均价默认只显示 3 天，想显示 1 天改 {@link #SHOW_ONE_DAY_AVERAGE}。</li>
 * </ul>
 */
public class ItemPriceTooltipListener extends AbstractListener {

    private static final boolean SHOW_ONE_DAY_AVERAGE = false;
    private static final boolean SHOW_THREE_DAY_AVERAGE = true;

    private static final ChatFormatting LABEL_COLOR = ChatFormatting.GOLD;
    private static final ChatFormatting PRICE_COLOR = ChatFormatting.DARK_AQUA;

    @Override
    public String getListenerName() {
        return "ItemPriceTooltipListener";
    }

    @Override
    public void registerListeners() {
        ItemTooltipCallback.EVENT.register(this::onItemTooltip);
    }

    private void onItemTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
        if (!ConfigManager.itemPriceTooltip.getValue()) return;
        if (stack.isEmpty() || !ToolList.isInSkyblock()) return;

        String apiId = ToolList.getInstance().tryGetSkyblockItemApiId(stack);
        if (apiId.isEmpty()) {
            apiId = resolveHuntingBoxShardId(stack);
            if (apiId.isEmpty()) return;
        }

        int count = Math.max(stack.getCount(), 1);

        // Bazaar 优先。Bazaar 物品不再显示 Lowest BIN，和后端数据的分工一致。
        if (PriceDataManager.isBazaarReady()) {
            PriceDataManager.BazaarProduct product = PriceDataManager.getBazaarProduct(apiId);
            if (product != null) {
                lines.add(priceLine("Bazaar Buy Price:", product.weightedBuyPrice(), count));
                lines.add(priceLine("Bazaar Sell Price:", product.bestBid(), count));
                return;
            }
        }

        if (PriceDataManager.isAuctionDataReady()) {
            Double lowestBin = PriceDataManager.getLowestBin(apiId);
            if (lowestBin != null) {
                lines.add(priceLine("Lowest BIN Price:", lowestBin, count));
            }
        }

        if (SHOW_ONE_DAY_AVERAGE) {
            Double average1Day = PriceDataManager.getAverage1Day(apiId);
            if (average1Day != null) {
                lines.add(priceLine("1 Day Avg. Price:", average1Day, count));
            }
        }

        if (SHOW_THREE_DAY_AVERAGE) {
            Double average3Day = PriceDataManager.getAverage3Day(apiId);
            if (average3Day != null) {
                lines.add(priceLine("3 Day Avg. Price:", average3Day, count));
            }
        }

        // Est. Item Value：和 Skyblocker 一样，Bazaar 物品已在上面提前返回
        double estimatedValue = NetworthDataManager.getEstimatedValue(
                stack, ToolList.getInstance().tryGetSkyblockItemId(stack), apiId, count);
        if (estimatedValue > 0) {
            lines.add(estimatedValueLine(estimatedValue, count));
        }
    }

    /**
     * Hunting Box 里的 shard 是只有显示名、没有 custom_data.id 的菜单物品
     * （例如显示为 {@code Flaming Spider}，而实际物品叫 {@code Flaming Spider Shard}），
     * 优先用 NEU 的 displayName 映射，再用 Bazaar 产品名兜底反查 id。
     */
    private static String resolveHuntingBoxShardId(ItemStack stack) {
        Screen screen = ToolList.mc.gui.screen();
        if (screen == null || !screen.getTitle().getString().contains("Hunting Box")) return "";

        String name = stack.getHoverName().getString();
        // NEU 的 displayName 映射是权威来源（Bogged -> SHARD_SEA_ARCHER、Zealot Bruiser -> SHARD_BRUISER）
        String id = AttributeShardRegistry.getBazaarNameByDisplayName(name);
        if (id == null) {
            // 兜底：用 Bazaar 产品名反查，覆盖比内置资源更新的碎片
            id = PriceDataManager.getShardBazaarIdByDisplayName(name);
        }
        return id == null ? "" : id;
    }

    private static Component priceLine(String label, Double price, int count) {
        MutableComponent line = Component.literal(label + " ").withStyle(LABEL_COLOR);
        return line.append(formatPrice(price, count));
    }

    private static Component estimatedValueLine(double total, int count) {
        MutableComponent line = Component.literal("Est. Item Value: ").withStyle(LABEL_COLOR);
        return line.append(formatPreCountedPrice(total, count));
    }

    /**
     * 和 Skyblocker 的 ItemTooltip#getCoinsMessage 保持一样：数量大于 1 时显示总价和单价。
     */
    private static Component formatPrice(Double price, int count) {
        if (price == null || price <= 0) {
            return Component.literal("No data").withStyle(ChatFormatting.RED);
        }

        if (count <= 1) {
            return Component.literal(format(price) + " Coins").withStyle(PRICE_COLOR);
        }

        return Component.literal(format(price * count) + " Coins ").withStyle(PRICE_COLOR)
                .append(Component.literal("(" + format(price) + " each)").withStyle(ChatFormatting.GRAY));
    }

    /**
     * 和 Skyblocker 的 {@code ItemTooltip#getCoinsMessage(price, count, true)} 一致：
     * 传入的 total 已经包含数量，单价由总价除以数量得到。
     */
    private static Component formatPreCountedPrice(double total, int count) {
        if (count <= 1) {
            return Component.literal(format(total) + " Coins").withStyle(PRICE_COLOR);
        }

        return Component.literal(format(total) + " Coins ").withStyle(PRICE_COLOR)
                .append(Component.literal("(" + format(total / count) + " each)").withStyle(ChatFormatting.GRAY));
    }

    private static String format(double value) {
        return String.format(Locale.ENGLISH, "%1$,.1f", value);
    }
}
