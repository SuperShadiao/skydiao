package pers.XiaoShadiao.skydiao.mixin.client.debug;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.IdDispatchCodec;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import pers.XiaoShadiao.skydiao.utils.ToolList;

import java.util.List;

@Mixin(IdDispatchCodec.class)
public abstract class MixinIdDispatchCodec<B extends ByteBuf, V, T> implements StreamCodec<B, V> {

    @Final
    @Shadow
    private List<?> byId;

    @WrapOperation(method = "decode(Lio/netty/buffer/ByteBuf;)Ljava/lang/Object;", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/VarInt;read(Lio/netty/buffer/ByteBuf;)I"))
    public int readVarInt(ByteBuf input, Operation<Integer> original) {
        Integer id = original.call(input);
        if(id == 24) {
            ToolList.getInstance().log.info("Hit Packet Id 24 Debug: " + byId);
        }
        return id;
    }

}
