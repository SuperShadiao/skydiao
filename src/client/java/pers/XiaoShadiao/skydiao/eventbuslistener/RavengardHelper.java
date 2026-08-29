package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.objects.Object2BooleanFunction;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class RavengardHelper extends AbstractListener {

    private final Map<SlotFilter, List<Slot>> caches = new HashMap<>();

    @Override
    public String getListenerName() {
        return "RavengardHelper";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.ON_SLOT_RENDER.register(this::renderSlots);
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft mc) {
        if(!ConfigManager.ravengardHelper.getValue() || !(mc.screen instanceof AbstractContainerScreen<?> abstractContainerScreen) || !StatusManager.get().isInRavengard()) {
            caches.clear();
            return;
        }

        AbstractContainerMenu menu = abstractContainerScreen.getMenu();

        for (SlotFilter slotFilter : SlotFilter.values()) {
            caches.put(slotFilter, slotFilter.shouldEnable.test(mc.screen) ? slotFilter.filter.apply(menu) : List.of());
        }
    }

    public void renderSlots(Screen screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float tickDelta) {
        if(!ConfigManager.ravengardHelper.getValue() || !(screen instanceof AbstractContainerScreen<?> abstractContainerScreen) || !StatusManager.get().isInRavengard()) return;

        caches.keySet().stream().sorted(Comparator.comparing(SlotFilter::ordinal)).forEach(slotFilter -> {
            List<Slot> slots = caches.get(slotFilter);
            if(slots.isEmpty()) return;
            for (Slot slot : slots) {
                RenderUtils.renderSlot(guiGraphics, abstractContainerScreen, slot, slotFilter.renderColor.getRGB() & 0x88FFFFFF);
            }
        });
    }

    public boolean itemCanUse(Slot slot) {
        return itemCanUse(slot.getItem());
    }

    public boolean itemCanUse(ItemStack itemStack) {
        return !itemCantUse(itemStack);
    }

    public boolean itemCantUse(Slot slot) {
        return itemCantUse(slot.getItem());
    }

    public boolean itemCantUse(ItemStack itemStack) {
        Identifier identifier = itemStack.get(DataComponents.ITEM_MODEL);
        return identifier != null && identifier.getPath().endsWith("_greyed");
    }

    enum SlotFilter {
        VALUE(Color.orange, (menu) -> {
            if(mc.player == null || mc.level == null) return List.of();
            return menu.slots.stream().map(slot -> {
                        double value = 0;
                        List<Component> list = slot.getItem().getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL);
                        for (Component component : list) {
                            String message = ToolList.getInstance().deleteColorCode(component.getString());
                            if(message.contains(" Crown")) {
                                Matcher matcher = Pattern.compile("([\\d.]+) Crown").matcher(message);
                                if(matcher.find()) {
                                    value = Double.parseDouble(matcher.group(1));
                                }
                            }
                        }
                        return Object2DoubleMap.entry(slot, value);
                    }).sorted(Comparator.comparingDouble((Object2DoubleMap.Entry<Slot> entry) -> entry.getDoubleValue()).reversed().thenComparing(entry -> entry.getKey().container != mc.player.getInventory()))
                    .filter(entry -> entry.getDoubleValue() > 0)
                    .map(Object2DoubleMap.Entry::getKey)
                    .limit(31).toList();
        }, screen -> screen instanceof AbstractContainerScreen<?> && !(screen instanceof InventoryScreen)),
        DEFENSE(Color.cyan, (menu) -> {
            if(mc.player == null || mc.level == null) return List.of();
            List<Slot> list1 = menu.slots.stream()
                    .sorted(Comparator.comparingDouble((Slot slot) -> {
                        List<Component> list = slot.getItem().getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL);
                        for (Component component : list) {
                            String message = ToolList.getInstance().deleteColorCode(component.getString());
                            if (message.contains(" Defense")) {
                                Matcher matcher = Pattern.compile("([\\d.]+) Defense").matcher(message);
                                if (matcher.find()) {
                                    return Double.parseDouble(matcher.group(1));
                                }
                            }
                        }
                        return 0;
                    }).reversed())
                    .filter(AbstractListener.ravengardHelper::itemCanUse)
                    .toList();

            Slot chestplate = null;
            Slot leggings = null;
            Slot boots = null;

            for (Slot slot : list1) {
                if(chestplate == null && slot.getItem().getItem().getDescriptionId().endsWith("_chestplate")) {
                    chestplate = slot;
                }
                if(leggings == null && slot.getItem().getItem().getDescriptionId().endsWith("_leggings")) {
                    leggings = slot;
                }
                if(boots == null && slot.getItem().getItem().getDescriptionId().endsWith("_boots")) {
                    boots = slot;
                }
                if(chestplate != null && leggings != null && boots != null) {
                    break;
                }
            }
            return Stream.of(chestplate, leggings, boots)
                    .filter(Objects::nonNull)
                    .toList();
        }, screen -> screen instanceof AbstractContainerScreen<?>),
        DAMAGE(Color.RED, (menu) -> {
            if(mc.player == null || mc.level == null) return List.of();
            List<Slot> list1 = menu.slots.stream()
                    .sorted(Comparator.comparingDouble((Slot slot) -> {
                        List<Component> list = slot.getItem().getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL);
                        double rawDamage = 0;
                        double abilityDamageBoost = 0;
                        for (Component component : list) {
                            String message = ToolList.getInstance().deleteColorCode(component.getString());
                            if (message.contains(" Damage")) {
                                Matcher matcher = Pattern.compile("([\\d.]+) ?(Ranged Attack )?Damage").matcher(message);
                                if (matcher.find()) {
                                    rawDamage = Double.parseDouble(matcher.group(1));
                                } else {
                                    matcher = Pattern.compile("([\\d.]+)% Ability Damage Boost").matcher(message);
                                    if (matcher.find()) {
                                        abilityDamageBoost = Double.parseDouble(matcher.group(1));
                                    }
                                }
                            }
                        }
                        return rawDamage + abilityDamageBoost / 10 * 6;
                    }).reversed().thenComparing(slot -> slot.container != mc.player.getInventory()))
                    .filter(AbstractListener.ravengardHelper::itemCanUse)
                    .toList();

            Slot weapon = null;
            Slot bow = null;

            for (Slot slot : list1) {

                if(bow == null && slot.getItem().getItem() == Items.BOW) {
                    bow = slot;
                } else if(weapon == null && slot.getItem().getItem() != Items.BOW) {
                    weapon = slot;
                }
                if(weapon != null && bow != null) {
                    break;
                }
            }

            return Stream.of(weapon, bow)
                    .filter(Objects::nonNull)
                    .toList();
        }, screen -> screen instanceof AbstractContainerScreen<?>),
        HEALING(Color.magenta, (menu) -> {
            if(mc.player == null || mc.level == null) return List.of();
            return menu.slots.stream()
                    .filter((Slot slot) -> {
                        List<Component> list = slot.getItem().getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL);
                        for (Component component : list) {
                            String message = ToolList.getInstance().deleteColorCode(component.getString());
                            if (message.contains("Heal")) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .toList();
        }, screen -> screen instanceof AbstractContainerScreen<?> && !(screen instanceof InventoryScreen)),

        ;

        private final Color renderColor;
        private final Function<AbstractContainerMenu, List<Slot>> filter;
        private final Object2BooleanFunction<Screen> shouldEnable;

        SlotFilter(Color renderColor, Function<AbstractContainerMenu, List<Slot>> filter, Object2BooleanFunction<Screen> shouldEnable) {
            this.renderColor = renderColor;
            this.filter = filter;
            this.shouldEnable = shouldEnable;
        }

    }

}
