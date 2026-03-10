package pers.XiaoShadiao.skydiao.mixin.client;

import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.server.RunningOnDifferentThreadException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;

@Mixin(PacketUtils.class)
public class MixinPacketListener {

    @Inject(at = @At("HEAD"), method = "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V")
    private static <T extends PacketListener> void onPacket(Packet<T> packet, T packetListener, PacketProcessor packetProcessor, CallbackInfo ci) {
        if (packetListener instanceof ClientCommonPacketListener && CustomFabricEvents.CLIENT_PACKET_EVENT.invoker().onPacket((Packet<PacketListener>) packet, packetListener, packetProcessor)) {
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }
    }

}
