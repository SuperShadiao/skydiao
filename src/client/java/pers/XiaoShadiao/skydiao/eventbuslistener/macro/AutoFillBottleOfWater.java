package pers.XiaoShadiao.skydiao.eventbuslistener.macro;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.AimHelper;
import pers.XiaoShadiao.skydiao.utils.playerinput.InputSimulator;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.stream.IntStream;

public class AutoFillBottleOfWater extends AbstractListener implements IMacro {

    public boolean enabled = false;

    public BlockPos waterPos = null;
    public BlockPos chestPos = null;
    private ToolList.ThreadedTask<Object> task;

    @Override
    public String getListenerName() {
        return "AutoFillBottleOfWater";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(mc.player == null || mc.level == null || !"dynamic".equals(StatusManager.get().getMode())) {
            return;
        }

        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        if (waterPos != null) RenderUtils.renderESP(wr, waterPos, 0, 1, 1, 1, false);
        if (chestPos != null) RenderUtils.renderESP(wr, chestPos, 1, 1, 0, 1, false);
        wr.finishDraw();
    }

    private void onTick(Minecraft mc) {
        if(mc.player == null || mc.level == null) {
            enabled = false;
            return;
        }

        if(!enabled) return;
        if(task != null && !task.future.isDone()) return;

        if(waterPos == null) {
            enabled = false;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c未设置水的坐标, 请使用§e/skydiao autofillbottle water <x> <y> <z>§c设置!"));
            return;
        }

        if(chestPos == null) {
            enabled = false;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c未设置欲填充的箱子的坐标, 请使用§e/skydiao autofillbottle chest <x> <y> <z>§c设置!"));
            return;
        }

        int bottleIndex = IntStream.range(0, 36).filter(i -> mc.player.getInventory().getItem(i).getItem() == Items.GLASS_BOTTLE).findFirst().orElse(-1);
        if(bottleIndex == -1) {
            enabled = false;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c物品栏没有空瓶!"));
            return;
        }

        if(bottleIndex >= 9) {
            enabled = false;
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c将背包的空瓶移到物品栏来继续!"));
            return;
        }

        task = ToolList.addThreadedTask(() -> {
            try {
                activeThisMacro();
                InputSimulator.switchItem(bottleIndex);
                AimHelper aimHelper = new AimHelper();
                int i = 0;
                while (i < 50) {
                    AimHelper.getYawPitchByBlockPos(waterPos).updateToAimHelper(aimHelper);
                    i++;
                    Thread.sleep(10);
                }

                while(mc.player.getInventory().getItem(bottleIndex).getItem() == Items.GLASS_BOTTLE && IntStream.range(0, 36).filter(j -> mc.player.getInventory().getItem(j).isEmpty()).count() > 3) {
                    InputSimulator.pressRightClick();
                    Thread.sleep(10);
                }
                InputSimulator.releaseRightClick();

                i = 0;
                while (i < 50) {
                    AimHelper.getYawPitchByBlockPos(chestPos).updateToAimHelper(aimHelper);
                    i++;
                    Thread.sleep(10);
                }
                InputSimulator.singleRightClick();

                Thread.sleep(2000);

                if(!(mc.screen instanceof ContainerScreen containerScreen)) {
                    enabled = false;
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §c箱子打开超时, 请检查你的位置..."));
                    return null;
                }

                for (Slot slot : containerScreen.getMenu().slots) {
                    if(slot.container == mc.player.getInventory() && slot.getItem().getItem() == Items.POTION) {
                        mc.execute(() -> {
                            if (mc.screen == containerScreen && mc.player != null && mc.gameMode != null) {
                                mc.gameMode.handleContainerInput(containerScreen.getMenu().containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
                            }
                        });
                        Thread.sleep(260 + ToolList.getInstance().random.nextInt(100));
                    }
                }
                if(mc.screen == containerScreen) mc.execute(() -> mc.screen.onClose()); else {
                    enabled = false;
                    ToolList.printChatMessage(Component.literal("§a[小沙雕] §c箱子似乎被手动关闭了..."));
                    return null;
                }
            } catch (Exception e) {
                logger.catching(e);
            } finally {
                InputSimulator.releaseAllKey();
            }

            return null;
        });

    }

    @Override
    public boolean isMacroActive() {
        return enabled;
    }

    @Override
    public boolean onMacroCheck(PositionInfo beforeTP, PositionInfo afterTP) {
        return false;
    }

    @Override
    public boolean onMacroCheck(int beforeSlot, int afterSlot) {
        return false;
    }

    @Override
    public String getMacroName() {
        return "Auto Fill Bottle Of Water";
    }

}
