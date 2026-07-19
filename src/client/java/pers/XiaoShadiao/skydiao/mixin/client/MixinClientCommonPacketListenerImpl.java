package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.server.RunningOnDifferentThreadException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.ToolList;

@Mixin(ClientCommonPacketListenerImpl.class)
public class MixinClientCommonPacketListenerImpl {

    @Inject(at = @At("HEAD"), method = "handleDisconnect")
    public void handleDisconnect(ClientboundDisconnectPacket packet, CallbackInfo ci) {
        if (!ToolList.mc.isSameThread() && this instanceof ClientCommonPacketListener && CustomFabricEvents.CLIENT_PACKET_EVENT.invoker().onPacket((Packet) packet, (PacketListener) this, ToolList.mc.packetProcessor())) {
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }
    }

}
