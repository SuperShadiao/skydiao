package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.sounds.SoundEvents;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.tab.TabReader;

import java.util.LinkedList;

public class GardenTrapListener extends AbstractListener {
    @Override
    public String getListenerName() {
        return "GardenTrapListener";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private long lastShowTime = System.currentTimeMillis();

    private void onLastRender(LevelRenderContext context) {
        if (!ConfigManager.gardenTrapPrompt.getValue() || mc.level == null || !"garden".equals(StatusManager.get().getMode()))
            return;

        long now = System.currentTimeMillis();
        if (now - lastShowTime < 30000) return;

        String fullTraps = TabReader.findLineStartsWith("Full Traps:").orElse(null);
        String noBait = TabReader.findLineStartsWith("No Bait:").orElse(null);

        LinkedList<String> cps = new LinkedList<>();

        if (fullTraps != null && !"Full Traps: None".equals(fullTraps)) cps.add("有捕鼠夹满了");
        if (noBait != null && !"No Bait: None".equals(noBait)) cps.add("有捕鼠夹没有诱饵了");

        if (cps.isEmpty()) return;

        lastShowTime = now;
        XSDHUD.bigTitle.updateTitleMsg(String.join("，", cps), 5000, SoundEvents.WITHER_SPAWN);
    }
}
