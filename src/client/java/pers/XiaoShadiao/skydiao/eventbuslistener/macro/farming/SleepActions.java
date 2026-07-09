package pers.XiaoShadiao.skydiao.eventbuslistener.macro.farming;


import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SleepActions {

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

    private ActionsWithSleep() {
    }

    private ArrayList<Action> actions = new ArrayList<>();
    private final Map<String, Object> context = new HashMap<>();

    public void run() {
        ToolList.addThreadedTask(() -> {
            for (Action action : actions) {
                if (action.ro != null) action.ro.run();
                if (action.rw != null) action.rw.run(context);
                int randSleep = ToolList.getInstance().random.nextInt(action.sleepMs / 2);
                if (action.sleepMs + randSleep > 0) Thread.sleep(action.sleepMs + randSleep);
            }
            return null;
        });
    }

    public static ActionsWithSleep builder() {
        return new ActionsWithSleep();
    }

    public ActionsWithSleep addAction(funcWithoutCtx r, int sleepMs) {
        actions.add(new Action(r, sleepMs));
        return this;
    }

    public ActionsWithSleep addAction(funcWithCtx r, int sleepMs) {
        actions.add(new Action(r, sleepMs));
        return this;
    }

    public ActionsWithSleep addSleep(int sleepMs) {
        actions.add(new Action(sleepMs));
        return this;
    }
}