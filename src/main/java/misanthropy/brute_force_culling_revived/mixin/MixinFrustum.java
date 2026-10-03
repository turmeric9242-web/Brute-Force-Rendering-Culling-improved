package misanthropy.brute_force_culling_revived.mixin;

import misanthropy.brute_force_culling_revived.api.Config;
import misanthropy.brute_force_culling_revived.api.CullingStateManager;
import misanthropy.brute_force_culling_revived.util.DummySection;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Frustum.class)
public abstract class MixinFrustum {

    @Unique
    private static final ThreadLocal<DummySection> DUMMY_SECTION = ThreadLocal.withInitial(DummySection::new);

    @Inject(method = "isVisible", at = @At(value = "RETURN"), cancellable = true)
    public void afterVisible(@NotNull AABB aabb, @NotNull CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !CullingStateManager.applyFrustum || !Config.shouldCullChunk()) {
            return;
        }
        double sizeX = aabb.maxX - aabb.minX;
        double sizeY = aabb.maxY - aabb.minY;
        double sizeZ = aabb.maxZ - aabb.minZ;
        if (sizeX < 15.0 || sizeX > 17.0 || sizeY < 15.0 || sizeY > 17.0 || sizeZ < 15.0 || sizeZ > 17.0) {
            return;
        }

        DummySection section = DUMMY_SECTION.get();

        section.set(
                (int) ((aabb.minX + aabb.maxX) * 0.5D),
                (int) ((aabb.minY + aabb.maxY) * 0.5D),
                (int) ((aabb.minZ + aabb.maxZ) * 0.5D)
        );

        boolean culled = !CullingStateManager.shouldRenderChunk(section, true);
        CullingStateManager.countChunk(culled);
        if (culled) {
            cir.setReturnValue(false);
        }
    }
}
