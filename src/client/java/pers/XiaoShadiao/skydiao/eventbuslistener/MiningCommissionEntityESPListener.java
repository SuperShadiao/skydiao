package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;
import pers.XiaoShadiao.skydiao.utils.tab.TabReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MiningCommissionEntityESPListener extends AbstractListener {

    @Override
    public String getListenerName() {
        return "MiningCommissionEntityESPListener";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(mc.player == null || mc.level == null || !isInCorrectArea() || !ConfigManager.miningCommissionEntityESP.getValue()) return;

        Map<String, List<Object>> commisionNameToEntity = Map.of(
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

        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);

        List<Entity> targets = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if(testEntityIsCommissionTarget(entity, commisionNameToEntity)) {
                targets.add(entity);
            }
        }

        commisionNameToEntity.entrySet().stream()
                .filter(entry -> {
                    Optional<String> line = TabReader.findLineWith(entry.getKey());
                    return line.isPresent() && !line.get().contains("DONE");
                })
                .forEach(entry -> {
                    for (Entity target : targets) {
                        if(testEntityAndTarget(target, entry.getValue())) {
                            RenderUtils.renderESP(wr, target, 1, 0, 1, 1, false);
                        }
                    }
                });

        wr.finishDraw();
    }

    private boolean testEntityIsCommissionTarget(Entity entity, Map<String, List<Object>> commisionNameToEntity) {
        return commisionNameToEntity.values().stream().anyMatch(v -> testEntityAndTarget(entity, v));
    }

    private boolean testEntityAndTarget(Entity entity, List<Object> v) {
        return v.stream().anyMatch(v2 -> switch(v2) {
            case String name -> ToolList.getInstance().deleteColorCode(entity.getName().getString()).trim().equals(name);
            case Class<?> clazz -> clazz.equals(entity.getClass());
            default -> false;
        });
    }

    private boolean isInCorrectArea() {
        return "mining_3".equals(StatusManager.get().getMode()) || "crystal_hollows".equals(StatusManager.get().getMode());
    }

}
