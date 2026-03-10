package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.XiaoShadiao.skydiao.eventbuslistener.bossbar.DungeonF7BossbarListener;
import pers.XiaoShadiao.skydiao.utils.Register;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractListener extends Thread {

    public static final Minecraft mc = ToolList.mc;
    public static List<AbstractListener> listeners = new ArrayList<>();
    public final Logger logger = LogManager.getLogger(getListenerName());

    public static final BasicListener basicListener = new BasicListener();
    public static final MineshaftShareListener mineshaftShareListener = new MineshaftShareListener();
    public static final DungeonMobESPListener dungeonMobESPListener = new DungeonMobESPListener();
    public static final E2AMappingListener e2AMappingListener = new E2AMappingListener();
    public static final PrivateIslandProtectorListener privateIslandProtectorListener = new PrivateIslandProtectorListener();
    public static final DungeonTrapRenderListener dungeonTrapRenderListener = new DungeonTrapRenderListener();
    public static final AutoEnchantmentTableGameListener autoEnchantmentTableGameListener = new AutoEnchantmentTableGameListener();

    public static final DungeonF7BossbarListener dungeonF7Bossbar = new DungeonF7BossbarListener();

    public AbstractListener() {
        setName("LT_" + getListenerName());
        start();
    }

    public static void initListeners() {
//        listeners = List.of(
//                basicListener,
//                mineshaftShareListener
//        );
//
//        for (AbstractListener listener : listeners) {
//            listener.registerListeners();
//        }
        Register.execRegister(AbstractListener.class, AbstractListener.class, listener -> {
            listeners.add(listener);
            listener.registerListeners();
        });
    }

    public abstract String getListenerName();

    protected abstract void registerListeners();

    @Override
    public void run() {}

}
