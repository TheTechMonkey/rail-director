package com.techmonkey.raildirector.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Vector3f;

import com.techmonkey.raildirector.ControlledDetectorRailBlock;
import com.techmonkey.raildirector.RailDirector;
import com.techmonkey.raildirector.RailRouter;

import java.util.ArrayList;
import java.util.List;

public final class RailRouteOverlay {
    private static final int RESTING_COLOR = 0xFFFFFFFF;
    private static final int ACTIVE_COLOR = 0xFF42E7F5;

    private RailRouteOverlay() {
    }

    public static void register() {
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, vanillaOutline) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null
                || (!minecraft.player.getMainHandItem().is(RailDirector.RAIL_WRENCH)
                    && !minecraft.player.getOffhandItem().is(RailDirector.RAIL_WRENCH))
                || !(minecraft.hitResult instanceof BlockHitResult hit)) {
                return true;
            }

            BlockPos pos = hit.getBlockPos();
            BlockState state = minecraft.level.getBlockState(pos);
            if (!state.is(Blocks.DETECTOR_RAIL) && !RailDirector.isOwnedDetector(state)) {
                return true;
            }

            BlockPos switchRail = RailRouter.findSwitchRail(minecraft.level, pos);
            if (switchRail == null) {
                return true;
            }

            RailShape switchShape = minecraft.level.getBlockState(switchRail)
                .getValue(net.minecraft.world.level.block.RailBlock.SHAPE);
            RailShape resting;
            RailShape active;
            if (RailDirector.isOwnedDetector(state)) {
                resting = state.getValue(ControlledDetectorRailBlock.INACTIVE_ROUTE);
                active = state.getValue(ControlledDetectorRailBlock.ACTIVE_ROUTE);
            } else {
                resting = switchShape;
                active = resting;
            }
            Direction incoming = RailRouter.directionFrom(switchRail, pos);
            List<RailShape> validRoutes = RailRouter.connectedShapes(minecraft.level, switchRail, pos);
            if (!validRoutes.isEmpty()) {
                if (!validRoutes.contains(resting)) resting = validRoutes.get(0);
                if (!validRoutes.contains(active)) active = validRoutes.get(0);
            }

            var camera = minecraft.gameRenderer.mainCamera().position();
            PoseStack poseStack = context.poseStack();
            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            submit(context.submitNodeCollector(), poseStack,
                routeLines(minecraft, switchRail, resting, incoming, 0.155), RESTING_COLOR,
                minecraft.getWindow().getAppropriateLineWidth() + 2.0F);
            submit(context.submitNodeCollector(), poseStack,
                routeLines(minecraft, switchRail, active, incoming, 0.185), ACTIVE_COLOR,
                minecraft.getWindow().getAppropriateLineWidth() + 3.0F);
            poseStack.popPose();
            return false;
        });
    }

    private static void submit(net.minecraft.client.renderer.SubmitNodeCollector collector, PoseStack poseStack,
                               List<Line> lines, int color, float width) {
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, consumer) -> {
            for (Line line : lines) {
                drawLine(pose, consumer, line, color, width);
            }
        });
    }

    private static List<Line> routeLines(Minecraft minecraft, BlockPos junction, RailShape shape,
                                         Direction incoming, double height) {
        List<Line> lines = new ArrayList<>();
        Direction outgoing = otherEnd(shape, incoming);
        if (outgoing == null) {
            return lines;
        }

        BlockPos destination = findRail(minecraft, junction, outgoing);
        if (destination == null) destination = junction.relative(outgoing);

        // Outline the two ordinary rail blocks that make up this selectable route.
        addTopSquare(lines, junction, height, 0.055);
        addTopSquare(lines, destination, height, 0.055);

        double startX = junction.getX() + 0.5 + incoming.getStepX() * 0.48;
        double startZ = junction.getZ() + 0.5 + incoming.getStepZ() * 0.48;
        double startY = junction.getY() + height;
        double endX = destination.getX() + 0.5;
        double endZ = destination.getZ() + 0.5;
        double endY = destination.getY() + height;

        if (incoming.getAxis() == outgoing.getAxis()) {
            lines.add(new Line(startX, startY, startZ, endX, endY, endZ));
        } else {
            addCurve(lines, startX, startY, startZ,
                junction.getX() + 0.5, junction.getY() + height, junction.getZ() + 0.5,
                endX, endY, endZ);
        }
        addArrow(lines, endX, endY, endZ, outgoing);
        return lines;
    }

    private static void addTopSquare(List<Line> lines, BlockPos pos, double height, double inset) {
        double x1 = pos.getX() + inset;
        double x2 = pos.getX() + 1.0 - inset;
        double y = pos.getY() + height;
        double z1 = pos.getZ() + inset;
        double z2 = pos.getZ() + 1.0 - inset;
        lines.add(new Line(x1, y, z1, x2, y, z1));
        lines.add(new Line(x2, y, z1, x2, y, z2));
        lines.add(new Line(x2, y, z2, x1, y, z2));
        lines.add(new Line(x1, y, z2, x1, y, z1));
    }

    private static void addCurve(List<Line> lines, double startX, double startY, double startZ,
                                 double controlX, double controlY, double controlZ,
                                 double endX, double endY, double endZ) {
        int segments = 16;
        double previousX = startX;
        double previousY = startY;
        double previousZ = startZ;
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            double u = 1.0 - t;
            double nextX = u * u * startX + 2.0 * u * t * controlX + t * t * endX;
            double nextY = u * u * startY + 2.0 * u * t * controlY + t * t * endY;
            double nextZ = u * u * startZ + 2.0 * u * t * controlZ + t * t * endZ;
            lines.add(new Line(previousX, previousY, previousZ, nextX, nextY, nextZ));
            previousX = nextX;
            previousY = nextY;
            previousZ = nextZ;
        }
    }

    private static void addArrow(List<Line> lines, double x, double y, double z, Direction direction) {
        double backX = -direction.getStepX() * 0.22;
        double backZ = -direction.getStepZ() * 0.22;
        double sideX = -direction.getStepZ() * 0.16;
        double sideZ = direction.getStepX() * 0.16;
        lines.add(new Line(x, y, z, x + backX + sideX, y, z + backZ + sideZ));
        lines.add(new Line(x, y, z, x + backX - sideX, y, z + backZ - sideZ));
    }

    private static Direction otherEnd(RailShape shape, Direction incoming) {
        Direction first;
        Direction second;
        switch (shape) {
            case NORTH_SOUTH -> { first = Direction.NORTH; second = Direction.SOUTH; }
            case EAST_WEST -> { first = Direction.EAST; second = Direction.WEST; }
            case SOUTH_EAST -> { first = Direction.SOUTH; second = Direction.EAST; }
            case SOUTH_WEST -> { first = Direction.SOUTH; second = Direction.WEST; }
            case NORTH_WEST -> { first = Direction.NORTH; second = Direction.WEST; }
            case NORTH_EAST -> { first = Direction.NORTH; second = Direction.EAST; }
            default -> { return null; }
        }
        if (first == incoming) return second;
        if (second == incoming) return first;
        return null;
    }

    private static BlockPos findRail(Minecraft minecraft, BlockPos origin, Direction direction) {
        BlockPos beside = origin.relative(direction);
        if (minecraft.level.getBlockState(beside).getBlock() instanceof BaseRailBlock) return beside;
        if (minecraft.level.getBlockState(beside.above()).getBlock() instanceof BaseRailBlock) return beside.above();
        if (minecraft.level.getBlockState(beside.below()).getBlock() instanceof BaseRailBlock) return beside.below();
        return null;
    }

    private static void drawLine(PoseStack.Pose pose, VertexConsumer consumer, Line line, int color, float width) {
        Vector3f normal = new Vector3f((float) (line.x2 - line.x1), (float) (line.y2 - line.y1),
            (float) (line.z2 - line.z1)).normalize();
        consumer.addVertex(pose, (float) line.x1, (float) line.y1, (float) line.z1)
            .setColor(color).setNormal(pose, normal).setLineWidth(width);
        consumer.addVertex(pose, (float) line.x2, (float) line.y2, (float) line.z2)
            .setColor(color).setNormal(pose, normal).setLineWidth(width);
    }

    private record Line(double x1, double y1, double z1, double x2, double y2, double z2) {
    }
}
