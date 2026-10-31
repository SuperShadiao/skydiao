package pers.XiaoShadiao.skydiao.utils;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.CustomData;
import pers.XiaoShadiao.skydiao.config.ConfigManager;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockAnimation {

    private static final Set<String> SKYBLOCK_SWORD_IDS = Set.of(
            "HYPERION", "VALKYRIE", "ASTRAEA", "SCYLLA",
            "NECRON_BLADE", "REAPER_SCYTHE", "SHADOW_FURY", "LIVID_DAGGER",
            "GIANTS_SWORD", "MIDAS_SWORD", "FLORID_ZOMBIE_SWORD",
            "SILK_EDGE_SWORD", "POOCH_SWORD", "AXE_OF_THE_SHREDDED",
            "EMBER_ROD", "PRISMARINE_BLADE", "SWORD_OF_REVELATIONS",
            "VOIDEDGE_KATANA", "VORPAL_KATANA", "ATOMSPLIT_KATANA",
            "ASPECT_OF_THE_END", "ASPECT_OF_THE_DRAGON", "ROGUE_SWORD",
            "UNDEAD_SWORD", "CLEAVER", "FEL_SWORD", "ZOMBIE_SWORD",
            "ORNATE_ZOMBIE_SWORD", "CUTLASS", "SPIDER_SWORD", "HUNTER_KNIFE",
            "ZOMBIE_COMMANDER_WHIP");

    private static final ConcurrentHashMap<String, Boolean> ID_CACHE = new ConcurrentHashMap<>();

    private BlockAnimation() {
    }

    public static boolean active() {
        return ConfigManager.blockAnimation.getValue();
    }

    public static boolean rightClicking() {
        return ToolList.mc.options.keyUse.isDown();
    }

    public static float itemSize() {
        return Math.clamp(ConfigManager.blockAnimationItemSize.getValue() / 100.0F, 0.3F, 3.0F);
    }

    public static boolean isSword(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(ItemTags.SWORDS)) return true;
        String id = skyblockId(stack);
        if (id.isEmpty()) return false;
        return ID_CACHE.computeIfAbsent(id,
                value -> SKYBLOCK_SWORD_IDS.contains(value) || value.contains("SWORD") || value.contains("KATANA"));
    }

    public static InteractionHand blockingHand(Player player) {
        if (!active() || player == null) return null;
        if (isSword(player.getMainHandItem()) && canBlock(player.getOffhandItem())) return InteractionHand.MAIN_HAND;
        if (isSword(player.getOffhandItem()) && canBlock(player.getMainHandItem())) return InteractionHand.OFF_HAND;
        return null;
    }

    public static boolean blocking(Player player) {
        return rightClicking() && blockingHand(player) != null;
    }

    public static HumanoidArm blockingArm(Player player, HumanoidArm mainArm) {
        InteractionHand hand = blockingHand(player);
        if (hand == null) return mainArm;
        return hand == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite();
    }

    public static boolean usingConsumable(Player player) {
        if (player == null || !player.isUsingItem()) return false;
        ItemStack stack = player.getUseItem();
        if (stack.isEmpty()) return false;
        ItemUseAnimation animation = stack.getUseAnimation();
        return animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK;
    }

    public static boolean usingBow(Player player) {
        if (player == null || !player.isUsingItem()) return false;
        ItemStack stack = player.getUseItem();
        if (stack.isEmpty()) return false;
        ItemUseAnimation animation = stack.getUseAnimation();
        return animation == ItemUseAnimation.BOW || animation == ItemUseAnimation.CROSSBOW;
    }

    private static String skyblockId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        CompoundTag tag = data.copyTag();
        return tag.getStringOr("id", "");
    }

    private static boolean canBlock(ItemStack other) {
        if (other.isEmpty()) return true;
        ItemUseAnimation animation = other.getUseAnimation();
        return animation != ItemUseAnimation.BLOCK
                && animation != ItemUseAnimation.SPYGLASS
                && animation != ItemUseAnimation.BRUSH;
    }
}