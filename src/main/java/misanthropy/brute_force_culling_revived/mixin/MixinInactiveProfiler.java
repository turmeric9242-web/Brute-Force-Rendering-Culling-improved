package misanthropy.brute_force_culling_revived.mixin;

import misanthropy.brute_force_culling_revived.api.CullingStateManager;
import net.minecraft.util.profiling.InactiveProfiler;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InactiveProfiler.class)
public class MixinInactiveProfiler {

    @Inject(method = "popPush(Ljava/lang/String;)V", at = @At(value = "HEAD"))
    public void onPopPush(@NotNull String p_18564_, CallbackInfo ci) {
        if (!p_18564_.isEmpty()) {
            char c = p_18564_.charAt(0);
            if (c == 'c' || c == 'd') {
                CullingStateManager.onProfilerPopPush(p_18564_);
            }
        }
    }

    @Inject(method = "push(Ljava/lang/String;)V", at = @At(value = "HEAD"))
    public void onPush(String p_18559_, CallbackInfo ci) {
        if (!p_18559_.isEmpty()) {
            char c = p_18559_.charAt(0);
            if (c == 'c') {
                CullingStateManager.onProfilerPush(p_18559_);
            }
        }
    }
    }