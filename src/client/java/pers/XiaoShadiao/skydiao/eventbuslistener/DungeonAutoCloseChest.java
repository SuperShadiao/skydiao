package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;

public class DungeonAutoCloseChest extends AbstractListener implements IDungeonListener {

    private BlockPos lastClickedChestPos = BlockPos.ZERO;

    private static final List<String> notValuedItems = List.of(
            "Spirit Leap",
            "Decoy",
            "Trap",
            "Training Weights",
            "Inflatable Jerry",
            "Healing VIII Splash Potion",
            "Defuse Kit",
            "Superboom TNT"
    );

    @Override
    public String getListenerName() {
        return "DungeonAutoCloseChest";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
        CustomFabricEvents.MOUSE_BUTTON_EVENT.register(this::onMouseButton);
    }

    private boolean onMouseButton(long window, MouseButtonInfo mouseButtonInfo, int state) {
        if(ConfigManager.dungeonAutoCloseChest.getValue() && mc.level != null && mc.hitResult instanceof BlockHitResult blockHitResult && blockHitResult.getType() == HitResult.Type.BLOCK) {
            if(mc.level.getBlockState(blockHitResult.getBlockPos()).getBlock() instanceof ChestBlock) {
                lastClickedChestPos = blockHitResult.getBlockPos();
            }
        }
        return false;
    }

    private void onStartClientTick(Minecraft mc) {
        if(mc.level == null || mc.player == null || !ConfigManager.dungeonAutoCloseChest.getValue() || !StatusManager.get().isInDungeon()) return;

        if(ToolList.getInstance().isDevEnvironment() && mc.player.getMainHandItem().isEmpty()) return;

        if(mc.screen instanceof ContainerScreen containerScreen) {
//            System.out.println(containerScreen.getTitle().toString() + " & " + containerScreen.getTitle().getClass());
//            System.out.println(containerScreen.getTitle().getContents().getClass());
//            containerScreen.getTitle().getContents().visit(content -> {
//                System.out.println(content);
//                System.out.println(content.getClass());
//                return Optional.empty();
//            });
            if (mc.player.distanceToSqr(lastClickedChestPos.getX(), lastClickedChestPos.getY(), lastClickedChestPos.getZ()) > 15 * 15) return;
            if (containerScreen.getTitle().getContents() instanceof TranslatableContents translatableContents) {
                if(translatableContents.getKey().equals("container.chest") || translatableContents.getKey().equals("container.chestDouble")) {
                    List<Slot> nonEmptySlots = containerScreen.getMenu().slots.stream().filter(slot -> slot.hasItem() && slot.container == containerScreen.getMenu().getContainer()).toList();
                    if (nonEmptySlots.size() == 1 || nonEmptySlots.size() == 2) {
                        if (nonEmptySlots.stream().allMatch(slot -> notValuedItems.contains(ToolList.getInstance().deleteColorCode(slot.getItem().getHoverName().getString())))) {
                            mc.screen.onClose();
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getFloor() {
        return 7;
    }

}
