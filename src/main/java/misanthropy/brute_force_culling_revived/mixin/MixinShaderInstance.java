package misanthropy.brute_force_culling_revived.mixin;

import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import misanthropy.brute_force_culling_revived.api.CullingRenderEvent;
import misanthropy.brute_force_culling_revived.api.CullingStateManager;
import misanthropy.brute_force_culling_revived.api.impl.ICullingShader;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@SuppressWarnings("unused")
@Mixin(ShaderInstance.class)
public abstract class MixinShaderInstance implements ICullingShader {
    @Shadow
    @Nullable
    public abstract Uniform getUniform(String name);

    @Unique private boolean bruteForceRenderingRevived$hasCullingUniforms;

    @Unique @Nullable private Uniform CULLING_CAMERA_POS;
    @Unique @Nullable private Uniform CULLING_CAMERA_DIR;
    @Unique @Nullable private Uniform BOX_SCALE;
    @Unique @Nullable private Uniform RENDER_DISTANCE;
    @Unique @Nullable private Uniform DEPTH_SIZE;
    @Unique @Nullable private Uniform CULLING_SIZE;
    @Unique @Nullable private Uniform ENTITY_CULLING_SIZE;
    @Unique @Nullable private Uniform LEVEL_HEIGHT_OFFSET;
    @Unique @Nullable private Uniform LEVEL_MIN_SECTION;
    @Unique @Nullable private Uniform CULLING_FRUSTUM;
    @Unique @Nullable private Uniform FRUSTUM_POS;
    @Unique @Nullable private Uniform TEST_POS;
    @Unique @Nullable private Uniform CULLING_VIEW_MAT;
    @Unique @Nullable private Uniform CULLING_PROJ_MAT;

    @Final
    @Shadow
    private int programId;

    @Inject(
            method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V",
            at = @At("RETURN")
    )
    public void construct(CallbackInfo ci) {
        this.CULLING_CAMERA_POS = this.getUniform("CullingCameraPos");
        this.CULLING_CAMERA_DIR = this.getUniform("CullingCameraDir");
        this.BOX_SCALE = this.getUniform("BoxScale");
        this.TEST_POS = this.getUniform("TestPos");
        this.RENDER_DISTANCE = this.getUniform("RenderDistance");
        this.DEPTH_SIZE = this.getUniform("DepthSize");
        this.CULLING_SIZE = this.getUniform("CullingSize");
        this.LEVEL_HEIGHT_OFFSET = this.getUniform("LevelHeightOffset");
        this.LEVEL_MIN_SECTION = this.getUniform("LevelMinSection");
        this.ENTITY_CULLING_SIZE = this.getUniform("EntityCullingSize");
        this.CULLING_FRUSTUM = this.getUniform("CullingFrustum");
        this.FRUSTUM_POS = this.getUniform("FrustumPos");
        this.CULLING_VIEW_MAT = this.getUniform("CullingViewMat");
        this.CULLING_PROJ_MAT = this.getUniform("CullingProjMat");

        this.bruteForceRenderingRevived$hasCullingUniforms =
                this.CULLING_CAMERA_POS != null || this.CULLING_CAMERA_DIR != null || this.BOX_SCALE != null
                        || this.RENDER_DISTANCE != null || this.DEPTH_SIZE != null || this.CULLING_SIZE != null
                        || this.ENTITY_CULLING_SIZE != null || this.LEVEL_HEIGHT_OFFSET != null
                        || this.LEVEL_MIN_SECTION != null || this.CULLING_FRUSTUM != null || this.FRUSTUM_POS != null
                        || this.TEST_POS != null || this.CULLING_VIEW_MAT != null || this.CULLING_PROJ_MAT != null;
    }

    @Override public boolean bruteForceRenderingRevived$hasCullingUniforms() { return bruteForceRenderingRevived$hasCullingUniforms; }

    @Override @Nullable public Uniform bruteForceRenderingRevived$getCullingFrustum() { return CULLING_FRUSTUM; }
    @Override @Nullable public Uniform bruteForceRenderingRevived$getCullingCameraPos() { return CULLING_CAMERA_POS; }
    @Override @Nullable public Uniform bruteForceRenderingRevived$getRenderDistance() { return RENDER_DISTANCE; }
    @Override @Nullable public Uniform bruteForceRenderingRevived$getDepthSize() { return DEPTH_SIZE; }
    @Override @Nullable public Uniform bruteForceRenderingRevived$getCullingSize() { return CULLING_SIZE; }
    @Override @Nullable public Uniform bruteForceRenderingRevived$getLevelHeightOffset() { return LEVEL_HEIGHT_OFFSET; }
    @Override @Nullable public Uniform bruteForceRenderingRevived$getLevelMinSection() { return LEVEL_MIN_SECTION; }
    @Override @Nullable public Uniform bruteForceRenderingRevived$getEntityCullingSize() { return ENTITY_CULLING_SIZE; }
    @Override @Nullable public Uniform bruteForceRenderingRevived$getFrustumPos() { return FRUSTUM_POS; }
    @Override @Nullable public Uniform bruteForceRenderingRevived$getCullingViewMat() { return CULLING_VIEW_MAT; }
    @Override @Nullable public Uniform bruteForceRenderingRevived$getCullingProjMat() { return CULLING_PROJ_MAT; }
    @Override @Nullable public Uniform bruteForceRenderingRevived$getCullingCameraDir() { return CULLING_CAMERA_DIR; }
    @Override @Nullable public Uniform bruteForceRenderingRevived$getBoxScale() { return BOX_SCALE; }
    @Override @Nullable public Uniform bruteForceRenderingRevived$getTestPos() { return TEST_POS; }

    @Inject(at = @At("TAIL"), method = "apply")
    public void onApply(CallbackInfo ci) {
        if (CullingStateManager.updatingDepth) {
            ProgramManager.glUseProgram(this.programId);
        }
    }

    @Mixin(RenderSystem.class)
    public static class MixinRenderSystem {
        @Inject(at = @At(value = "TAIL"), method = "setupShaderLights")
        private static void shader(@NotNull ShaderInstance p_157462_, CallbackInfo ci) {
            CullingRenderEvent.setUniform(p_157462_);
        }
    }
}