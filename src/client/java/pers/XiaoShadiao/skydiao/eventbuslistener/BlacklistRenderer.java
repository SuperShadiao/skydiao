package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import pers.XiaoShadiao.skydiao.utils.SkyblockBlacklistManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class BlacklistRenderer extends AbstractListener {

    private boolean tippedBlacklist = false;

    @Override
    public String getListenerName() {
        return "BlacklistRenderer";
    }

    @Override
    public void registerListeners() {
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((m, l) -> tippedBlacklist = false);
    }

    private int tick;
    private final Map<RemotePlayer, SkyblockBlacklistManager.SkyblockBlacklistEntry> players = new HashMap<>();

    private void onStartTick(Minecraft mc) {
        if(mc.level == null) {
            return;
        }

        if(tick++ % 20 == 0) {
            players.clear();
            for (Entity entity : mc.level.entitiesForRendering()) {
                if(entity instanceof RemotePlayer remotePlayer) {
                    SkyblockBlacklistManager.SkyblockBlacklistEntry e = SkyblockBlacklistManager.getInstance().tryGetEntry(remotePlayer);
                    if(e != null) {
                        if(!tippedBlacklist) {
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e===============黑名单警告=============="));
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e检测到当前服务器包含黑名单玩家. 使用/skydiao viewblp查看详情."));
                            ToolList.printChatMessage(Component.literal("§a[小沙雕] §e======================================"));
                            tippedBlacklist = true;
                        }
                        players.put(remotePlayer, e);
                    }
                }
            }
        }

    }

    private void onLastRender(LevelRenderContext context) {
        if(!SkyblockBlacklistManager.getInstance().isAvaliable()) return;

        RenderUtils.WorldRender wr1 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wr2 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.NO_THROUGH_WALLS_FILL);
        for (Map.Entry<RemotePlayer, SkyblockBlacklistManager.SkyblockBlacklistEntry> entry : players.entrySet()) {
            Color color = entry.getValue().type() == null ? Color.WHITE : entry.getValue().type().color;
            RenderUtils.renderESP(wr1, entry.getKey(), color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1, false);
            RenderUtils.renderESP(wr2, entry.getKey(), color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1, true);
        }
        wr1.finishDraw();
        wr2.finishDraw();
    }

    public int printBLP() {
        ToolList.printChatMessage(Component.literal("§a[小沙雕] §e当前服务器包含" + players.size() + "个黑名单玩家"));
        for (Map.Entry<RemotePlayer, SkyblockBlacklistManager.SkyblockBlacklistEntry> entry : players.entrySet()) {
            ToolList.printChatMessage(Component.literal("§a[小沙雕] "));
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §b" + entry.getKey().getName().getString()));
            SkyblockBlacklistManager.SkyblockBlacklistEntry value = entry.getValue();
            ToolList.printChatMessage(Component.literal("§a[小沙雕] §c标签: " + (value.type() == null ? "无" : value.type().type) + ", 原因: " + value.reason()));
        }
        return 0;
    }

}
