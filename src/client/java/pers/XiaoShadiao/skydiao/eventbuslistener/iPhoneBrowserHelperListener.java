package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.phys.EntityHitResult;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class iPhoneBrowserHelperListener extends AbstractForagingListener {

    private static final Pattern areaPattern = Pattern.compile("HOTSPOT! Your Hunting Hotspot is the (.*)!");
    private static final List<Pattern> tradeNPCShardPatterns = List.of(
            Pattern.compile("I found this really cool (.*) on the floor around here\\."),
            Pattern.compile("I've got an? (.*) you can h-h-have, if you w-w-want it\\.\\.\\."),
            Pattern.compile("Do you want this (.*)\\? I already maxed that Attribute\\.\\.\\."),
            Pattern.compile("Say, do you have a use for an? (.*)\\? I found it lying around\\.\\.\\.")
    );
    private static final List<Pattern> tradeNPCRequiredItemPatterns = List.of(
            Pattern.compile("I'll trade it to you in exchange for an? (.*)."),
            Pattern.compile("You can h-h-have it if you give m-m-me an? (.*)\\.\\.\\."),
            Pattern.compile("How about I give you it in exchange for, say, an? (.*)\\?"),
            Pattern.compile("I'll give you it in exchange for an? (.*)!")
    );

    private BlockPos tradeNPCPos;
    private String tradeNPCShard;
    private String tradeNPCRequiredItem;

    @Override
    public String getListenerName() {
        return "iPhoneBrowserHelperListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
        CustomFabricEvents.MOUSE_BUTTON_EVENT.register(this::onMouseClick);
    }

    private boolean onMouseClick(long l, MouseButtonInfo mouseButtonInfo, int state) {
        if(mc.screen == null) {
            if(mc.hitResult instanceof EntityHitResult entityHitResult) {
                Entity entity = entityHitResult.getEntity();
                if(entity instanceof RemotePlayer) {
                    tradeNPCPos = BlockPos.containing(entity.position());
                }
            }
        }
        return false;
    }

    private void onLastRender(LevelRenderContext context) {
        if(!ConfigManager.safariRenderTargetESP.getValue() || mc.player == null || mc.level == null || !isInSafari()) return;

        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        for (Entity entity : mc.level.entitiesForRendering()) {

            if(entity instanceof RemotePlayer player && ToolList.getInstance().deleteColorCode(player.getName().getString()).trim().equals("Hideyho")) {
                RenderUtils.renderESP(wr, entity, 1, 1, 1, 1, false);
                continue;
            }

            if((!(entity instanceof Mob)) || entity instanceof HappyGhast) continue;

            E2AMappingListener.MobInfo mobInfo = e2AMappingListener.getMobInfo(entity.asLivingEntity());

            RenderUtils.renderESP(wr, entity, 1, mobInfo != null && mobInfo.armorStand.getName().getString().contains("SPARKLING") ? 1 : 0, 0, 1, false);
        }

        wr.finishDraw();
    }

    private void onChat(Component component, boolean b) {
        if(!StatusManager.get().isInSafari()) return;

        String message = ToolList.getInstance().deleteColorCode(component.getString());
        if(ConfigManager.safariBoardcastHotspot.getValue()) {
            Matcher matcher = areaPattern.matcher(message);
            if (matcher.find()) {
                String send = "/pc [SkyDiao] Hunting热点: " + matcher.group(1);
                if (ToolList.getInstance().isDevEnvironment()) ToolList.printChatMessage(Component.literal(send));
                ToolList.sendChatMessage(send);
            }
        }

        if(ConfigManager.safariBoardcastTradeNPC.getValue()) {
            for (Pattern pattern : tradeNPCShardPatterns) {
                Matcher matcher = pattern.matcher(message);
                if (matcher.find()) {
                    tradeNPCShard = matcher.group(1);
                }
            }
            for (Pattern pattern : tradeNPCRequiredItemPatterns) {
                Matcher matcher = pattern.matcher(message);
                if (matcher.find()) {
                    tradeNPCRequiredItem = matcher.group(1);
                }
            }
            if (tradeNPCPos != null && tradeNPCShard != null && tradeNPCRequiredItem != null) {
                String send = "/pc [SkyDiao] " + tradeNPCRequiredItem + " -> " + tradeNPCShard + " at " + tradeNPCPos.getX() + " " + tradeNPCPos.getY() + " " + tradeNPCPos.getZ();
                if (ToolList.getInstance().isDevEnvironment()) ToolList.printChatMessage(Component.literal(send));
                ToolList.sendChatMessage(send);
                tradeNPCPos = null;
                tradeNPCShard = null;
                tradeNPCRequiredItem = null;
            }
        }

    }

}
