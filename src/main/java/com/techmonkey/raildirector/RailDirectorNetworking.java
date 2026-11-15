package com.techmonkey.raildirector;

import com.techmonkey.raildirector.network.OpenEditorPayload;
import com.techmonkey.raildirector.network.SaveConfigPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

public final class RailDirectorNetworking {
    public static final long NO_RAIL = Long.MIN_VALUE;

    private RailDirectorNetworking() {
    }

    public static void registerServer() {
        PayloadTypeRegistry.clientboundPlay().register(OpenEditorPayload.TYPE, OpenEditorPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SaveConfigPayload.TYPE, SaveConfigPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SaveConfigPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> save(player, payload));
        });
    }

    public static void openEditor(ServerPlayer player, BlockPos detector) {
        if (!(player.level() instanceof ServerLevel level) || !RailDirector.isOwnedDetector(level.getBlockState(detector))) {
            return;
        }

        DetectorConfig stored = RailDirectorData.get(level).getConfig(detector.asLong());
        RailCommand command = stored == null ? null : stored.commandFor(detector);
        int current = RailShapes.ordinal(level.getBlockState(detector).getValue(ControlledDetectorRailBlock.ROUTE));
        int absent = command == null ? current : RailShapes.ordinal(command.absentShape());
        int present = command == null ? current : RailShapes.ordinal(command.presentShape());
        int connections = RailRouter.connectedRailMask(level, detector);

        ServerPlayNetworking.send(player, new OpenEditorPayload(detector, connections, absent, present));
    }

    private static void save(ServerPlayer player, SaveConfigPayload payload) {
        if (!(player.level() instanceof ServerLevel level)
            || player.blockPosition().distManhattan(payload.detector()) > 8
            || !RailDirector.isOwnedDetector(level.getBlockState(payload.detector()))
            || (!player.getMainHandItem().is(RailDirector.RAIL_WRENCH)
                && !player.getOffhandItem().is(RailDirector.RAIL_WRENCH))) {
            return;
        }

        RailDirectorData data = RailDirectorData.get(level);
        if (payload.clear()) {
            data.remove(payload.detector().asLong());
            BlockState owned = level.getBlockState(payload.detector());
            BlockState vanilla = Blocks.DETECTOR_RAIL.defaultBlockState()
                .setValue(DetectorRailBlock.SHAPE, owned.getValue(DetectorRailBlock.SHAPE))
                .setValue(DetectorRailBlock.POWERED, owned.getValue(DetectorRailBlock.POWERED))
                .setValue(BaseRailBlock.WATERLOGGED, owned.getValue(BaseRailBlock.WATERLOGGED));
            level.setBlockAndUpdate(payload.detector(), vanilla);
            player.sendOverlayMessage(Component.translatable("screen.raildirector.cleared"));
            return;
        }

        List<RailCommand> commands = List.of(new RailCommand(
            payload.detector(),
            RailShapes.fromOrdinal(payload.absentShape()),
            RailShapes.fromOrdinal(payload.presentShape())
        ));

        DetectorConfig config = new DetectorConfig(payload.detector(), List.copyOf(commands));
        data.put(config);
        RailRouter.applyCurrentConfiguration(level, config);
        player.sendOverlayMessage(Component.translatable("screen.raildirector.saved"));
    }
}
