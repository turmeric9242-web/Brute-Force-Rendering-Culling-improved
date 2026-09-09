package misanthropy.brute_force_culling_revived.api.impl;

public interface IRenderSectionVisibility {
    boolean bruteForceRenderingRevived$isVisibleAtFrame(int clientTick);

    void bruteForceRenderingRevived$markVisibleAtFrame(int clientTick);

    int bruteForceRenderingRevived$getPositionX();

    int bruteForceRenderingRevived$getPositionY();

    int bruteForceRenderingRevived$getPositionZ();
}
