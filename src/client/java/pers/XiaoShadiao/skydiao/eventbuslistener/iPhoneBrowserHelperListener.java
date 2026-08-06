package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class iPhoneBrowserHelperListener extends AbstractForagingListener {

    private static final Pattern areaPattern = Pattern.compile("HOTSPOT! Your Hunting Hotspot is the (.*)!");

    @Override
    public String getListenerName() {
        return "iPhoneBrowserHelperListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(!ConfigManager.safariRenderTargetESP.getValue() || mc.player == null || mc.level == null || !isInSafari()) return;

        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if((!(entity instanceof Mob)) || entity instanceof HappyGhast) continue;
            RenderUtils.renderESP(wr, entity, 1, 0, 0, 1, false);
        }

        wr.finishDraw();
    }

    private void onChat(Component component, boolean b) {
        if(!StatusManager.get().isInSafari() || !ConfigManager.safariBoardcastHotspot.getValue()) return;

        String message = ToolList.getInstance().deleteColorCode(component.getString());
        Matcher matcher = areaPattern.matcher(message);
        if(matcher.find()) {
            String send = "/pc [SkyDiao] Hunting热点: " + matcher.group(1);
            if(ToolList.getInstance().isDevEnvironment()) ToolList.printChatMessage(Component.literal(send));
            ToolList.sendChatMessage(send);
        }
    }

}
