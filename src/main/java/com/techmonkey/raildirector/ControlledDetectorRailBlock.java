package com.techmonkey.raildirector;

import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

public final class ControlledDetectorRailBlock extends DetectorRailBlock {
    public static final EnumProperty<RailShape> ROUTE = EnumProperty.create("route", RailShape.class, RailShapes::isFlat);
    public static final EnumProperty<RailShape> INACTIVE_ROUTE = EnumProperty.create("inactive_route", RailShape.class, RailShapes::isFlat);
    public static final EnumProperty<RailShape> ACTIVE_ROUTE = EnumProperty.create("active_route", RailShape.class, RailShapes::isFlat);

    public ControlledDetectorRailBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(ROUTE, RailShape.NORTH_SOUTH)
            .setValue(INACTIVE_ROUTE, RailShape.NORTH_SOUTH)
            .setValue(ACTIVE_ROUTE, RailShape.NORTH_SOUTH));
    }

    @Override
    public EnumProperty<RailShape> getShapeProperty() {
        return DetectorRailBlock.SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(DetectorRailBlock.SHAPE, POWERED, WATERLOGGED, ROUTE, INACTIVE_ROUTE, ACTIVE_ROUTE);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effectApplier, boolean canApplyEffects) {
        // Route first, then let the detector perform its normal powered-state update. This gives the
        // junction the full detector-to-junction travel interval instead of changing it afterward.
        if (level instanceof ServerLevel serverLevel && entity instanceof AbstractMinecart) {
            restoreConfigurationIfMissing(serverLevel, pos, state);
            RailRouter.detectorChanged(serverLevel, pos, true);
        }
        super.entityInside(state, level, pos, entity, effectApplier, canApplyEffects);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        // A POWERED or route-property change also reaches this callback. Only discard the saved
        // command when the controlled detector was actually replaced by another block.
        if (!level.getBlockState(pos).is(this)) {
            RailDirectorData.get(level).remove(pos.asLong());
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    private static void restoreConfigurationIfMissing(ServerLevel level, BlockPos detector, BlockState state) {
        RailDirectorData data = RailDirectorData.get(level);
        if (data.getConfig(detector.asLong()) != null) return;

        BlockPos junction = RailRouter.findSwitchRail(level, detector);
        if (junction == null) return;
        List<RailShape> choices = RailRouter.connectedShapes(level, junction, detector);
        if (choices.isEmpty()) return;

        RailShape resting = state.getValue(INACTIVE_ROUTE);
        RailShape active = state.getValue(ACTIVE_ROUTE);
        BlockState repaired = state;
        if (!choices.contains(resting)) {
            resting = choices.get(0);
            repaired = repaired.setValue(INACTIVE_ROUTE, resting);
        }
        if (!choices.contains(active)) {
            active = choices.get(0);
            repaired = repaired.setValue(ACTIVE_ROUTE, active);
        }
        if (repaired != state) {
            level.setBlock(detector, repaired, Block.UPDATE_CLIENTS);
        }
        data.put(new DetectorConfig(detector, List.of(new RailCommand(junction, resting, active))));
    }
}
