package misanthropy.brute_force_culling_revived.util;

import misanthropy.brute_force_culling_revived.api.impl.IRenderSectionVisibility;

public class DummySection implements IRenderSectionVisibility {
    private int x;
    private int y;
    private int z;

    public void set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean bruteForceRenderingRevived$isVisibleAtFrame(int clientTick) {
        return false;
    }

    @Override
    public void bruteForceRenderingRevived$markVisibleAtFrame(int clientTick) {
    }

    @Override
    public int bruteForceRenderingRevived$getPositionX() {
        return x;
    }

    @Override
    public int bruteForceRenderingRevived$getPositionY() {
        return y;
    }

    @Override
    public int bruteForceRenderingRevived$getPositionZ() {
        return z;
    }
}