package misanthropy.brute_force_culling_revived.mixin.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import misanthropy.brute_force_culling_revived.util.SodiumSectionAsyncUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderRegion.class)
public abstract class MixinRenderRegion {
    @ModifyReturnValue(method = "getSection", at = @At("RETURN"), remap = false)
    private RenderSection onGetSection(RenderSection section) {
        if (SodiumSectionAsyncUtil.renderingEntities && section == null) {
            return new RenderSection((RenderRegion) (Object) this, 0, 0, 0);
        }

        return section;
    }
}
