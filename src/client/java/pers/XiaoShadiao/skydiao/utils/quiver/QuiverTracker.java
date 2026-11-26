package pers.XiaoShadiao.skydiao.utils.quiver;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.inventory.Slot;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 追踪当前选择的箭矢类型、数量和颜色。
 * 数据来源与 SkyHanni 一致：背包中的 Arrow Swapper / Quiver 预览 lore，以及相关聊天消息。
 */
public final class QuiverTracker {

    private static final Pattern SELECTED_ARROW = Pattern.compile("Selected: (.+)");
    private static final Pattern ACTIVE_ARROW = Pattern.compile("Active Arrow: (.+?) \\(([\\d,]+)\\)");
    private static final Pattern ARROWS_REMAINING = Pattern.compile("Arrows Remaining: ([\\d,]+)");

    private static final Pattern SELECT_ARROW = Pattern.compile("You set your selected arrow type to (.+)!");
    private static final Pattern ADDED_TO_QUIVER = Pattern.compile("You've added (.+?) x([\\d,]+) to your quiver!");
    private static final Pattern RAN_OUT = Pattern.compile("You have run out of (.+)!");
    private static final Pattern FILLED_QUIVER = Pattern.compile("You filled your quiver with ([\\d,]+) extra arrows!");

    private static final Map<String, ArrowIconInfo> ARROW_ICONS = Map.ofEntries(
            Map.entry("Flint Arrow", new ArrowIconInfo("ARROW", "hypixel_skyblock:item/uncategorized/flint_arrow", false, 0xFFFFFFFF)),
            Map.entry("Bouncy Arrow", new ArrowIconInfo("BOUNCY_ARROW", "hypixel_skyblock:item/uncategorized/bouncy_arrow", true, 0xFF5555FF)),
            Map.entry("Icy Arrow", new ArrowIconInfo("ICY_ARROW", "hypixel_skyblock:item/uncategorized/icy_arrow", true, 0xFF5555FF)),
            Map.entry("Explosive Arrow", new ArrowIconInfo("EXPLOSIVE_ARROW", "hypixel_skyblock:item/uncategorized/explosive_arrow", true, 0xFF5555FF)),
            Map.entry("Armorshred Arrow", new ArrowIconInfo("ARMORSHRED_ARROW", "hypixel_skyblock:item/uncategorized/armorshred_arrow", true, 0xFF5555FF)),
            Map.entry("Glue Arrow", new ArrowIconInfo("GLUE_ARROW", "hypixel_skyblock:item/uncategorized/glue_arrow", true, 0xFF5555FF)),
            Map.entry("Nansorb Arrow", new ArrowIconInfo("NANSORB_ARROW", "hypixel_skyblock:item/uncategorized/nansorb_arrow", true, 0xFF5555FF)),
            Map.entry("Reinforced Iron Arrow", new ArrowIconInfo("REINFORCED_IRON_ARROW", "hypixel_skyblock:item/uncategorized/reinforced_iron_arrow", false, 0xFFFFFFFF)),
            Map.entry("Magma Arrow", new ArrowIconInfo("MAGMA_ARROW", "hypixel_skyblock:item/slayer/blaze/arrows/magma_arrow", false, 0xFFAA00AA)),
            Map.entry("Redstone-tipped Arrow", new ArrowIconInfo("REDSTONE_TIPPED_ARROW", "hypixel_skyblock:item/uncategorized/redstone_tipped_arrow", false, 0xFF55FF55)),
            Map.entry("Gold-tipped Arrow", new ArrowIconInfo("GOLD_TIPPED_ARROW", "hypixel_skyblock:item/uncategorized/gold_tipped_arrow", false, 0xFF55FF55)),
            Map.entry("Emerald-tipped Arrow", new ArrowIconInfo("EMERALD_TIPPED_ARROW", "hypixel_skyblock:item/uncategorized/emerald_tipped_arrow", false, 0xFF55FF55))
    );

    private static final Minecraft MC = Minecraft.getInstance();

    private static String arrowType = "";
    private static int arrowAmount;
    private static int arrowColor = 0xFFFFFFFF;
    private static boolean amountKnown;
    private static ItemStack arrowIcon = ItemStack.EMPTY;
    private static boolean wearingSkeletonMaster;
    private static boolean holdingBow;
    private static boolean hasBowInInventory;

