package pers.XiaoShadiao.skydiao.eventbuslistener;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.awt.*;
import java.util.Arrays;

public class MineshaftHelperListener extends AbstractListener {

    private final IntSet claimedCorpse = new IntOpenHashSet();
    private final Int2ObjectMap<CorpseType> corpseList = new Int2ObjectOpenHashMap<>();

    private final IntSet littleFootEntities = new IntOpenHashSet();

    @Override
    public String getListenerName() {
        return "MineshaftHelperListener";
    }

    @Override
    public void registerListeners() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientStartTick);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(this::worldUnload);
        CustomFabricEvents.MOUSE_BUTTON_EVENT.register(this::onMouseEvent);
        WorldRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(WorldRenderContext context) {
        if(mc.level == null || !ConfigManager.mineshaftHelper.getValue()) return;

        RenderUtils.WorldRender wr1 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wr2 = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);

        corpseList.forEach((entityId, type) -> {
            if(!claimedCorpse.contains(entityId)) {
                Entity entity = mc.level.getEntity(entityId);
                if(entity == null || type.color.getColor() == null) return;
                Color color = new Color(type.color.getColor());
                float r = color.getRed() / 255f;
                float g = color.getGreen() / 255f;
                float b = color.getBlue() / 255f;
                float a = color.getAlpha() / 255f;
                RenderUtils.renderESP(wr1, entity.position(), r, g, b, a, false);
                RenderUtils.renderESP(wr2, entity.position(), r, g, b, a, true);
            }
        });
        for (Integer littleFootEntity : littleFootEntities) {
            Entity entity = mc.level.getEntity(littleFootEntity);
            if(entity == null) continue;

            RenderUtils.renderESP(wr1, entity, 0, 1, 1, 1, false);
            RenderUtils.renderTrace(wr1, entity, 0, 1, 1, 1);
        }
    }

    private boolean onMouseEvent(long windows, MouseButtonInfo mouseButtonInfo, int state) {
        if(!ConfigManager.mineshaftHelper.getValue()) return false;

        if(state == 1) {
            if(mc.hitResult instanceof EntityHitResult entityHitResult) {
                if(entityHitResult.getEntity() instanceof ArmorStand armorStand) {
                    claimedCorpse.add(armorStand.getId());
                }
            }
        }
        return false;
    }

    private void worldUnload(Minecraft mc, ClientLevel level) {
        claimedCorpse.clear();
        corpseList.clear();
    }

    private void onClientStartTick(Minecraft mc) {
        if(mc.level == null || !ConfigManager.mineshaftHelper.getValue() || !"mineshaft".equals(StatusManager.get().getMode())) return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if(entity instanceof ArmorStand armorStand) {
                String id = ToolList.getInstance().tryGetSkyblockItemId(armorStand.getItemBySlot(EquipmentSlot.HEAD));
                Arrays.stream(CorpseType.values()).filter(type -> type.itemId.equals(id)).findFirst().ifPresent(type -> corpseList.put(entity.getId(), type));
            } else if (entity instanceof RemotePlayer remotePlayer) {
                if(remotePlayer.getName().getString().trim().equalsIgnoreCase("Littlefoot")) {
                    littleFootEntities.add(remotePlayer.getId());
                }
            }
        }

        corpseList.int2ObjectEntrySet().removeIf(entry -> mc.level == null || mc.level.getEntity(entry.getIntKey()) == null);
        littleFootEntities.removeIf(entityId -> mc.level == null || mc.level.getEntity(entityId) == null);
    }

    // ️ ⬇️ Skyblocker Mod ⬇️

    public enum CorpseType {
        LAPIS("LAPIS_ARMOR_HELMET", ChatFormatting.BLUE), // dark blue looks bad and these two never exist in same shaft
        UMBER("ARMOR_OF_YOG_HELMET", ChatFormatting.GOLD),
        TUNGSTEN("MINERAL_HELMET", ChatFormatting.GRAY),
        VANGUARD("VANGUARD_HELMET", ChatFormatting.AQUA),
        ;

        private final String itemId;
        private final ChatFormatting color;

        CorpseType(String itemId, ChatFormatting color) {
            this.itemId = itemId;
            this.color = color;
        }
    }

}
