package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class DungeonAutoGFSListener extends AbstractListener {

    public record RequiredItem(String itemId, int requiredCount, int getCount, BooleanSupplier enabled) {}

    private static final List<RequiredItem> items = List.of(
            new RequiredItem("SPIRIT_LEAP", 8, 5, ConfigManager.dungeonautogfsleap::getValue),
            new RequiredItem("SUPERBOOM_TNT", 30, 20, ConfigManager.dungeonautogfstnt::getValue),
            new RequiredItem("DUNGEON_DECOY", 30, 20, ConfigManager.dungeonautogfsdecoy::getValue),
            new RequiredItem("ENDER_PEARL", 8, 5, ConfigManager.dungeonautogfsenderpearl::getValue)
    );

    private final Map<String, RequiredItem> idToItem = Util.make(() -> {
        Map<String, RequiredItem> map = new HashMap<>();
        items.forEach(item -> map.put(item.itemId, item));
        return map;
    });

    @Override
    public String getListenerName() {
        return "DungeonAutoGFSListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
    }

    private int tick = 0;

    private void onStartClientTick(Minecraft mc) {
        if(!ConfigManager.dungeonautogfs.getValue() || mc.level == null || mc.player == null || !StatusManager.get().isInDungeon()) return;

        tick++;
        if(tick % 200 != 0) return;

        Object2IntMap<String> itemCount = new Object2IntOpenHashMap<>();
        itemCount.defaultReturnValue(0);

        boolean hasEmptySlot = false;
        for (ItemStack itemStack : mc.player.getInventory().getNonEquipmentItems()) {
            if (itemStack.isEmpty()) {
                hasEmptySlot = true;
                continue;
            }
            String itemId = ToolList.getInstance().tryGetSkyblockItemId(itemStack);
            if (itemId.isEmpty()) continue;
            if (!idToItem.containsKey(itemId)) continue;

            itemCount.put(itemId, itemCount.getInt(itemId) + itemStack.getCount());
        }

        if(!hasEmptySlot) return; // 不管怎么说, 我先来, You have no space in your inventory!

        for (RequiredItem requiredItem : items) {
            if(requiredItem.getCount() > itemCount.getInt(requiredItem.itemId) && requiredItem.enabled.getAsBoolean()) {
                // /gfs SPIRIT_LEAP 1
                ToolList.sendChatMessage("/gfs " + requiredItem.itemId + " " + requiredItem.getCount);
                ToolList.printChatMessage(Component.literal("§a[小沙雕] §e自动从Sack拿取物品" + requiredItem.itemId));
                tick = 130;
                return;
            }
        }
    }

}
