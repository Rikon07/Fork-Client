package com.mahidx7.forkclient.mixin;

import com.mahidx7.forkclient.optimization.OptimizationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirects dirty-section and terrain-setup calls from vanilla into the optimization
 * system. When blocks change, vanilla normally marks render sections dirty through
 * the Level; this mixin intercepts those calls and redirects them to the
 * OptimizationManager's section tracking system.
 */
@Mixin(Level.class)
public class LevelExtractorMixin {

    @Unique
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"))
    private void forkClient$onBlockChanged(BlockPos pos, BlockState state, int flags,
                                            int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        OptimizationManager manager = OptimizationManager.INSTANCE;
        if (!manager.isEnabled()) return;

        int sectionX = pos.getX() >> 4;
        int sectionY = pos.getY() >> 4;
        int sectionZ = pos.getZ() >> 4;

        manager.markSectionDirty(sectionX, sectionY, sectionZ);

        if ((flags & 0x01) != 0) {
            manager.scheduleRebuildForBlockArea(
                    pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                    true
            );
        }
    }
}
