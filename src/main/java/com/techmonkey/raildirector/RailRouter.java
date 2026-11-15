package com.techmonkey.raildirector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class RailRouter {
    private static final Map<ServerLevel, RuntimeState> RUNTIME = new WeakHashMap<>();

    private RailRouter() {
    }

    public static BlockPos findNearbyRail(ServerLevel level, BlockPos detector, Direction direction) {
        BlockPos horizontal = detector.relative(direction);
        if (isPlainRail(level.getBlockState(horizontal))) {
            return horizontal;
        }
        if (isPlainRail(level.getBlockState(horizontal.above()))) {
            return horizontal.above();
        }
        if (isPlainRail(level.getBlockState(horizontal.below()))) {
            return horizontal.below();
        }
        return null;
    }

    public static boolean isValidTarget(BlockPos detector, BlockPos target) {
        int dx = Math.abs(target.getX() - detector.getX());
        int dy = Math.abs(target.getY() - detector.getY());
        int dz = Math.abs(target.getZ() - detector.getZ());
        return dy <= 1 && dx + dz == 1;
    }

    public static boolean isPlainRail(BlockState state) {
        return state.is(Blocks.RAIL);
    }

    public static int connectedRailMask(Level level, BlockPos junction) {
        int mask = 0;
        for (int i = 0; i < RailDirections.CARDINALS.length; i++) {
            BlockPos horizontal = junction.relative(RailDirections.CARDINALS[i]);
            if (isAnyRail(level.getBlockState(horizontal))
                || isAnyRail(level.getBlockState(horizontal.above()))
                || isAnyRail(level.getBlockState(horizontal.below()))) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    public static BlockPos findSwitchRail(Level level, BlockPos detector) {
        BlockPos best = null;
        int bestExits = -1;
        for (Direction direction : RailDirections.CARDINALS) {
            BlockPos candidate = findNearbyPlainRail(level, detector, direction);
            if (candidate == null) continue;
            int exits = Integer.bitCount(connectedRailMask(level, candidate));
            if (isNeighbor(candidate, detector)) exits--;
            if (exits > bestExits) {
                best = candidate;
                bestExits = exits;
            }
        }
        return best;
    }

    public static Direction directionFrom(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    public static List<RailShape> connectedShapes(Level level, BlockPos junction, BlockPos detector) {
        int mask = connectedRailMask(level, junction);
        Direction incoming = directionFrom(junction, detector);
        List<RailShape> choices = new ArrayList<>();
        for (RailShape shape : RailShapes.FLAT) {
            int needed = shapeBits(shape);
            if (needed != 0 && (needed & bit(incoming)) != 0 && (mask & needed) == needed) {
                choices.add(shape);
            }
        }
        return choices;
    }

    private static int shapeBits(RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH -> bits(Direction.NORTH, Direction.SOUTH);
            case EAST_WEST -> bits(Direction.EAST, Direction.WEST);
            case SOUTH_EAST -> bits(Direction.SOUTH, Direction.EAST);
            case SOUTH_WEST -> bits(Direction.SOUTH, Direction.WEST);
            case NORTH_WEST -> bits(Direction.NORTH, Direction.WEST);
            case NORTH_EAST -> bits(Direction.NORTH, Direction.EAST);
            default -> 0;
        };
    }

    private static int bits(Direction first, Direction second) {
        return bit(first) | bit(second);
    }

    private static int bit(Direction direction) {
        for (int i = 0; i < RailDirections.CARDINALS.length; i++) {
            if (RailDirections.CARDINALS[i] == direction) return 1 << i;
        }
        return 0;
    }

    private static BlockPos findNearbyPlainRail(Level level, BlockPos origin, Direction direction) {
        BlockPos horizontal = origin.relative(direction);
        if (isPlainRail(level.getBlockState(horizontal))) return horizontal;
        if (isPlainRail(level.getBlockState(horizontal.above()))) return horizontal.above();
        if (isPlainRail(level.getBlockState(horizontal.below()))) return horizontal.below();
        return null;
    }

    private static boolean isNeighbor(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX()) + Math.abs(first.getZ() - second.getZ()) == 1
            && Math.abs(first.getY() - second.getY()) <= 1;
    }

    private static boolean isAnyRail(BlockState state) {
        return state.getBlock() instanceof BaseRailBlock;
    }

    public static RailShape currentShape(ServerLevel level, BlockPos target) {
        BlockState state = level.getBlockState(target);
        if (RailDirector.isOwnedDetector(state)) return state.getValue(DetectorRailBlock.SHAPE);
        return isPlainRail(state) ? state.getValue(RailBlock.SHAPE) : RailShape.NORTH_SOUTH;
    }

    public static void detectorChanged(ServerLevel level, BlockPos detector, boolean active) {
        DetectorConfig config = RailDirectorData.get(level).getConfig(detector.asLong());
        if (config == null) {
            return;
        }

        RuntimeState runtime = RUNTIME.computeIfAbsent(level, ignored -> new RuntimeState());
        for (RailCommand command : config.commands()) {
            long target = command.target().asLong();
            if (!active) {
                Long lastWriter = runtime.lastWriter.get(target);
                if (lastWriter != null && lastWriter != detector.asLong()) {
                    continue;
                }
            }

            apply(level, command.target(), active ? command.presentShape() : command.absentShape());
            runtime.lastWriter.put(target, detector.asLong());
        }
    }

    public static void applyCurrentConfiguration(ServerLevel level, DetectorConfig config) {
        BlockState detectorState = level.getBlockState(config.detector());
        boolean active = detectorState.getBlock() instanceof DetectorRailBlock
            && detectorState.getValue(DetectorRailBlock.POWERED);
        RuntimeState runtime = RUNTIME.computeIfAbsent(level, ignored -> new RuntimeState());
        for (RailCommand command : config.commands()) {
            apply(level, command.target(), active ? command.presentShape() : command.absentShape());
            runtime.lastWriter.put(command.target().asLong(), config.detector().asLong());
        }
    }

    private static void apply(ServerLevel level, BlockPos target, RailShape shape) {
        BlockState state = level.getBlockState(target);
        if (!isPlainRail(state)) {
            return;
        }

        BlockState changed = state.setValue(RailBlock.SHAPE, shape);
        if (changed != state) {
            level.setBlock(target, changed, Block.UPDATE_CLIENTS);
        }
    }

    private static final class RuntimeState {
        private final Map<Long, Long> lastWriter = new HashMap<>();
    }
}
