package pers.XiaoShadiao.skydiao.server.eventbuslistener;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

public class EndermanDropSomething extends AbstractListener {

    @Override
    public String getListenerName() {
        return "EndermanDropSomething";
    }

    @Override
    public void registerListeners() {
        LootTableEvents.MODIFY.register(this::modifyLootTable);
    }

    private void modifyLootTable(ResourceKey<LootTable> key, LootTable.Builder lootTable, LootTableSource lootTableSource, HolderLookup.Provider provider) {
        if(key.equals(EntityType.ENDERMAN.getDefaultLootTable().get())) {
            lootTable.withPool(LootPool.lootPool()
                    .add(LootItem.lootTableItem(Items.DIAMOND).setWeight(1000).apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 10))))
                    .add(LootItem.lootTableItem(Items.NETHERITE_INGOT).setWeight(100).apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 100))))
                    .setRolls(ConstantValue.exactly(1.0F))
            );
        }
    }

}
