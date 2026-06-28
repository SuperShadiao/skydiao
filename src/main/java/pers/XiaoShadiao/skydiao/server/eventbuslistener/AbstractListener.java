package pers.XiaoShadiao.skydiao.server.eventbuslistener;

import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.XiaoShadiao.skydiao.server.utils.Register;
import pers.XiaoShadiao.skydiao.server.utils.ToolList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractListener extends Thread {

    public static List<AbstractListener> listeners = new ArrayList<>();
    public final Logger logger = LogManager.getLogger(getListenerName());

    public static final WardenControllerListener wardenControllerListener = new WardenControllerListener();

    public AbstractListener() {
        setName("SLT_" + getListenerName());
        start();
    }

    public static void initListeners() {
        Register.execRegister(AbstractListener.class, AbstractListener.class, listener -> {
            listeners.add(listener);
            listener.registerListeners();
        });
        listeners = Collections.unmodifiableList(listeners);
    }

    public abstract String getListenerName();

    public abstract void registerListeners();

    @Override
    public void run() {}

    public static <T> T printThis(T t) {
        System.out.println(t);
        return t;
    }

    public String toString() {
        return "ServerListener " + getListenerName() + " | " + super.toString();
    }

}
