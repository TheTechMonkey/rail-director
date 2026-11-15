package com.techmonkey.raildirector.mixin;

import com.techmonkey.raildirector.RailDirectorData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RailState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RailState.class)
public abstract class RailStateMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private BlockPos pos;

    @Inject(method = "connectTo", at = @At("HEAD"), cancellable = true)
    private void raildirector$preventVanillaConnection(RailState other, CallbackInfo ci) {
        if (isControlled()) {
            ci.cancel();
        }
    }

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void raildirector$preventVanillaPlacement(boolean hasSignal, boolean first, RailShape defaultShape,
                                                       CallbackInfoReturnable<RailState> cir) {
        if (isControlled()) {
            cir.setReturnValue((RailState) (Object) this);
        }
    }

    private boolean isControlled() {
        return level instanceof ServerLevel serverLevel
            && RailDirectorData.get(serverLevel).isControlled(pos.asLong());
    }
}
