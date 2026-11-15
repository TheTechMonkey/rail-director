package com.techmonkey.raildirector.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public record OpenEditorPayload(BlockPos detector, int connectionMask, int absentShape, int presentShape)
    implements CustomPacketPayload {
    public static final Type<OpenEditorPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath("raildirector", "open_editor"));
    public static final StreamCodec<FriendlyByteBuf, OpenEditorPayload> CODEC =
        StreamCodec.of(OpenEditorPayload::write, OpenEditorPayload::read);

    private static OpenEditorPayload read(FriendlyByteBuf buf) {
        return new OpenEditorPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private static void write(FriendlyByteBuf buf, OpenEditorPayload payload) {
        buf.writeBlockPos(payload.detector());
        buf.writeVarInt(payload.connectionMask());
        buf.writeVarInt(payload.absentShape());
        buf.writeVarInt(payload.presentShape());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
