package misanthropy.brute_force_culling_revived.mixin;

import misanthropy.brute_force_culling_revived.api.impl.IRenderSectionVisibility;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkRenderDispatcher.RenderChunk.class)
public abstract class MixinRenderChunk implements IRenderSectionVisibility {

    @Shadow
    @Final
    BlockPos.MutableBlockPos origin;

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
        return origin.getX();
    }

    @Override
    public int bruteForceRenderingRevived$getPositionY() {
        return origin.getY();
    }

    @Override
    public int bruteForceRenderingRevived$getPositionZ() {
        return origin.getZ();
    }
}