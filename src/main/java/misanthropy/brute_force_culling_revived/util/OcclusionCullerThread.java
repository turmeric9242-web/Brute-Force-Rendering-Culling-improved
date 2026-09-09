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
                        CullingStateManager.addChunkCullingTime(System.nanoTime() - start);
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