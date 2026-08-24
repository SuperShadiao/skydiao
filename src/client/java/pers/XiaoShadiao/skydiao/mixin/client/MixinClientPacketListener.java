package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.server.RunningOnDifferentThreadException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pers.XiaoShadiao.skydiao.fabriccustomevent.CustomFabricEvents;
import pers.XiaoShadiao.skydiao.utils.ToolList;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener extends ClientCommonPacketListenerImpl {

    protected MixinClientPacketListener(Minecraft minecraft, Connection connection, CommonListenerCookie cookie) {
        super(minecraft, connection, cookie);
    }

    @WrapMethod(method = "handleLogin")
    public void handleLogin(ClientboundLoginPacket packet, Operation<Void> original) {
        try {
            original.call(packet);
        } catch (RunningOnDifferentThreadException e) {
            throw e;
        } catch (Throwable t) {
            // onPacketError(packet, t instanceof Exception e ? e : new RuntimeException(t));
            ToolList.mc.delayCrash(new CrashReport("A login error occurred", t));
        }
    }

    @WrapOperation(method = "handleBundlePacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
    public void subPacketEventPoster(Packet<?> instance, PacketListener t, Operation<Void> original) {
        if(!CustomFabricEvents.CLIENT_PACKET_EVENT.invoker().onPacket(instance, t, ToolList.mc.packetProcessor())) original.call(instance, t);
    }

}