    private static boolean needsScan;
    private static int tickCounter;
    private static boolean initialized;

    private QuiverTracker() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ClientTickEvents.END_CLIENT_TICK.register(QuiverTracker::tick);
        ClientReceiveMessageEvents.GAME.register(QuiverTracker::onChat);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    public static String getArrowType() {
        return arrowType;
    }

    public static int getArrowAmount() {
        return arrowAmount;
    }

    public static int getArrowColor() {
        return arrowColor;
    }

    public static boolean isAmountKnown() {
        return amountKnown;
    }

    public static ItemStack getArrowIcon() {
        return arrowIcon;
    }

    public static boolean isWearingSkeletonMaster() {
        return wearingSkeletonMaster;
    }

    public static boolean shouldShow() {
        if (arrowType.isEmpty()) {
            return false;
        }
        int when = ConfigManager.quiverHUDWhen.getValue();
        return when == 0
                || (when == 1 && hasBowInInventory)
                || (when == 2 && holdingBow);
    }

    private static void tick(Minecraft client) {
        if (!ConfigManager.quiverHUD.getValue() || !ToolList.isInSkyblock()) {
            return;
        }
        Player player = client.player;
        if (player == null) {
            return;
        }
        if (++tickCounter % 5 == 0 || needsScan) {
            needsScan = false;
            updateBowState(player);
            scanInventory(player);
            scanQuiverMenu(client);
        }
    }

    private static void scanQuiverMenu(Minecraft client) {
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        if (!cleanText(screen.getTitle().getString()).contains("Quiver") || arrowType.isEmpty()) {
            return;
        }

        int total = 0;
        ItemStack matchingIcon = null;
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (!(stack.getItem() instanceof ArrowItem)) {
                continue;
            }
            String name = cleanText(stack.getHoverName().getString());
            if (name.equalsIgnoreCase(arrowType) || name.equalsIgnoreCase(arrowType + "s")) {
                total += stack.getCount();
                if (matchingIcon == null) {
                    matchingIcon = stack.copy();
                }
            }
        }

