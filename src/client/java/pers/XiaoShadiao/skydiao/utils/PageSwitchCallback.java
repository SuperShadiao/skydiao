package pers.XiaoShadiao.skydiao.utils;

import net.minecraft.client.gui.screens.Screen;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PageSwitchCallback {

    private Screen screen = null;

    private final CountDownLatch latch = new CountDownLatch(1);

    public void setScreen(Screen screen) {
        this.screen = screen;
        latch.countDown();
    }

    public Screen getScreen() {
        return getScreen(10000);
    }

    public Screen getScreen(long timeoutMs) {
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return screen;
    }

}