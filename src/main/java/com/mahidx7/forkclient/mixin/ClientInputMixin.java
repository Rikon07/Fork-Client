package com.mahidx7.forkclient.mixin;

import com.mahidx7.forkclient.client.ForkClientController;
import com.mahidx7.forkclient.client.permissions.FeaturePermissions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientInput.class)
public class ClientInputMixin {
    @Shadow
    public Input keyPresses;

    @Unique
    @Inject(method = "tick", at = @At("RETURN"))
    private void forkClient$onTick(CallbackInfo ci) {
        ForkClientController ctrl = ForkClientController.INSTANCE;
        Minecraft client = Minecraft.getInstance();

        boolean shift = this.keyPresses.shift();
        boolean sprint = this.keyPresses.sprint();

        if (!FeaturePermissions.canUseMovement()) {
            return;
        }

        if (ctrl.isModuleEnabled("toggle_sneak") && ctrl.isToggleSneakLatched()) {
            shift = true;
        }

        if (ctrl.isModuleEnabled("auto_sprint")
                && client.player != null
                && !ctrl.isToggleSneakLatched()) {
            sprint = true;
        }

        if (shift != this.keyPresses.shift() || sprint != this.keyPresses.sprint()) {
            this.keyPresses = new Input(
                    this.keyPresses.forward(),
                    this.keyPresses.backward(),
                    this.keyPresses.left(),
                    this.keyPresses.right(),
                    this.keyPresses.jump(),
                    shift,
                    sprint
            );
        }
    }
}
