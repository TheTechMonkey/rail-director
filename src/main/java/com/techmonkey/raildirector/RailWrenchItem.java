package com.techmonkey.raildirector;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.List;

public final class RailWrenchItem extends Item {
    public RailWrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (!state.is(Blocks.DETECTOR_RAIL) && !RailDirector.isOwnedDetector(state)) {
            return InteractionResult.PASS;
        }

        if (context.getPlayer() instanceof ServerPlayer player) {
            ServerLevel level = (ServerLevel) context.getLevel();
            var detector = context.getClickedPos();
            var switchRail = RailRouter.findSwitchRail(level, detector);
            if (switchRail == null) {
                player.sendOverlayMessage(Component.literal("Rail Director: no ordinary switch rail found beside detector"));
                return InteractionResult.SUCCESS_SERVER;
            }
            RailShape switchShape = level.getBlockState(switchRail).getValue(net.minecraft.world.level.block.RailBlock.SHAPE);
            if (state.is(Blocks.DETECTOR_RAIL)) {
                RailShape original = state.getValue(DetectorRailBlock.SHAPE);
                BlockState owned = RailDirector.CONTROLLED_DETECTOR_RAIL.defaultBlockState()
                    .setValue(DetectorRailBlock.SHAPE, original)
                    .setValue(DetectorRailBlock.POWERED, state.getValue(DetectorRailBlock.POWERED))
                    .setValue(BaseRailBlock.WATERLOGGED, state.getValue(BaseRailBlock.WATERLOGGED))
                    .setValue(ControlledDetectorRailBlock.ROUTE, original)
                    .setValue(ControlledDetectorRailBlock.INACTIVE_ROUTE, switchShape)
                    .setValue(ControlledDetectorRailBlock.ACTIVE_ROUTE, switchShape);
                level.setBlockAndUpdate(detector, owned);
            }
            cycleRoute(level, detector, switchRail, player.isShiftKeyDown(), player);
        }
        return context.getLevel().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    private static void cycleRoute(ServerLevel level, net.minecraft.core.BlockPos detector,
                                   net.minecraft.core.BlockPos switchRail,
                                   boolean inactive, ServerPlayer player) {
        BlockState state = level.getBlockState(detector);
        List<RailShape> choices = RailRouter.connectedShapes(level, switchRail, detector);
        if (choices.isEmpty()) {
            player.sendOverlayMessage(Component.literal("Rail Director: at least two neighboring tracks are required"));
            return;
        }

        BlockState normalized = state;
        if (!choices.contains(normalized.getValue(ControlledDetectorRailBlock.INACTIVE_ROUTE))) {
            normalized = normalized.setValue(ControlledDetectorRailBlock.INACTIVE_ROUTE, choices.get(0));
        }
        if (!choices.contains(normalized.getValue(ControlledDetectorRailBlock.ACTIVE_ROUTE))) {
            normalized = normalized.setValue(ControlledDetectorRailBlock.ACTIVE_ROUTE, choices.get(0));
        }

        var property = inactive
            ? ControlledDetectorRailBlock.INACTIVE_ROUTE
            : ControlledDetectorRailBlock.ACTIVE_ROUTE;
        RailShape current = normalized.getValue(property);
        int index = choices.indexOf(current);
        RailShape next = choices.get((index + 1 + choices.size()) % choices.size());
        BlockState changed = normalized.setValue(property, next);
        level.setBlockAndUpdate(detector, changed);

        RailShape resting = changed.getValue(ControlledDetectorRailBlock.INACTIVE_ROUTE);
        RailShape poweredRoute = changed.getValue(ControlledDetectorRailBlock.ACTIVE_ROUTE);
        DetectorConfig config = new DetectorConfig(detector, List.of(
            new RailCommand(switchRail, resting, poweredRoute)
        ));
        RailDirectorData.get(level).put(config);
        RailRouter.applyCurrentConfiguration(level, config);
        player.sendOverlayMessage(Component.literal(inactive
            ? "White route — no cart detected"
            : "Cyan route — cart detected"));
    }

}
