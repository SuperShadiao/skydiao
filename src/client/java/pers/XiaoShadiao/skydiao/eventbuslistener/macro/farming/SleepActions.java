package pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming;


import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SleepActions {
    public static boolean actionDoing = false;

    @FunctionalInterface
    public interface funcWithoutCtx {
        void run();
    }

    @FunctionalInterface
    public interface funcWithCtx {
        void run(Map<String, Object> ctx);
    }

    private static class Action {
        public funcWithCtx rw;
        public funcWithoutCtx ro;
        public int sleepMs;

        public Action(funcWithCtx r, int sleepMs) {
            this.rw = r;
            this.sleepMs = sleepMs;
        }

        public Action(funcWithoutCtx r, int sleepMs) {
            this.ro = r;
            this.sleepMs = sleepMs;
        }

        public Action(int sleepMs) {
            this.sleepMs = sleepMs;
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
                if (action.ro != null) action.ro.run();
                if (action.rw != null) action.rw.run(context);
                int randSleep = ToolList.getInstance().random.nextInt(action.sleepMs / 4);
                if (action.sleepMs + randSleep > 0) Thread.sleep(action.sleepMs + randSleep);
            }
            actionDoing = false;
            return null;
        });
    }

    public static SleepActions builder() {
        return new SleepActions();
    }

    public SleepActions addAction(funcWithoutCtx r, int sleepMs) {
        actions.add(new Action(r, sleepMs));
        return this;
    }

    public SleepActions addAction(funcWithCtx r, int sleepMs) {
        actions.add(new Action(r, sleepMs));
        return this;
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