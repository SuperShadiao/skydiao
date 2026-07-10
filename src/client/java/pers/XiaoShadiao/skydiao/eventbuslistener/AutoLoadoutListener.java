package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.apache.commons.lang3.mutable.MutableBoolean;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class AutoLoadoutListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "AutoLoadoutListener";
    }

    @Override
    public void registerListeners() {
        ScreenEvents.AFTER_INIT.register(this::onLoadoutGuiOpen);
    }

    public int loadout = 0;
    public Consumer<CallbackResult> callback;
    private PageSwitchCallback pageSwitchCallback;

    public void onLoadoutGuiOpen(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof ContainerScreen containerScreen)) return;
        Container container = containerScreen.getMenu().getContainer();
        if (container instanceof SimpleContainer) {
            String chestName = ToolList.getInstance().deleteColorCode(containerScreen.getTitle().getString());
            if (chestName.contains("Loadouts")) {
                if(pageSwitchCallback == null) {
                    ScreenEvents.afterExtract(screen).register(this::onLoadoutGuiDraw);
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
        EXCEPTION
    }

    public void onLoadoutGuiDraw(Screen screen0, GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float tickDelta) {
        if (loadout == 0) return;

        if (!(screen0 instanceof ContainerScreen)) return;
        ChestMenu menu0 = ((ContainerScreen) screen0).getMenu();
        Container container0 = menu0.getContainer();
        if (!(container0 instanceof SimpleContainer)) return;

        int page = (loadout - 1) / 12;

        // index table
        // 14 15 16 lastp
        // 23 24 25
        // 32 33 34
        // 41 42 43 nextp
        
        int slot = switch(loadout) {
            case 1, 13, 25 -> 14;
            case 2, 14, 26 -> 15;
            case 3, 15, 27 -> 16;
            case 4, 16 -> 23;
            case 5, 17 -> 24;
            case 6, 18 -> 25;
            case 7, 19 -> 32;
            case 8, 20 -> 33;
            case 9, 21 -> 34;
            case 10, 22 -> 41;
            case 11, 23 -> 42;
            case 12, 24 -> 43;
            default -> throw new IllegalStateException("Unexpected value: " + loadout);
        };
        Consumer<CallbackResult> callback = this.callback;
        this.callback = null;

        loadout = 0;

        ToolList.addThreadedTask(new Callable<Void>() {
            public Void call() {
                Screen screen;
                ChestMenu menu = menu0;
                try {
                    Thread.sleep(400 + ToolList.getInstance().random.nextInt(200));
                    PageSwitchCallback pageSwitchCallback = page > 0 ? new PageSwitchCallback() : null;
                    for (int i = 0; i < page; i++) {
                        ChestMenu finalMenu = menu;
                        mc.execute(() -> {
                            if (mc.player != null && mc.gameMode != null) {
                                AutoLoadoutListener.this.pageSwitchCallback = pageSwitchCallback;
                                mc.gameMode.handleContainerInput(finalMenu.containerId, 44, 0, ContainerInput.PICKUP, mc.player);
                            }
                        });
                        if(mc.screen == null) {
                            call(CallbackResult.TERMINATED);
                            return null;
                        }
                        Thread.sleep(600 + ToolList.getInstance().random.nextInt(200));
                        screen = pageSwitchCallback.getScreen();
                        if(screen == null) {
                            call(CallbackResult.TERMINATED);
                            return null;
                        }
                        menu = ((ContainerScreen) screen).getMenu();
                    }
                    if(mc.screen == null) {
                        call(CallbackResult.TERMINATED);
                        return null;
                    }
                    ChestMenu finalMenu1 = menu;
                    mc.execute(() -> {
                        if (mc.player != null && mc.gameMode != null) {
                            mc.gameMode.handleContainerInput(finalMenu1.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
                        }
                    });
                    Thread.sleep(500 + ToolList.getInstance().random.nextInt(150));
                    mc.execute(() -> {
                        if (mc.screen != null) mc.screen.onClose();
                        call(CallbackResult.DONE);
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
                logger.info("Loadout returned " + result);
                if(callback == null) return;
                called = true;
                callback.accept(result);
            }
        });
    }

    public void switchLoadout(int index, Consumer<CallbackResult> callback) {
        // 终止前一个未完成的任务
        if (this.callback != null) {
            this.callback.accept(CallbackResult.TERMINATED);
        }

        this.loadout = index;
        this.callback = callback;

        // 使用原子操作确保线程安全
        final int expectedIndex = index;

        ToolList.addThreadedTask(() -> {
            Thread.sleep(7000);

            // 检查是否仍未处理
            if (AutoLoadoutListener.this.loadout == expectedIndex) {
                // 使用 call 方法确保线程安全和防止重复调用
                Consumer<CallbackResult> cb = AutoLoadoutListener.this.callback;
                if (cb != null) {
                    cb.accept(CallbackResult.TERMINATED);  // 超时应返回 TERMINATED
                }
                AutoLoadoutListener.this.loadout = 0;
                AutoLoadoutListener.this.callback = null;
            }
            return null;
        });

        ToolList.sendChatMessage("/loadout");
    }

    private class PageSwitchCallback {

        private Screen screen = null;

        private final CountDownLatch latch = new CountDownLatch(1);

        public void setScreen(Screen screen) {
            this.screen = screen;
            latch.countDown();
        }

        public Screen getScreen() {
            try {
                latch.await(10000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return screen;
        }

    }

}