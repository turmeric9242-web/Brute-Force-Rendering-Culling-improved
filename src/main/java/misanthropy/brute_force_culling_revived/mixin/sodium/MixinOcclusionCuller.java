package misanthropy.brute_force_culling_revived.mixin.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import misanthropy.brute_force_culling_revived.api.Config;
import misanthropy.brute_force_culling_revived.api.CullingStateManager;
import misanthropy.brute_force_culling_revived.api.impl.IRenderSectionVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OcclusionCuller.class)
public abstract class MixinOcclusionCuller {

    @ModifyReturnValue(method = "isSectionVisible", at = @At(value = "RETURN"), remap = false)
    private static boolean onIsSectionVisible(boolean visible, RenderSection section, Viewport viewport, float maxDistance) {
        if (!visible) return false;
        if (CullingStateManager.checkCulling) return true;
        if (!Config.shouldCullChunk()) return true;

        boolean culled = !CullingStateManager.shouldRenderChunk((IRenderSectionVisibility) section, true);
        CullingStateManager.countChunk(culled);
        return !culled;
    }

    @Inject(method = "findVisible", at = @At(value = "HEAD"), remap = false, cancellable = true)
    private void onFindVisible(OcclusionCuller.Visitor visitor, Viewport viewport, float searchDistance, boolean useOcclusionCulling, int frame, CallbackInfo ci) {
        if (Config.getAsyncChunkRebuild() && RenderSystem.isOnRenderThread()) {
            ci.cancel();
        }
    }
}