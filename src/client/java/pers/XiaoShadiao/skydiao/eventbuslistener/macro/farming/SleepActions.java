package pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming;


import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SleepActions {
    public static boolean actionDoing = false;
    private static long antiMarcoTime;

    public static boolean antiMarco() {
        return System.currentTimeMillis() - antiMarcoTime < 2000;
    }

    @FunctionalInterface
    public interface funcWithoutCtx {
        void run();
    }

    @FunctionalInterface
    public interface funcWithCtx {
        void run(Map<String, Object> ctx);
    }

    private static class Action {
        private final funcWithCtx rw;
        private final funcWithoutCtx ro;
        private final int sleepMs;
        private final boolean antiMarco;

        public Action(funcWithCtx r, int sleepMs, boolean antiMarco) {
            this.rw = r;
            this.ro = null;
            this.sleepMs = sleepMs;
            this.antiMarco = antiMarco;
        }

        public Action(funcWithoutCtx r, int sleepMs, boolean antiMarco) {
            this.rw = null;
            this.ro = r;
            this.sleepMs = sleepMs;
            this.antiMarco = antiMarco;
        }

        public Action(int sleepMs) {
            this.rw = null;
            this.ro = null;
            this.sleepMs = sleepMs;
            this.antiMarco = false;
        }
    }

    private SleepActions() {
    }

    private final ArrayList<Action> actions = new ArrayList<>();
    private final Map<String, Object> context = new HashMap<>();

    public void run() {
        actionDoing = true;
        ToolList.addThreadedTask(() -> {
            for (Action action : actions) {
                if (action.antiMarco) antiMarcoTime = System.currentTimeMillis();
                if (action.ro != null) action.ro.run();
                if (action.rw != null) action.rw.run(context);
                int randSleep = ToolList.getInstance().random.nextInt(action.sleepMs / 4) + action.sleepMs;
                if (randSleep > 0) Thread.sleep(randSleep);
            }
            actionDoing = false;
            return null;
        });
    }

    public static SleepActions builder() {
        return new SleepActions();
    }

    public SleepActions addAction(funcWithoutCtx r, int sleepMs, boolean antiMarco) {
        actions.add(new Action(r, sleepMs, antiMarco));
        return this;
    }

    public SleepActions addAction(funcWithCtx r, int sleepMs, boolean antiMarco) {
        actions.add(new Action(r, sleepMs, antiMarco));
        return this;
    }

    public SleepActions addAction(funcWithoutCtx r, int sleepMs) {
        return addAction(r, sleepMs, false);
    }

    public SleepActions addAction(funcWithCtx r, int sleepMs) {
        return addAction(r, sleepMs, false);
    }

    public SleepActions addAction(funcWithoutCtx r) {
        return addAction(r, 0);
    }

    public SleepActions addAction(funcWithCtx r) {
        return addAction(r, 0);
    }

    public SleepActions addSleep(int sleepMs) {
        actions.add(new Action(sleepMs));
        return this;
    }
}