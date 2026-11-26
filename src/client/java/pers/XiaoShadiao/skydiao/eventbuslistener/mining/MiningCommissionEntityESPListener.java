package pers.XiaoShadiao.skydiao.eventbuslistener.mining;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;
import pers.XiaoShadiao.skydiao.utils.tab.TabReader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MiningCommissionEntityESPListener extends AbstractMiningListener {

    private static final Map<String, List<Object>> COMMISSIONS = Map.of(
            "Treasure Hoarder Puncher", List.of("Treasure Hunter"),
            "Goblin Slayer", List.of("Goblin", "Weakling", "Creepertamer", "Pitfighter"),
            "Glacite Walker Slayer", List.of("Ice Walker"),
            "Star Sentry Puncher", List.of("Crystal Sentry"),

            // CN

            "Automaton Slayer", List.of(IronGolem.class),
            "Sludge Slayer", List.of(Slime.class),
            "Team Treasurite Member Slayer", List.of("Team Treasurite"),
            // Goblin Slayer duplicated
            "Yog Slayer", List.of(MagmaCube.class),
            "Thyst Slayer", List.of(Endermite.class),
            "Boss Corleone Slayer", List.of("Boss Corleone")
    );

    /** 实体名/类 -> 委托名；启动时反向建表，渲染时每个实体只查表。 */
    private static final CommissionIndex INDEX = CommissionIndex.of(COMMISSIONS);
    private static final Set<String> COMMISSION_KEYS = COMMISSIONS.keySet();

    /** 本 tick 已接取且未完成的委托，tick 线程写、渲染线程读。 */
    private volatile Set<String> activeCommissions = Set.of();

    @Override
    public String getListenerName() {
        return "MiningCommissionEntityESPListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::refreshActiveCommissions);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    /** 每 tick 解析一次 tab，渲染线程不再逐个委托查询 TabReader。 */
    private void refreshActiveCommissions(net.minecraft.client.Minecraft mc) {
        if (mc.player == null || mc.level == null || !isInCorrectArea()
                || !ConfigManager.miningCommissionEntityESP.getValue()) {
            activeCommissions = Set.of();
            return;
        }
        TabReader.refreshTab();
        List<String> lines = TabReader.getLines();
        Set<String> active = new HashSet<>();
        for (String key : COMMISSION_KEYS) {
            for (String line : lines) {
                if (!line.contains(key)) continue;
                if (!line.contains("DONE")) active.add(key);
                break;
            }
        }
        activeCommissions = active;
    }

    private void onLastRender(LevelRenderContext context) {
        if(mc.player == null || mc.level == null || !isInCorrectArea() || !ConfigManager.miningCommissionEntityESP.getValue()) return;
        Set<String> active = activeCommissions;
        if (active.isEmpty()) return;

        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        for (Entity entity : mc.level.entitiesForRendering()) {
            // 每个实体名字只展平一次，再查预建好的「实体 -> 委托」表
            String name = ToolList.getInstance().deleteColorCode(entity.getName().getString()).trim();
            Set<String> commissions = INDEX.match(entity.getClass(), name);
            if (commissions.isEmpty()) continue;
            for (String commission : commissions) {
                if (!active.contains(commission)) continue;
                RenderUtils.renderESP(wr, entity, 1, 0, 1, 1, false);
                break;
            }
        }
        wr.finishDraw();
    }

    private boolean isInCorrectArea() {
        return isInMines() || isInCrystalHollows();
    }

    private record CommissionIndex(Map<Class<?>, Set<String>> byClass, Map<String, Set<String>> byName) {

        private static CommissionIndex of(Map<String, List<Object>> source) {
            Map<Class<?>, Set<String>> byClass = new HashMap<>();
            Map<String, Set<String>> byName = new HashMap<>();
            for (Map.Entry<String, List<Object>> entry : source.entrySet()) {
                for (Object target : entry.getValue()) {
                    if (target instanceof Class<?> clazz) {
                        byClass.computeIfAbsent(clazz, key -> new HashSet<>()).add(entry.getKey());
                    } else if (target instanceof String name) {
                        byName.computeIfAbsent(name, key -> new HashSet<>()).add(entry.getKey());
                    }
                }
            }
            byClass.replaceAll((key, value) -> Set.copyOf(value));
            byName.replaceAll((key, value) -> Set.copyOf(value));
            return new CommissionIndex(Map.copyOf(byClass), Map.copyOf(byName));
        }

        private Set<String> match(Class<?> clazz, String name) {
            Set<String> byClassSet = byClass.get(clazz);
            Set<String> byNameSet = byName.get(name);
            if (byClassSet == null) return byNameSet == null ? Set.of() : byNameSet;
            if (byNameSet == null) return byClassSet;
            Set<String> union = new HashSet<>(byClassSet);
            union.addAll(byNameSet);
            return union;
        }
    }
}
