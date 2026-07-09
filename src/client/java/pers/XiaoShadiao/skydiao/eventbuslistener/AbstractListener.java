package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pers.XiaoShadiao.skydiao.eventbuslistener.bossbar.dungeon.*;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.CarnivalFruitDigger;
import pers.XiaoShadiao.skydiao.eventbuslistener.macro.MacroManagerListener;
import pers.XiaoShadiao.skydiao.utils.Register;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractListener extends Thread {

    public static final Minecraft mc = ToolList.mc;
    public static List<AbstractListener> listeners = new ArrayList<>();
    public final Logger logger = LogManager.getLogger(getListenerName());

    public static final MacroManagerListener mml = new MacroManagerListener();

    public static final BasicListener basicListener = new BasicListener();
    public static final MineshaftShareListener mineshaftShareListener = new MineshaftShareListener();
    public static final DungeonMobESPListener dungeonMobESPListener = new DungeonMobESPListener();
    public static final E2AMappingListener e2AMappingListener = new E2AMappingListener();
    public static final PrivateIslandProtectorListener privateIslandProtectorListener = new PrivateIslandProtectorListener();
    public static final DungeonTrapRenderListener dungeonTrapRenderListener = new DungeonTrapRenderListener();
    public static final AutoEnchantmentTableGameListener autoEnchantmentTableGameListener = new AutoEnchantmentTableGameListener();
    public static final InventoryItemFilter inventoryItemFilter = new InventoryItemFilter();
    public static final AutoHarpListener autoHarpListener = new AutoHarpListener();
    public static final NecronLadderNotification necronLadderNotification = new NecronLadderNotification();
    public static final F7AutoTerminal f7AutoTerminal = new F7AutoTerminal();
    public static final AutoClickerListener autoClickerListener = new AutoClickerListener();
    public static final GalateaShulkerESPListener galateaShulkerESPListener = new GalateaShulkerESPListener();
    public static final MineshaftHelperListener mineshaftHelperListener = new MineshaftHelperListener();
    public static final RiftAutoDanceRoomListener riftAutoDanceRoomListener = new RiftAutoDanceRoomListener();
    public static final TooltipScrollController tooltipScrollController = new TooltipScrollController();
    public static final RiftTimeGunRightClickHolderListener riftTimeGunRightClickHolderListener = new RiftTimeGunRightClickHolderListener();
    public static final ResurrectionItemListener resurrectionItemListener = new ResurrectionItemListener();
    public static final SlayerTogetherListener slayerTogetherListener = new SlayerTogetherListener();
    public static final HubRatESPListener hubRatESPListener = new HubRatESPListener();
    public static final GardenTrapListener gardenTrapListener = new GardenTrapListener();
    public static final DungeonAutoCloseChest dungeonAutoCloseChest = new DungeonAutoCloseChest();
    public static final DungeonSomePuzzleSolverListener dungeonSomePuzzleSolverListener = new DungeonSomePuzzleSolverListener();
    public static final FishingHotSpotListener fishingHotSpotListener = new FishingHotSpotListener();
    public static final TitleChanger titleChanger = new TitleChanger();
    public static final WorldRenderCrashFix worldRenderCrashFix = new WorldRenderCrashFix();
    public static final GhostEntityFixer ghostEntityFixer = new GhostEntityFixer();
    public static final DungeonMiscMessageListener dungeonMiscMessageListener = new DungeonMiscMessageListener();
    public static final LotusAtollHelper lotusAtollHelper = new LotusAtollHelper();
    public static final FishingBigMobRenderListener fishingBigMobRenderListener = new FishingBigMobRenderListener();
    public static final BliveModeChatHandler bliveModeChatHandler = new BliveModeChatHandler();
    public static final TPSListener tpsListener = new TPSListener();
    public static final DungeonTrashTPSListener dungeonTrashTPSListener = new DungeonTrashTPSListener();
    public static final CarnivalFruitDigger carnvialFruitDigger = new CarnivalFruitDigger();
    public static final BlacklistRenderer blacklistRenderer = new BlacklistRenderer();
    public static final CrystalHollowHelperListener crystalHollowHelperListener = new CrystalHollowHelperListener();
    public static final DungeonTTTFailListener dungeonTTTFailListener = new DungeonTTTFailListener();
    public static final XiaoShadiaoCommandListener xiaoShadiaoCommandListenerListener = new XiaoShadiaoCommandListener();
    public static final AutoLoadoutListener autoLoadoutListener = new AutoLoadoutListener();

    public static final DungeonF1BossbarListener dungeonF1Bossbar = new DungeonF1BossbarListener();
    public static final DungeonF2BossbarListener dungeonF2Bossbar = new DungeonF2BossbarListener();
    public static final DungeonF3BossbarListener dungeonF3Bossbar = new DungeonF3BossbarListener();
    public static final DungeonF4BossbarListener dungeonF4Bossbar = new DungeonF4BossbarListener();
    public static final DungeonF5BossbarListener dungeonF5Bossbar = new DungeonF5BossbarListener();
    public static final DungeonF6BossbarListener dungeonF6Bossbar = new DungeonF6BossbarListener();
    public static final DungeonF7BossbarListener dungeonF7Bossbar = new DungeonF7BossbarListener();

    public static final FoxModelLoaderAdapter foxModelLoaderAdapter = new FoxModelLoaderAdapter();
    public static final SPMLoaderAdapter spmlLoaderAdapter = new SPMLoaderAdapter();

    public static final List<ICustomSkinModelLoader> modelLoaderAdapters = List.of(
            foxModelLoaderAdapter,
            spmlLoaderAdapter
    );

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
        listeners = Collections.unmodifiableList(listeners);
    }

    public abstract String getListenerName();

    public abstract void registerListeners();

    @Override
    public void run() {
    }

    public static <T> T printThis(T t) {
        System.out.println(t);
        return t;
    }

    public String toString() {
        return "Listener " + getListenerName() + " | " + super.toString();
    }

}