        if (matchingIcon != null) {
            arrowAmount = total;
            amountKnown = true;
            arrowIcon = matchingIcon;
        }
    }

    private static void scanInventory(Player player) {
        String detectedType = null;
        Integer detectedColor = null;
        int detectedAmount = -1;
        boolean detectedAmountKnown = false;

        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) {
                continue;
            }

            List<Component> lore = getLoreComponents(stack);
            if (lore.isEmpty()) {
                continue;
            }

            boolean quiverPreview = hasQuiverArrowTag(stack);
            for (Component line : lore) {
                String text = cleanText(line.getString());

                Matcher selected = SELECTED_ARROW.matcher(text);
                if (selected.find()) {
                    String type = normalizeType(cleanText(selected.group(1)));
                    if (detectedType == null) {
                        detectedType = type;
                    }
                    if (detectedColor == null) {
                        detectedColor = findColor(line, type);
                    }
                }

                Matcher active = ACTIVE_ARROW.matcher(text);
                if (active.find()) {
                    String type = normalizeType(cleanText(active.group(1)));
                    detectedType = type;
                    detectedAmount = parseAmount(active.group(2));
                    detectedAmountKnown = true;
                    Integer color = findColor(line, type);
                    if (color != null) {
                        detectedColor = color;
                    }
                }

                if (quiverPreview) {
                    Matcher remaining = ARROWS_REMAINING.matcher(text);
                    if (remaining.find()) {
                        detectedAmount = parseAmount(remaining.group(1));
                        detectedAmountKnown = true;
                    }
                }
            }
        }

        if (detectedType != null) {
            arrowType = detectedType;
            arrowIcon = createArrowIcon(detectedType);
            ArrowIconInfo info = ARROW_ICONS.get(detectedType);
            if (detectedColor == null && info != null) {
                arrowColor = info.color();
            }
        }
        if (detectedColor != null) {
            arrowColor = 0xFF000000 | detectedColor;
        }
        if (detectedAmountKnown) {
            arrowAmount = detectedAmount;
            amountKnown = true;
        }
    }

    private static String normalizeType(String type) {
        if (type.endsWith("Arrows")) {
            return type.substring(0, type.length() - 1);
        }
        return type;
    }

    private static Integer findColor(Component component, String type) {
        if (component.getString().contains(type)) {
            TextColor color = component.getStyle().getColor();
            if (color != null) {
                return color.getValue();
            }
        }
        for (Component sibling : component.getSiblings()) {
            Integer color = findColor(sibling, type);
            if (color != null) {
                return color;
            }
        }
        return null;
    }

    private static ItemStack createArrowIcon(String type) {
        ItemStack stack = new ItemStack(Items.ARROW);
        if (type.equalsIgnoreCase("NONE")) {
            return stack;
        }

        ArrowIconInfo info = ARROW_ICONS.get(type);
        String id;
        String model;
        boolean glowing;
        if (info != null) {
            id = info.id();
            model = info.model();
            glowing = info.glowing();
        } else {
            id = type.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
            model = "hypixel_skyblock:item/uncategorized/" + id.toLowerCase(Locale.ROOT);
            glowing = false;
        }

        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ITEM_MODEL, Identifier.parse(model));
        if (glowing) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    private static void updateBowState(Player player) {
        holdingBow = player.getMainHandItem().getItem() instanceof BowItem
                || player.getItemBySlot(EquipmentSlot.OFFHAND).getItem() instanceof BowItem;

        hasBowInInventory = holdingBow;
        if (!hasBowInInventory) {
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (stack.getItem() instanceof BowItem) {
                    hasBowInInventory = true;
                    break;
                }
            }
        }

        wearingSkeletonMaster = cleanText(player.getItemBySlot(EquipmentSlot.CHEST).getHoverName().getString())
                .contains("Skeleton Master Chestplate");
    }

    private static void onChat(Component text, boolean overlay) {
        if (overlay || !ConfigManager.quiverHUD.getValue() || !ToolList.isInSkyblock()) {
            return;
        }

        String message = cleanText(text.getString());
        Matcher matcher;

        if ((matcher = SELECT_ARROW.matcher(message)).find()) {
            arrowType = normalizeType(cleanText(matcher.group(1)));
            needsScan = true;
        } else if ((matcher = ADDED_TO_QUIVER.matcher(message)).find()) {
            String type = normalizeType(cleanText(matcher.group(1)));
            if (arrowType.isEmpty() || type.contains(arrowType) || arrowType.contains(type)) {
                arrowType = type;
                arrowAmount += parseAmount(matcher.group(2));
                amountKnown = true;
            }
            needsScan = true;
        } else if ((matcher = RAN_OUT.matcher(message)).find()) {
            arrowType = normalizeType(cleanText(matcher.group(1)));
            arrowAmount = 0;
            amountKnown = true;
        } else if ((matcher = FILLED_QUIVER.matcher(message)).find()) {
            needsScan = true;
        } else if (message.contains("Cleared your quiver")
                || message.contains("Your quiver is now completely empty")
                || message.contains("Your favorite arrow has been reset")) {
            arrowType = "NONE";
            arrowAmount = 0;
            amountKnown = true;
        } else if (message.contains("quiver") || message.contains("arrow")) {
            needsScan = true;
        }
    }

    private static List<Component> getLoreComponents(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        return lore == null ? List.of() : lore.lines();
    }

    private static boolean hasQuiverArrowTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains("quiver_arrow");
    }

    private static int parseAmount(String value) {
        return Integer.parseInt(value.replace(",", ""));
    }

    private static String cleanText(String value) {
        return ChatFormatting.stripFormatting(value).trim();
    }

    private record ArrowIconInfo(String id, String model, boolean glowing, int color) {
    }

    private static void reset() {
        arrowType = "";
        arrowAmount = 0;
        arrowColor = 0xFFFFFFFF;
        amountKnown = false;
        arrowIcon = new ItemStack(Items.ARROW);
        wearingSkeletonMaster = false;
        holdingBow = false;
        hasBowInInventory = false;
        needsScan = false;
        tickCounter = 0;
    }
}
