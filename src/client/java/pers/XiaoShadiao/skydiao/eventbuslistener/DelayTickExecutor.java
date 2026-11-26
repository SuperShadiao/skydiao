package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.ArrayList;
import java.util.Iterator;
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
            List<Runnable> pending = List.of();
            synchronized (list) {
                Iterator<Entry> it = list.iterator();
                while(it.hasNext()) {
                    Entry entry = it.next();
                    if(entry.delayTick > 0) {
                        entry.delayTick--;
                    } else {
                        if(pending.isEmpty()) {
                            pending = new ArrayList<>();
                        }
                        pending.add(entry.runnable);
                        it.remove();
                    }
                }
            }
            Throwable throwable = null;
            for (Runnable runnable : pending) {
                try {
                    if(throwable != null) delayExec(runnable, 0); else runnable.run();
                } catch (Throwable e) {
                    throwable = e;
                }
            }
            if(throwable != null) throw new RuntimeException("Exception in delayed tick", throwable);
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
