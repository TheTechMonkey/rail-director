package com.techmonkey.raildirector.mixin;

import com.techmonkey.raildirector.RailRouter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DetectorRailBlock.class)
public abstract class DetectorRailBlockMixin {
    @Inject(method = "checkPressed", at = @At("TAIL"))
    private void raildirector$afterPressedCheck(Level level, BlockPos pos, BlockState originalState, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockState currentState = level.getBlockState(pos);
        if (!(currentState.getBlock() instanceof DetectorRailBlock)) {
            return;
        }

        boolean before = originalState.getValue(DetectorRailBlock.POWERED);
        boolean after = currentState.getValue(DetectorRailBlock.POWERED);
        if (before != after) {
            RailRouter.detectorChanged(serverLevel, pos, after);
        }
    }
}
