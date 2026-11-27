package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.AimHelper;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoCombineEnchantbookListener extends AbstractListener {

    private ToolList.ThreadedTask<?> task;

    @Override
    public String getListenerName() {
        return "AutoCombineEnchantbookListener";
    }

    @Override
    public void registerListeners() {

    }

    public void startCombine() {
        if (task != null && !task.future.isDone()) {
            return;
        }
        task = ToolList.addThreadedTask(() -> {

            BlockPos avPos0 = null;
            BlockPos blockPos = mc.player.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(blockPos.offset(2, 2, 2), blockPos.offset(-2, 0, -2))) {
                if(mc.level.getBlockState(pos).is(Blocks.ANVIL)) {
                    avPos0 = pos;
                    break;
                }
            }

            final BlockPos avPos = avPos0;
            Runnable openAv = () -> {
                if (avPos == null) {
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §e未检测到附近有铁砧, 尝试使用指令打开铁砧"));
                    ToolList.sendChatMessage("/av");
                } else {
                    while (!(mc.hitResult instanceof BlockHitResult blockHitResult && blockHitResult.getBlockPos().equals(avPos))) {
                        AimHelper.getYawPitchByBlockPos(avPos).updateToAimHelper(new AimHelper(1));
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {

                        }
                    }
                    InputSimulator.singleRightClick();
                }
            };
            openAv.run();

            Screen screen = syncScreenListener.createScreenListener().getScreen();
            if(!(screen instanceof ContainerScreen containerScreen)) return null;
            ChestMenu menu = containerScreen.getMenu();
            Thread.sleep(400);

            List<Object2IntMap.Entry<String>> blacklistEnchantments = new ArrayList<>();

            boolean tryUsePickupStash = true;
            a:while(true) {
                Map<Object2IntMap.Entry<String>, List<Slot>> enchantmentToSlots = new HashMap<>();

                for (Slot slot : menu.slots) {
                    ItemStack itemStack = slot.getItem();
                    if(!ToolList.getInstance().tryGetSkyblockItemId(itemStack).equals("ENCHANTED_BOOK")) continue;

                    Object2IntMap<String> enchant = ToolList.getInstance().tryGetSkyblockItemEnchantments(itemStack);
                    if(enchant.isEmpty()) continue;

                    Object2IntMap.Entry<String> enchantAndLevel = enchant.object2IntEntrySet().iterator().next();
                    enchantmentToSlots.computeIfAbsent(enchantAndLevel, k -> new ArrayList<>()).add(slot);
                }

                enchantmentToSlots.entrySet().removeIf(entry -> blacklistEnchantments.contains(entry.getKey()) || entry.getValue().size() < 2);

                if(enchantmentToSlots.isEmpty()) {
                    if(tryUsePickupStash) {
                        tryUsePickupStash = false;
                        mc.execute(screen::onClose);
                        Thread.sleep(300);
                        ToolList.sendChatMessage("/pickupstash");
                        Thread.sleep(100);
                        openAv.run();

                        screen = syncScreenListener.createScreenListener().getScreen();
                        if(!(screen instanceof ContainerScreen containerScreen2)) break;
                        menu = containerScreen2.getMenu();
                        Thread.sleep(400);
                        continue;
                    } else {
                        break;
                    }
                }

                if(mc.gui.screen() != screen) break;

                Map.Entry<Object2IntMap.Entry<String>, List<Slot>> readyToCombine = enchantmentToSlots.entrySet().iterator().next();

                Slot click1 = readyToCombine.getValue().get(0);
                Slot click2 = readyToCombine.getValue().get(1);

                ChestMenu finalMenu = menu;
                mc.execute(() -> mc.gameMode.handleContainerInput(finalMenu.containerId, click1.index, 0, ContainerInput.QUICK_MOVE, mc.player));
                Thread.sleep(500);
                mc.execute(() -> mc.gameMode.handleContainerInput(finalMenu.containerId, click2.index, 0, ContainerInput.QUICK_MOVE, mc.player));
                ItemStack product;
                while((product = menu.slots.get(13).getItem()).is(Items.BARRIER)) {
                    if(!(mc.gui.screen() instanceof ContainerScreen)) break;
                    if(ToolList.getInstance().deleteColorCode(product.getHoverName().getString()).equals("Error!")) {
                        blacklistEnchantments.add(readyToCombine.getKey());
                        mc.execute(screen::onClose);
                        Thread.sleep(100);

                        openAv.run();

                        screen = syncScreenListener.createScreenListener().getScreen();
                        if(!(screen instanceof ContainerScreen containerScreen2)) break a;
                        menu = containerScreen2.getMenu();
                        Thread.sleep(400);
                        continue a;
                    }
                }
                // logger.info("Flag Item: " + product + ", Hover Name: " + ToolList.getInstance().deleteColorCode(product.getHoverName().getString()));

                tryUsePickupStash = true;
                Thread.sleep(500);
                mc.execute(() -> mc.gameMode.handleContainerInput(finalMenu.containerId, 22, 0, ContainerInput.PICKUP, mc.player));
                Thread.sleep(500);
                mc.execute(() -> mc.gameMode.handleContainerInput(finalMenu.containerId, 22, 0, ContainerInput.PICKUP, mc.player));
                Thread.sleep(500);
            }

            ToolList.printChatMessage(Component.literal("§a[小沙雕] 已完成本轮附魔书合成"));
            mc.gui.screen().onClose();
            return null;
        });
    }

}
