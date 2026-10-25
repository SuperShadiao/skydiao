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
import org.jetbrains.annotations.Nullable;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.PageSwitchCallback;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class AutoSwapReviveHeadListener extends AbstractListener {

    public enum Type {
        HEAD,
        REVIVE,
        ;
    }

    @Override
    public String getListenerName() {
        return "AutoSwapReviveHeadListener";
    }

    @Override
    public void registerListeners() {
        ScreenEvents.AFTER_INIT.register(this::onLoadoutGuiOpen);
        CustomFabricEvents.MOUSE_BUTTON_EVENT.register((hwnd, button, state) -> state == 1 && mc.screen != null && futureTask != null && !futureTask.future.isDone());
    }

    public Type execute = null;
    public Consumer<CallbackResult> callback;
    private PageSwitchCallback pageSwitchCallback;
    private int taskId;
    private ToolList.ThreadedTask<?> futureTask;

    public void onLoadoutGuiOpen(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof ContainerScreen containerScreen)) return;
        Container container = containerScreen.getMenu().getContainer();
        if (container instanceof SimpleContainer) {
            String chestName = ToolList.getInstance().deleteColorCode(containerScreen.getTitle().getString());
            if (chestName.contains("Stats & Equipment")) {
                if(pageSwitchCallback == null) {
                    ScreenEvents.afterExtract(screen).register(this::onStatsEquipmentGuiDraw);
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
        NOT_FOUND,
        EXCEPTION
    }

    public void onStatsEquipmentGuiDraw(Screen screen0, GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float tickDelta) {
        if (execute == null) return;

        if (!(screen0 instanceof ContainerScreen)) return;
        ChestMenu menu0 = ((ContainerScreen) screen0).getMenu();
        Container container0 = menu0.getContainer();
        if (!(container0 instanceof SimpleContainer)) return;
        Consumer<CallbackResult> callback = this.callback;
        this.callback = null;

        Type currentType = execute;
        execute = null;

        futureTask = ToolList.addThreadedTask(new Callable<Void>() {
            public Void call() {
                Screen screen;
                ChestMenu menu = menu0;
                try {
                    Thread.sleep(200 + ToolList.getInstance().random.nextInt(200));
                    if(mc.screen == null) {
                        call(CallbackResult.TERMINATED);
                        return null;
                    }
                    ChestMenu finalMenu1 = menu;
                    boolean found = false;

                    Slot bonzoMask = null;
                    Slot spiritMask = null;
                    for (Slot slot : menu.slots) {
                        if(slot.container == mc.player.getInventory()) {
                            String id = ToolList.getInstance().tryGetSkyblockItemId(slot.getItem());
                            if(currentType == Type.HEAD) {
                                if(((id.endsWith("_HEAD") && (id.startsWith("GOLD_") || id.startsWith("DIAMOND_"))))) {
                                    mc.execute(() -> {
                                        if (mc.player != null && mc.gameMode != null) {
                                            mc.gameMode.handleContainerInput(finalMenu1.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
                                        }
                                    });
                                    found = true;
                                    break;
                                }
                            } else {
                                if(id.endsWith("SPIRIT_MASK")) {
                                    spiritMask = slot;
                                } else if(id.endsWith("BONZO_MASK")) {
                                    bonzoMask = slot;
                                }
                            }
                        }
                    }

                    if(currentType == Type.REVIVE) {
                        Slot realSlot = getBonzoOrSpiritMask(bonzoMask, spiritMask);
                        if(realSlot != null) {
                            found = true;
                            mc.execute(() -> {
                                if (mc.player != null && mc.gameMode != null) {
                                    mc.gameMode.handleContainerInput(finalMenu1.containerId, realSlot.index, 0, ContainerInput.PICKUP, mc.player);
                                }
                            });
                        }
                    }

                    Thread.sleep(100 + ToolList.getInstance().random.nextInt(50));
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

            private @Nullable Slot getBonzoOrSpiritMask(Slot bonzoMask, Slot spiritMask) {
                Slot realSlot = null;
                if(bonzoMask != null && spiritMask != null) {
                    if(resurrectionItemListener.getBonzoCD() < resurrectionItemListener.getSpiritCD()) {
                        realSlot = bonzoMask;
                    } else {
                        realSlot = spiritMask;
                    }
                } else if(bonzoMask != null) {
                    realSlot = bonzoMask;
                } else if(spiritMask != null) {
                    realSlot = spiritMask;
                }
                return realSlot;
            }

            public boolean called = false;

            private void call(CallbackResult result) {
                if(called) return;
                logger.info("Swap result " + result);
                if(callback == null) return;
                called = true;
                callback.accept(result);
            }
        });
    }

    public void switchReviveHead(Type type, Consumer<CallbackResult> callback) {
        // 终止前一个未完成的任务
        if (this.callback != null) {
            this.callback.accept(CallbackResult.TERMINATED);
        }

        this.execute = type;
        this.callback = callback;

        int expectedTaskId = this.taskId = ToolList.getInstance().random.nextInt();

        ToolList.addThreadedTask(() -> {
            Thread.sleep(7000);

            // 检查是否仍未处理
            if (AutoSwapReviveHeadListener.this.taskId == expectedTaskId) {
                // 使用 call 方法确保线程安全和防止重复调用
                Consumer<CallbackResult> cb = AutoSwapReviveHeadListener.this.callback;
                if (cb != null) {
                    cb.accept(CallbackResult.TERMINATED);  // 超时应返回 TERMINATED
                }
                AutoSwapReviveHeadListener.this.execute = null;
                AutoSwapReviveHeadListener.this.callback = null;
            }
            return null;
        });

        ToolList.sendChatMessage("/stats");
    }

}