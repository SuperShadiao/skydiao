package pers.XiaoShadiao.skydiao.eventbuslistener;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.ToolList;
import pers.XiaoShadiao.skydiao.utils.playerinput.AimHelper;

import java.util.Optional;

public class DungeonSomePuzzleSolverListener extends AbstractListener implements IDungeonListener {

    private final AimHelper aimHelper = new AimHelper(2.5);
    private BlockPos lastTPPos = BlockPos.ZERO;
    private BlockPos targetPos = BlockPos.ZERO;
    private int remainRotateTick = 0;

    @Override
    public String getListenerName() {
        return "DungeonSomePuzzleSolverListener";
    }

    @Override
    public void registerListeners() {
        CustomFabricEvents.CLIENT_PACKET_EVENT.register(this::onClientPacket);
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft mc) {
        if (remainRotateTick > 0) {
            remainRotateTick--;

            AimHelper.getYawPitchByBlockPos(targetPos.above(2)).updateToAimHelper(aimHelper);
        }
    }

    private boolean onClientPacket(Packet<?> packet, PacketListener packetListener, PacketProcessor packetProcessor) {
        if (StatusManager.get().isInDungeon() && ConfigManager.dungeonPuzzleHelper.getValue() && mc.level != null && packet instanceof ClientboundPlayerPositionPacket tpPacket) {
            ToolList.TPInfo tpInfo = ToolList.getInstance().parseTPPacket(tpPacket);
            Vec3 temp = tpInfo.to().position();
            BlockPos tempPos = BlockPos.containing(temp);
            Optional<BlockPos> first = BlockPos.betweenClosedStream(tempPos.offset(-2, -2, -2), tempPos.offset(2, 2, 2))
                    .filter(pos -> mc.level.getBlockState(pos).getBlock() == Blocks.END_PORTAL_FRAME)
                    .findFirst();

            if (first.isPresent()) {
                lastTPPos = first.get();

                for (int i = 0; i < 4; i++) {
                    BlockPos offset = lastTPPos.offset(6 * ((i & 0b01) == 0 ? 1 : -1), 0, 6 * ((i & 0b10) == 0 ? 1 : -1));
                    if(mc.level.getBlockState(offset).getBlock() == Blocks.END_PORTAL_FRAME) {
                        targetPos = offset;
                        remainRotateTick = 5;
                        ToolList.printChatMessage(Component.literal("§a[小沙雕] 按住前进键完成该解密!"));
                        break;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public int getFloor() {
        return 7;
    }

}
