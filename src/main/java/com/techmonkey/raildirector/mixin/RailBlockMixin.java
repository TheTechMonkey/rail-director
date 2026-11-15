package com.techmonkey.raildirector.mixin;

import com.techmonkey.raildirector.RailDirectorData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RailBlock.class)
public abstract class RailBlockMixin {
    @Inject(method = "updateState", at = @At("HEAD"), cancellable = true)
    private void raildirector$keepCommandedShape(BlockState state, Level level, BlockPos pos, Block block, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel && RailDirectorData.get(serverLevel).isControlled(pos.asLong())) {
            ci.cancel();
        }
    }
}
