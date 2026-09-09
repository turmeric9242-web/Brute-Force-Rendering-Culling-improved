package misanthropy.brute_force_culling_revived.api.impl;

import com.mojang.blaze3d.shaders.Uniform;
import org.jetbrains.annotations.Nullable;

public interface ICullingShader {
    boolean bruteForceRenderingRevived$hasCullingUniforms();

    @Nullable Uniform bruteForceRenderingRevived$getRenderDistance();
    @Nullable Uniform bruteForceRenderingRevived$getCullingCameraPos();
    @Nullable Uniform bruteForceRenderingRevived$getCullingCameraDir();
    @Nullable Uniform bruteForceRenderingRevived$getBoxScale();
    @Nullable Uniform bruteForceRenderingRevived$getDepthSize();
    @Nullable Uniform bruteForceRenderingRevived$getCullingSize();
    @Nullable Uniform bruteForceRenderingRevived$getLevelHeightOffset();
    @Nullable Uniform bruteForceRenderingRevived$getLevelMinSection();
    @Nullable Uniform bruteForceRenderingRevived$getEntityCullingSize();
    @Nullable Uniform bruteForceRenderingRevived$getCullingFrustum();
    @Nullable Uniform bruteForceRenderingRevived$getFrustumPos();
    @Nullable Uniform bruteForceRenderingRevived$getCullingViewMat();
    @Nullable Uniform bruteForceRenderingRevived$getCullingProjMat();
    @Nullable Uniform bruteForceRenderingRevived$getTestPos();
}