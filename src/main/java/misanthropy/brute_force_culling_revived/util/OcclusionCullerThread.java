package misanthropy.brute_force_culling_revived.util;

import misanthropy.brute_force_culling_revived.api.Config;
import misanthropy.brute_force_culling_revived.api.CullingStateManager;
import misanthropy.brute_force_culling_revived.api.ModLoader;
import misanthropy.brute_force_culling_revived.api.data.ChunkCullingMap;
import net.minecraft.client.Minecraft;

import java.util.concurrent.Semaphore;

public class OcclusionCullerThread extends Thread {
    public static OcclusionCullerThread INSTANCE;
    private volatile boolean finished = false;
    private static final Semaphore TICK_SEMAPHORE = new Semaphore(0);
    private static long lastSignalNanos;
    private static final long BUDGET_NS = 30L * 1_000_000L;

    public OcclusionCullerThread() {
        super("BFR-OcclusionCuller");
        this.setDaemon(true);
        OcclusionCullerThread previous = INSTANCE;
        if (previous != null) {
            previous.finished = true;
            previous.interrupt();
        }
        TICK_SEMAPHORE.drainPermits();
        INSTANCE = this;
    }

    public static void shouldUpdate() {
        if (!Config.getAsyncChunkRebuild() || !ModLoader.hasSodium()) {
            return;
        }
        if (TICK_SEMAPHORE.availablePermits() >= 1) {
            return;
        }
        if (CullingStateManager.needPauseRebuild() || !SodiumSectionAsyncUtil.hasCuller()) {
            return;
        }
        ChunkCullingMap chunkCullingMap = CullingStateManager.CHUNK_CULLING_MAP;
        if (chunkCullingMap == null || !chunkCullingMap.isDone()) {
            return;
        }

        long interval = Config.getAsyncSignalIntervalNanos();
        if (interval > 0L) {
            long now = System.nanoTime();
            if (now - lastSignalNanos < interval) {
                return;
            }
            lastSignalNanos = now;
        }

        TICK_SEMAPHORE.release();
    }

    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        while (!finished) {
            try {
                TICK_SEMAPHORE.acquire();

                if (finished || Minecraft.getInstance().level == null) {
                    finished = true;
                    break;
                }

                ChunkCullingMap chunkCullingMap = CullingStateManager.CHUNK_CULLING_MAP;
                if (chunkCullingMap != null && chunkCullingMap.isDone()) {
                    if (Config.getAsyncChunkRebuild() && ModLoader.hasSodium()) {
                        long start = System.nanoTime();
                        SodiumSectionAsyncUtil.asyncSearchRebuildSection();
                        long durationNs = System.nanoTime() - start;
                        CullingStateManager.addChunkCullingTime(durationNs);

                        if (durationNs > BUDGET_NS) {
                            long cooldownMs = (durationNs - BUDGET_NS) / 1_000_000L;
                            if (cooldownMs > 0) {
                                Thread.sleep(cooldownMs);
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                CullingStateManager.LOGGER.error("Error in culling thread", e);
            }
        }
    }
}