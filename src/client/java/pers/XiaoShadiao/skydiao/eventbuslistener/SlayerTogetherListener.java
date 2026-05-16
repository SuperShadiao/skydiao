package pers.XiaoShadiao.skydiao.eventbuslistener;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.hud.XSDHUD;
import pers.XiaoShadiao.skydiao.irc.ChatClientManager;
import pers.XiaoShadiao.skydiao.irc.ChatPacket;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.Objects;

public class SlayerTogetherListener extends AbstractListener {

    private final Object2LongArrayMap<BlockPos> bossPosition = new Object2LongArrayMap<>();

    private boolean summoned = false;

    @Override
    public String getListenerName() {
        return "SlayerTogetherListener";
    }

    @Override
    public void registerListeners() {
        ClientReceiveMessageEvents.GAME.register(this::onChat);
        ClientReceiveMessageEvents.GAME_CANCELED.register(this::onChat);
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
        WorldRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(WorldRenderContext context) {
        if(!ConfigManager.slayerTogether.getValue()) return;
        RenderUtils.WorldRender wr = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        for (BlockPos pos : bossPosition.keySet()) {
            RenderUtils.renderESP(wr, pos, 1, 1, 0, 1, false);
            RenderUtils.renderTrace(wr, pos, 1, 1, 0, 1);
        }
    }

    private void onStartClientTick(Minecraft mc) {
        if(mc.player == null) return;

        boolean summonedFlag = false;
        for (String s : ToolList.getInstance().fetchScoreboardLinesNoColor()) {
            if (s.equals("Slay the boss!")) {
                summonedFlag = true;
                break;
            }
        }
        if(summonedFlag != summoned) {
            summoned = summonedFlag;
            if(summonedFlag) {
                postSlayerMessage("§aboss生成!");
            }
        }
    }

    private void onChat(Component component, boolean b) {
        if(mc.player == null) return;

        String message = ToolList.getInstance().deleteColorCode(component.getString());
        if (message.matches("SLAYER MINI-BOSS (.*) has spawned!")) {

            String killsLine = null;
            for (String s : ToolList.getInstance().fetchScoreboardLinesNoColor()) {
                if(s.matches(" ?\\d+/\\d+ Kills")) {
                    killsLine = s;
                }
            }

            postSlayerMessage("§amini-boss生成!" + (killsLine != null ? "§e" + killsLine : ""));
        }

        bossPosition.object2LongEntrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            long time = entry.getLongValue();
            return mc.player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 36 || System.currentTimeMillis() - time > 10000;
        });
    }

    private void postSlayerMessage(String msg) {
        ChatPacket packet = new ChatPacket();
        JsonObject jo = new JsonObject();
        jo.addProperty("msg", msg);
        jo.addProperty("pos", Objects.requireNonNull(mc.player).blockPosition().asLong());
        packet.message = jo.toString();
        packet.packetType = "slayer_together";
        packet.initSender();
        ChatClientManager.trySendOrWarning(packet);
    }

    public void onSlayerTogetherPacket(ChatPacket packet) {
        if(!ConfigManager.slayerTogether.getValue() || packet.sender.equals(mc.getUser().getName())) return;
        JsonObject jo = JsonParser.parseString(packet.message).getAsJsonObject();
        String msg = "§e" + packet.sender + "§a: " + jo.get("msg").getAsString();
        ToolList.printChatMessage(Component.literal("§a[XSD§cSlayer§a] " + msg));
        XSDHUD.bigTitle.updateTitleMsg(msg, 1500);
        long pos = jo.get("pos").getAsLong();
        bossPosition.put(BlockPos.of(pos), System.currentTimeMillis());
    }
}
