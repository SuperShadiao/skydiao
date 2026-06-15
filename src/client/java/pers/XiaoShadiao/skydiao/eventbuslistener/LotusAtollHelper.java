package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.FrogVariants;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

public class LotusAtollHelper extends AbstractFishingListener {

    private final IntSet jumpers = new IntOpenHashSet();

    @Override
    public String getListenerName() {
        return "LotusAtollHelper";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
    }

    private void onStartTick(Minecraft mc) {
        if(mc.level == null || !isInLotusAtoll() || !ConfigManager.lotusAtollHelper.getValue()) return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if(entity instanceof Frog frog) {
                E2AMappingListener.MobInfo mobInfo = e2AMappingListener.getMobInfo(frog);
                if(mobInfo != null && mobInfo.armorStand.getName().getString().contains("Jumper")) {
                    jumpers.add(frog.getId());
                }
            }
        }
    }

    private void onLastRender(LevelRenderContext context) {
        if(mc.level == null || !isInLotusAtoll() || !ConfigManager.lotusAtollHelper.getValue()) return;

        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);

        IntIterator it = jumpers.intIterator();
        while (it.hasNext()) {
            int id = it.nextInt();
            Entity entity = mc.level.getEntity(id);
            if(entity == null) {
                it.remove();
                continue;
            }
            RenderUtils.renderESP(wr, entity, 1, 0.5f, 0, 1, false);
            RenderUtils.renderTrace(wr, entity, 1, 0.5f, 0, 1);
        }
    }

}
