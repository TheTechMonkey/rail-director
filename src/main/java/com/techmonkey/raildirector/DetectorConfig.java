package com.techmonkey.raildirector;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.List;

public record DetectorConfig(BlockPos detector, List<RailCommand> commands) {
    public static final Codec<DetectorConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BlockPos.CODEC.fieldOf("detector").forGetter(DetectorConfig::detector),
        RailCommand.CODEC.listOf().fieldOf("commands").forGetter(DetectorConfig::commands)
    ).apply(instance, (detector, commands) -> new DetectorConfig(detector, List.copyOf(commands))));

    public RailCommand commandFor(BlockPos target) {
        for (RailCommand command : commands) {
            if (command.target().equals(target)) {
                return command;
            }
        }
        return null;
    }
}
