package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import pers.XiaoShadiao.skydiao.utils.PageSwitchCallback;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class AutoSwitchPetListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "AutoSwitchPetListener";
    }

    @Override
    public void registerListeners() {
        ScreenEvents.AFTER_INIT.register(this::onLoadoutGuiOpen);
    }

    public String petName;
    public Consumer<CallbackResult> callback;
    private PageSwitchCallback pageSwitchCallback;

    public void onLoadoutGuiOpen(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof ContainerScreen containerScreen)) return;
        Container container = containerScreen.getMenu().getContainer();
        if (container instanceof SimpleContainer) {
            String chestName = ToolList.getInstance().deleteColorCode(containerScreen.getTitle().getString());
            if (chestName.endsWith("Pets")) {
                if(pageSwitchCallback == null) {
                    ScreenEvents.afterExtract(screen).register(this::onPetGuiDraw);
                } else {
                    pageSwitchCallback.setScreen(screen);
                    pageSwitchCallback = null;
                }
            }
        }
    }

    public enum CallbackResult {
        DONE,
        TERMINATED,
        EXCEPTION,
        NOT_FOUND;
    }

    public void onPetGuiDraw(Screen screen0, GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float tickDelta) {
        if (petName == null) return;

        if (!(screen0 instanceof ContainerScreen)) return;
        ChestMenu menu0 = ((ContainerScreen) screen0).getMenu();
        Container container0 = menu0.getContainer();
        if (!(container0 instanceof SimpleContainer)) return;

        Consumer<CallbackResult> callback = this.callback;
        this.callback = null;

        String petName0 = petName;
        petName = null;

        ToolList.addThreadedTask(new Callable<Void>() {
            public Void call() {
                ChestMenu menu = menu0;
                try {
                    Thread.sleep(200 + ToolList.getInstance().random.nextInt(200));
                    if(mc.screen == null) {
                        call(CallbackResult.TERMINATED);
                        return null;
                    }
                    ChestMenu finalMenu1 = menu;
                    boolean found = false;
                    for (int i = 0; i < finalMenu1.slots.size(); i++) {
                        Slot slot = finalMenu1.slots.get(i);
                        if(ToolList.getInstance().deleteColorCode(slot.getItem().getHoverName().getString()).toLowerCase().contains(petName0.toLowerCase())) {
                            found = true;
                            mc.execute(() -> {
                                if (mc.player != null && mc.gameMode != null) {
                                    mc.gameMode.handleContainerInput(finalMenu1.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
                                }
                            });
                            break;
                        }
                    }
                    Thread.sleep(500 + ToolList.getInstance().random.nextInt(150));
                    CallbackResult result = found ? CallbackResult.DONE : CallbackResult.NOT_FOUND;
                    mc.execute(() -> {
                        if (mc.screen != null) mc.screen.onClose();
                        call(result);
                    });
                } catch (Throwable e) {
                    e.printStackTrace();
                    mc.execute(() -> call(CallbackResult.EXCEPTION));
                }
                return null;
            }

            public boolean called = false;

            private void call(CallbackResult result) {
                if(called) return;
                logger.info("SwitchPet returned " + result);
                if(callback == null) return;
                called = true;
                callback.accept(result);
            }
        });
    }

    public void switchPet(String petName, Consumer<CallbackResult> callback) {
        // 终止前一个未完成的任务
        if (this.callback != null) {
            this.callback.accept(CallbackResult.TERMINATED);
        }

        this.petName = petName;
        this.callback = callback;

        // 使用原子操作确保线程安全
        final String expectedPetName = petName;

        ToolList.addThreadedTask(() -> {
            Thread.sleep(7000);

            // 检查是否仍未处理
            if (AutoSwitchPetListener.this.petName.equals(expectedPetName)) {
                // 使用 call 方法确保线程安全和防止重复调用
                Consumer<CallbackResult> cb = AutoSwitchPetListener.this.callback;
                if (cb != null) {
                    cb.accept(CallbackResult.TERMINATED);  // 超时应返回 TERMINATED
                }
                AutoSwitchPetListener.this.petName = null;
                AutoSwitchPetListener.this.callback = null;
            }
            return null;
        });

        ToolList.sendChatMessage("/pet");
    }

}