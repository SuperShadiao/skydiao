package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.ArrayList;
import java.util.List;

public class DelayTickExecutor extends AbstractListener {

    private final List<Entry> list = new ArrayList<>();

    @Override
    public String getListenerName() {
        return "DelayTickExecutor";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(_ -> {
            synchronized (list) {
                list.removeIf(entry -> {
                    if (entry.delayTick > 0) {
                        entry.delayTick--;
                        return false;
                    } else {
                        entry.runnable.run();
                        return true;
                    }
                });
            }
        });
    }

    public void delayExec(Runnable runnable, int delayTick) {
        synchronized (list) {
            list.add(new Entry(delayTick, runnable));
        }
    }

    public static class Entry {
        public int delayTick;
        public final Runnable runnable;
        public Entry(int delayTick, Runnable runnable) {
            this.delayTick = delayTick;
            this.runnable = runnable;
        }
    }

}
