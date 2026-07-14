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
        try {
            latch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return screen;
    }

}