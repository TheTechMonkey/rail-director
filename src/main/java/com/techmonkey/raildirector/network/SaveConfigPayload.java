package com.techmonkey.raildirector.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public record SaveConfigPayload(BlockPos detector, boolean clear, int absentShape, int presentShape)
    implements CustomPacketPayload {
    public static final Type<SaveConfigPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath("raildirector", "save_config"));
    public static final StreamCodec<FriendlyByteBuf, SaveConfigPayload> CODEC =
        StreamCodec.of(SaveConfigPayload::write, SaveConfigPayload::read);

    private static SaveConfigPayload read(FriendlyByteBuf buf) {
        return new SaveConfigPayload(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
    }

    private static void write(FriendlyByteBuf buf, SaveConfigPayload payload) {
        buf.writeBlockPos(payload.detector());
        buf.writeBoolean(payload.clear());
        buf.writeVarInt(payload.absentShape());
        buf.writeVarInt(payload.presentShape());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
