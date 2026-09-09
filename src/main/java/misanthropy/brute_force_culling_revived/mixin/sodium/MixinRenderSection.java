package misanthropy.brute_force_culling_revived.mixin.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import misanthropy.brute_force_culling_revived.api.impl.IRenderSectionVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RenderSection.class)
public abstract class MixinRenderSection implements IRenderSectionVisibility {

    @Shadow(remap = false)
    public abstract int getOriginX();

    @Shadow(remap = false)
    public abstract int getOriginY();

    @Shadow(remap = false)
    public abstract int getOriginZ();

    @Unique
    private int bruteForceRenderingRevived$cullingLastVisibleFrame;

    @Override
    public boolean bruteForceRenderingRevived$isVisibleAtFrame(int frame) {
        return frame == bruteForceRenderingRevived$cullingLastVisibleFrame;
    }

    @Override
    public void bruteForceRenderingRevived$markVisibleAtFrame(int frame) {
        bruteForceRenderingRevived$cullingLastVisibleFrame = frame;
    }

    @Override
    public int bruteForceRenderingRevived$getPositionX() {
        return getOriginX();
    }

    @Override
    public int bruteForceRenderingRevived$getPositionY() {
        return getOriginY();
    }

    @Override
    public int bruteForceRenderingRevived$getPositionZ() {
        return getOriginZ();
    }
}
