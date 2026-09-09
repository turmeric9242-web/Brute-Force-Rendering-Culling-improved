package misanthropy.brute_force_culling_revived.mixin;

import misanthropy.brute_force_culling_revived.api.CullingStateManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void bruteForceRenderingRevived$tickCulling(@NotNull Entity entity, @NotNull CallbackInfo ci) {
        CullingStateManager.tickEntityCount++;
        if (!CullingStateManager.isTickCullable(entity)) return;

        CullingStateManager.tickCulling++;
        entity.setOldPosAndRot();
        ++entity.tickCount;
        ci.cancel();
    }
}
