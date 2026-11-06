package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.HeadTextures;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.renderutils.CustomRenderPipeline;
import pers.XiaoShadiao.skydiao.utils.renderutils.RenderUtils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiConsumer;

public class EndermanSlayerBeaconRender extends AbstractListener {

    private final Set<BlockPos> beaconPosSet = new HashSet<>();

    @Override
    public String getListenerName() {
        return "EndermanSlayerBeaconRender";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onPacket);
        LevelRenderEvents.END_MAIN.register(this::onLastRender);
    }

    private void onLastRender(LevelRenderContext context) {
        if(mc.level == null || mc.player == null || !"combat_3".equals(StatusManager.get().getMode())) return;

        Iterator<BlockPos> it = beaconPosSet.iterator();

        RenderUtils.WorldRender wrLine = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_LINE);
        RenderUtils.WorldRender wrFill = RenderUtils.createWorldRenderInstance(context, CustomRenderPipeline.THROUGH_WALLS_FILL);

        while(it.hasNext()) {

            BlockPos pos = it.next();
            if(mc.level.getBlockState(pos).getBlock() instanceof BeaconBlock) {
                RenderUtils.renderESP(wrLine, pos, 0, 1, 0, 1, false);
                RenderUtils.renderESP(wrFill, pos, 0, 1, 0, 1, true);
                RenderUtils.renderTrace(wrLine, pos, 0, 1, 0, 1);
            } else {
                it.remove();
            }
        }
        if(ConfigManager.endermanSlayerEyeESP.getValue()) {
            for (ArmorStand armorStand : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(20))) {
                ItemStack itemStack = armorStand.getItemBySlot(EquipmentSlot.HEAD);
                if(itemStack.getItem() == Items.PLAYER_HEAD && HeadTextures.NUKEKUBI.equals(ToolList.getInstance().getSkullBase64(itemStack))) {
                    Vec3 pos = armorStand.position().add(0, 0.8, 0);
                    RenderUtils.renderESP(wrLine, pos, 0.4f, 0, 1, 1, false);
                    RenderUtils.renderESP(wrFill, pos, 0.4f, 0, 1, 0.3f, true);
                    RenderUtils.renderTrace(wrLine, pos, 0.4f, 0, 1, 1);
                }
            }
        }

        wrFill.finishDraw();
        wrLine.finishDraw();

    }

    private boolean onPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if(mc.player != null && ConfigManager.endermanSlayerBeaconESP.getValue()) {
            BiConsumer<BlockPos, BlockState> consumer = (pos, state) -> {
                if (state.getBlock() instanceof BeaconBlock) {
                    delayTickExecutor.delayExec(() -> beaconPosSet.add(pos.immutable()), 1);
                }
            };
            if(packet instanceof ClientboundBlockUpdatePacket blockUpdatePacket) {
                consumer.accept(blockUpdatePacket.getPos(), blockUpdatePacket.getBlockState());
            } else if(packet instanceof ClientboundSectionBlocksUpdatePacket blocksUpdatePacket) {
                blocksUpdatePacket.runUpdates(consumer);
            }
        }
        return false;
    }

}
