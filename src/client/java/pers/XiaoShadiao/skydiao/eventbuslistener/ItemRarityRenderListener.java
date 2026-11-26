package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;

import java.awt.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class ItemRarityRenderListener extends AbstractListener {

    private static final Cache<ItemStack, Optional<Color>> itemRarityCache = CacheBuilder.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(Duration.ofMinutes(5))
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .build();

    private static final Map<String, Color> tooltipColorMap = Map.ofEntries(
            Map.entry("COMMON", new Color(0xFFFFFF)),
            Map.entry("UNCOMMON", new Color(0x55FF55)),
            Map.entry("RARE", new Color(0x459BFF)),
            Map.entry("EPIC", new Color(0x8A2BE2)),
            Map.entry("LEGENDARY", new Color(0xFF9000)),
            Map.entry("MYTHIC", new Color(0xFF55FF)),
            Map.entry("SUPREME", new Color(0x55FFFF)),
            Map.entry("SPECIAL", new Color(0xFF5555)),
            Map.entry("VERY SPECIAL", new Color(0xFF5555)),
            Map.entry("ULTIMATE", new Color(0xD13228)),
            Map.entry("ADMIN", new Color(0xD13228))
    );

    @Override
    public String getListenerName() {
        return "ItemRarityRenderListener";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.ON_RENDER_ITEM.register(this::onRenderItem);
    }

    private void onRenderItem(GuiGraphicsExtractor guiGraphicsExtractor, ItemStack itemStack, int x, int y) {
        if (!ConfigManager.itemRarityRender.getValue()) {
            return;
        }

        if (itemStack.isEmpty()) {
            return;
        }

            Optional<Color> color;
            try {
                color = itemRarityCache.get(itemStack, () -> {
                    Color r = null;
                    if (itemStack.getComponents().has(DataComponents.TOOLTIP_STYLE)) {
                        Identifier tooltipStyle = itemStack.getComponents().get(DataComponents.TOOLTIP_STYLE);
                        // System.out.println(tooltipStyle + " " + itemStack.getHoverName().getString());
                        if ("hypixel_skyblock".equals(tooltipStyle.getNamespace())) {
                            // 与 Skyblocker 的 SkyBlockColors 保持一致
                            r = switch (tooltipStyle.getPath()) {
                                case "uncommon" -> new Color(0x55FF55);
                                case "rare" -> new Color(0x459BFF);
                                case "epic" -> new Color(0x8A2BE2);
                                case "legendary" -> new Color(0xFF9000);
                                case "mythic" -> new Color(0xFF55FF);
                                case "supreme" -> new Color(0x55FFFF);
                                case "special", "very_special" -> new Color(0xFF5555);
                                case "ultimate", "admin" -> new Color(0xD13228);
                                default -> null;
                            };
                        }
                    }
                    if (r == null) {
                        List<Component> lines = itemStack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
                        for (int i = lines.size() - 1; i >= 0; i--) {
                            Component line = lines.get(i);
                            for (Map.Entry<String, Color> entry : tooltipColorMap.entrySet()) {
                                if (line.getString().contains(entry.getKey())) {
                                    return Optional.of(entry.getValue());
                                }
                            }
                        }
                    }
                    return Optional.ofNullable(r);
                });
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
            color.ifPresent(value -> guiGraphicsExtractor.fill(x, y, x + 16, y + 16, (value.getRGB() | 0xFF000000) & 0x66FFFFFF));
    }
}
