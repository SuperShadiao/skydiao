package net.hypixel.modapi.fabric.payload;

import io.netty.buffer.ByteBuf;
import net.hypixel.modapi.packet.HypixelPacket;
import net.hypixel.modapi.serializer.PacketSerializer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public class ServerboundHypixelPayload implements CustomPacketPayload {
    private final CustomPacketPayload.Type<@NotNull ServerboundHypixelPayload> id;
    private final HypixelPacket packet;

    public ServerboundHypixelPayload(HypixelPacket packet) {
        this.id = new CustomPacketPayload.Type<>(Identifier.parse(packet.getIdentifier()));
        this.packet = packet;
    }

    private void write(ByteBuf buf) {
        PacketSerializer serializer = new PacketSerializer(buf);
        packet.write(serializer);
    }

    public static StreamCodec<@NotNull ByteBuf, @NotNull ServerboundHypixelPayload> buildCodec(CustomPacketPayload.Type<@NotNull ServerboundHypixelPayload> id) {
        return CustomPacketPayload.codec(ServerboundHypixelPayload::write, buf -> {
            throw new UnsupportedOperationException("Cannot read ServerboundHypixelPayload");
        });
    }

    @Override
    public @NotNull Type<? extends @NotNull CustomPacketPayload> type() {
        return id;
    }
}

