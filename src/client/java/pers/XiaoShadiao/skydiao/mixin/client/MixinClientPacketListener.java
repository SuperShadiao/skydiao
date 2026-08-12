package pers.XiaoShadiao.skydiao.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.server.RunningOnDifferentThreadException;
import org.spongepowered.asm.mixin.Mixin;

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
            onPacketError(packet, t instanceof Exception e ? e : new RuntimeException(t));
        }
    }

}
