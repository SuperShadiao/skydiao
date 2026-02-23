package net.hypixel.modapi.fabric.payload;

import io.netty.buffer.ByteBuf;
import net.hypixel.modapi.packet.HypixelPacket;
import net.hypixel.modapi.serializer.PacketSerializer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class ServerboundHypixelPayload implements CustomPacketPayload {
    private final CustomPacketPayload.Type<ServerboundHypixelPayload> id;
    private final HypixelPacket packet;

    public ServerboundHypixelPayload(HypixelPacket packet) {
        this.id = new CustomPacketPayload.Type<>(ResourceLocation.parse(packet.getIdentifier()));
        this.packet = packet;
    }

    private void write(ByteBuf buf) {
        PacketSerializer serializer = new PacketSerializer(buf);
        packet.write(serializer);
    }

    public static StreamCodec<ByteBuf, ServerboundHypixelPayload> buildCodec(CustomPacketPayload.Type<ServerboundHypixelPayload> id) {
        return CustomPacketPayload.codec(ServerboundHypixelPayload::write, buf -> {
            throw new UnsupportedOperationException("Cannot read ServerboundHypixelPayload");
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return id;
    }
}

