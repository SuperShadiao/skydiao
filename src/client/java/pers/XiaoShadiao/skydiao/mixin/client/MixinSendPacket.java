package pers.XiaoShadiao.skydiao.mixin.client;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;

@Mixin(Connection.class)
public class MixinSendPacket {

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    public void sendPacket(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        boolean cancelled = CustomFabricEvents.CLIENT_SEND_CANCELLABLE_PACKET_EVENT.invoker().onSendPacket(packet);
        if (cancelled) {
            ci.cancel();
        } else {
            CustomFabricEvents.CLIENT_SEND_PACKET_EVENT.invoker().onSendPacket(packet);
        }
    }

}
