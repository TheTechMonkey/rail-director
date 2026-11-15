package com.techmonkey.raildirector;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.RailShape;

public record RailCommand(BlockPos target, RailShape absentShape, RailShape presentShape) {
    private static final Codec<RailShape> SHAPE_CODEC = Codec.STRING.comapFlatMap(name -> {
        try {
            RailShape shape = RailShape.valueOf(name);
            return RailShapes.isFlat(shape)
                ? DataResult.success(shape)
                : DataResult.error(() -> "Unsupported rail shape: " + name);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "Unknown rail shape: " + name);
        }
    }, RailShape::name);

    public static final Codec<RailCommand> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BlockPos.CODEC.fieldOf("target").forGetter(RailCommand::target),
        SHAPE_CODEC.fieldOf("absent_shape").forGetter(RailCommand::absentShape),
        SHAPE_CODEC.fieldOf("present_shape").forGetter(RailCommand::presentShape)
    ).apply(instance, RailCommand::new));
}
