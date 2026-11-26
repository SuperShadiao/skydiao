package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

public class EnchantedBookPreview extends AbstractListener {

    private static final Cache<ItemStack, String> shortedEnchantedNameCache = CacheBuilder.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(Duration.ofMinutes(5))
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .build();

    @Override
    public String getListenerName() {
        return "EnchantedBookPreview";
    }
    @Override
    public void registerListeners() {
        CustomFabricEvents.RENDER_ITEM_COUNT_OVERRIDE.register(this::replaceAsEnchantedName);
    }

    private String replaceAsEnchantedName(ItemStack itemStack, @Nullable String original) {
        if(ConfigManager.enchantedBookPreview.getValue() && ToolList.getInstance().tryGetSkyblockItemId(itemStack).equals("ENCHANTED_BOOK")) {
            try {
                return shortedEnchantedNameCache.get(itemStack, () -> {
                    // List<Component> lines = itemStack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
                    boolean isUltimateEnchantment = false;// = lines.stream().anyMatch(line -> line.getString().contains("You can only have 1 Ultimate"));

                    String itemName;

                    Object2IntMap<String> enchantmentsMap = ToolList.getInstance().tryGetSkyblockItemEnchantments(itemStack);

//                    List<String> enchantLevelSuffixes = List.of("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X");
//                    a:{
//                        for (int i = 0; i < Math.min(lines.size(), 5); i++) {
//                            itemName = lines.get(i).getString();
//                            for (String enchantLevelSuffix : enchantLevelSuffixes) {
//                                if(itemName.endsWith(enchantLevelSuffix)) {
//                                    break a;
//                                }
//                            }
//                        }
//
//                        return "";
//                    }
                    if(enchantmentsMap.isEmpty()) return "";

                    itemName = enchantmentsMap.keySet().iterator().next();

                    String[] itemNameParts;
                    String ultimateEnchantPrefix = "ultimate_";
                    if(itemName.startsWith(ultimateEnchantPrefix)) {
                        isUltimateEnchantment = true;
                        if(!itemName.equals("ultimate_wise") && !itemName.equals("ultimate_jerry")) {
                            itemName = itemName.substring(ultimateEnchantPrefix.length());
                        }
                    }

                    itemNameParts = itemName.split("_");
                    StringBuilder sb = new StringBuilder();

                    int limit = itemNameParts.length > 3 ? 2 : itemNameParts.length == 3 ? 3 : 2;
                    int i = 0;
                    if(itemNameParts.length > 1) {
                        for(String part : itemNameParts) {
                            if(!part.isEmpty()) sb.append(part.charAt(0));
                            i++;
                            if(i >= limit) break;
                        }
                    } else {
                        sb.append(itemNameParts[0], 0, Math.min(2, itemNameParts[0].length()));
                    }
                    if(!sb.isEmpty()) {
                        if(isUltimateEnchantment) {
                            return "§d" + sb.toString().toUpperCase();
                        } else {
                            return "§9" + sb.toString().toUpperCase();
                        }
                    }
                    return "";
                });
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return original;
    }

}
