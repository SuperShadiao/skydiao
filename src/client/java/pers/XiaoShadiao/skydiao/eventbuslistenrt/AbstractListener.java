package pers.XiaoShadiao.skydiao.eventbuslistenrt;

import java.util.List;

public abstract class AbstractListener extends Thread {

    public static List<AbstractListener> listeners;

    public static final BasicListener basicListener = new BasicListener();

    public AbstractListener() {
        setName("LT_" + getListenerName());
        start();
    }

    public static void initListeners() {
        listeners = List.of(
                basicListener
        );

        for (AbstractListener listener : listeners) {
            listener.registerListeners();
        }
    }

    public abstract String getListenerName();

    protected abstract void registerListeners();

    @Override
    public void run() {}

}
