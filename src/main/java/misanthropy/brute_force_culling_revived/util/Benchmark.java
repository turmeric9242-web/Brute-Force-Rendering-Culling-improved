package misanthropy.brute_force_culling_revived.util;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import misanthropy.brute_force_culling_revived.api.CullingStateManager;
import misanthropy.brute_force_culling_revived.api.data.EntityCullingMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class Benchmark {

    private static final int MOB_COUNT = 400;
    private static final int BLOCK_ENTITY_COUNT = 150;
    private static final int WALL_RADIUS = 6;
    private static final int BLOCK_ENTITY_RADIUS = 12;
    private static final int ROUNDS = 3;

    private static final double MOB_MIN_RADIUS = 10.0;
    private static final double MOB_MAX_RADIUS = 40.0;
    private static final double MOB_Y_SPREAD = 8.0;
    private static final long WARMUP_MS = 3000L;
    private static final long PASS_MS = 4000L;
    private static final long SETTLE_MS = 2000L;
    private static final float SWEEP_DEGREES = 360.0f / ROUNDS;
    private static final long SPAWN_SEED = 0x5EED1234L;
    private static final int ENTITY_ID_BASE = Integer.MAX_VALUE - 4096;
    private static final double DRIFT_ABORT_SQ = 4.0;
    private static final int UNLIMITED_FRAMERATE = 260;
    private static final long FRAME_TIMEOUT_MS = 3000L;

    private enum Phase { IDLE, WARMUP, BASELINE, TO_ON, CULLED, TO_OFF, DONE }

    private static final Pass BASELINE_PASS = new Pass();
    private static final Pass CULLED_PASS = new Pass();
    private static final List<String> REPORT = new ArrayList<>();
    private static final IntArrayList SPAWNED = new IntArrayList();
    private static final Map<BlockPos, BlockState> SAVED_BLOCKS = new LinkedHashMap<>();

    private static Phase phase = Phase.IDLE;
    private static long phaseStart;
    private static long lastFrameNanos;
    private static long lastFrameMillis;
    private static int round;
    private static String statusCache;

    private static double startX, startY, startZ;
    private static float startYaw, startPitch;
    private static int wallPlaced;
    private static int wallExpected;
    private static int blockEntitiesPlaced;
    private static int savedDebug;
    private static boolean savedVsync;
    private static int savedFramerateLimit;

    private Benchmark() {
    }

    public static boolean isRunning() {
        return phase != Phase.IDLE && phase != Phase.DONE;
    }

    public static boolean isActive() {
        return phase != Phase.IDLE;
    }

    public static boolean cullingDisabled() {
        return phase == Phase.WARMUP || phase == Phase.BASELINE || phase == Phase.TO_OFF;
    }

    public static @NotNull List<String> getReport() {
        return REPORT;
    }

    public static void toggle(boolean on) {
        if (on) start(); else cancel();
    }

    public static void start() {
        if (isRunning()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (mc.player == null || level == null) return;

        reset();

        startX = mc.player.getX();
        startY = mc.player.getY();
        startZ = mc.player.getZ();
        startYaw = mc.player.getYRot();
        startPitch = mc.player.getXRot();

        savedDebug = CullingStateManager.DEBUG;
        savedVsync = mc.options.enableVsync().get();
        savedFramerateLimit = mc.options.framerateLimit().get();

        CullingStateManager.DEBUG = 1;
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(UNLIMITED_FRAMERATE);
        mc.getWindow().updateVsync(false);
        mc.getWindow().setFramerateLimit(UNLIMITED_FRAMERATE);

        spawnMobs(level);
        buildScene(mc, level);

        round = 1;
        lastFrameMillis = Util.getMillis();
        enter(Phase.WARMUP);

        long total = WARMUP_MS + ROUNDS * (PASS_MS * 2 + SETTLE_MS * 2);
        say(mc, "Benchmark started: " + SPAWNED.size() + " mobs, " + blockEntitiesPlaced
                + " block entities, walls " + (wallPlaced == wallExpected ? "u sealed" : "INCOMPLETE") + ", "
                + ROUNDS + " Stand still. Do not move for ~" + (total / 1000L) + "s.");
    }

    public static void cancel() {
        if (phase == Phase.IDLE) return;
        Minecraft mc = Minecraft.getInstance();
        boolean aborted = isRunning();
        teardown(mc);
        phase = Phase.IDLE;
        REPORT.clear();
        if (aborted) say(mc, "Benchmark cancelled.");
    }

    public static void onFrame() {
        if (!isRunning()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            abort(mc, "level unloaded");
            return;
        }

        double dx = mc.player.getX() - startX;
        double dy = mc.player.getY() - startY;
        double dz = mc.player.getZ() - startZ;
        if (dx * dx + dy * dy + dz * dz > DRIFT_ABORT_SQ) {
            abort(mc, "player moved");
            return;
        }

        long now = Util.getNanos();
        long millis = Util.getMillis();
        long elapsed = millis - phaseStart;
        lastFrameMillis = millis;

        float base = startYaw + SWEEP_DEGREES * (round - 1);
        boolean measuring = phase == Phase.BASELINE || phase == Phase.CULLED;
        mc.player.setYRot(measuring ? base + SWEEP_DEGREES * (elapsed / (float) PASS_MS) : base);
        mc.player.setXRot(startPitch);

        if (lastFrameNanos != 0L) {
            if (phase == Phase.BASELINE) BASELINE_PASS.sample(now - lastFrameNanos);
            else if (phase == Phase.CULLED) CULLED_PASS.sample(now - lastFrameNanos);
        }
        lastFrameNanos = now;

        switch (phase) {
            case WARMUP -> { if (elapsed >= WARMUP_MS) enter(Phase.BASELINE); }
            case BASELINE -> {
                if (elapsed >= PASS_MS) {
                    BASELINE_PASS.endRound();
                    enter(Phase.TO_ON);
                }
            }
            case TO_ON -> { if (elapsed >= SETTLE_MS) enter(Phase.CULLED); }
            case CULLED -> {
                if (elapsed >= PASS_MS) {
                    CULLED_PASS.endRound();
                    if (round >= ROUNDS) {
                        finish(mc);
                    } else {
                        round++;
                        enter(Phase.TO_OFF);
                    }
                }
            }
            case TO_OFF -> { if (elapsed >= SETTLE_MS) enter(Phase.BASELINE); }
            default -> { }
        }
    }

    public static void onClientTick() {
        if (!isRunning()) return;
        if (Util.getMillis() - lastFrameMillis > FRAME_TIMEOUT_MS) {
            abort(Minecraft.getInstance(), "HUD hidden or rendering stalled");
        }
    }

    private static void enter(Phase next) {
        phase = next;
        phaseStart = Util.getMillis();
        lastFrameNanos = 0L;
        statusCache = describe(next);
    }

    private static @Nullable String describe(Phase p) {
        String what = switch (p) {
            case WARMUP -> "warming up";
            case BASELINE -> "measuring WITHOUT culling";
            case TO_ON, TO_OFF -> "settling";
            case CULLED -> "measuring WITH culling";
            default -> null;
        };
        return what == null ? null : "Benchmark round " + round + "/" + ROUNDS + ": " + what + "...";
    }

    private static void finish(Minecraft mc) {
        buildReport();
        teardown(mc);
        phase = Phase.DONE;
        for (String line : REPORT) {
            CullingStateManager.LOGGER.info("[benchmark] {}", line);
            say(mc, line);
        }
    }

    private static void abort(Minecraft mc, String why) {
        teardown(mc);
        phase = Phase.IDLE;
        REPORT.clear();
        say(mc, "Benchmark aborted: " + why + ".");
    }

    private static void buildReport() {
        REPORT.clear();
        Stats off = BASELINE_PASS.stats();
        Stats on = CULLED_PASS.stats();

        String walls = wallExpected == 0 ? "walls off (multiplayer)"
                : wallPlaced == wallExpected ? "walls sealed (" + wallPlaced + ")"
                : "WALLS INCOMPLETE " + wallPlaced + "/" + wallExpected + " - RESULTS INVALID";
        REPORT.add("Benchmark: " + SPAWNED.size() + " mobs, " + blockEntitiesPlaced
                + " block entities, " + walls);
        REPORT.add(String.format("%d rounds interleaved, %.1fs per pass, %.0f deg pan each (360 total)",
                ROUNDS, PASS_MS / 1000.0, SWEEP_DEGREES));
        REPORT.add("Culling OFF: " + off);
        REPORT.add("Culling ON : " + on);

        if (off.medianMs > 0.0 && on.medianMs > 0.0) {
            double frameDelta = (on.medianMs - off.medianMs) / off.medianMs * 100.0;
            double fpsDelta = (off.medianMs / on.medianMs - 1.0) * 100.0;
            REPORT.add(String.format("Median frame time %+.1f%%   FPS %+.1f%%", frameDelta, fpsDelta));
        }

        REPORT.add(String.format("Round-to-round spread: OFF %.1f%%   ON %.1f%%",
                BASELINE_PASS.roundSpreadPercent(), CULLED_PASS.roundSpreadPercent()));

        REPORT.add("Entities culled: " + CULLED_PASS.avg(CULLED_PASS.entityCulled) + "/"
                + CULLED_PASS.avg(CULLED_PASS.entityTotal) + " in view, "
                + CULLED_PASS.avg(CULLED_PASS.entityTotal - CULLED_PASS.entityCulled) + "/"
                + SPAWNED.size() + " spawned still drawn");
        REPORT.add("Block entities culled: " + CULLED_PASS.avg(CULLED_PASS.blockCulled) + "/"
                + CULLED_PASS.avg(CULLED_PASS.blockTotal) + " in view, "
                + CULLED_PASS.avg(CULLED_PASS.blockTotal - CULLED_PASS.blockCulled) + "/"
                + blockEntitiesPlaced + " placed still drawn");
        REPORT.add("Chunks culled: " + CULLED_PASS.avg(CULLED_PASS.chunkCulled) + "/"
                + CULLED_PASS.avg(CULLED_PASS.chunkTotal) + " tested");
        REPORT.add("Tick culled: " + CULLED_PASS.avg(CULLED_PASS.tickCulled) + "/"
                + CULLED_PASS.avg(CULLED_PASS.tickTotal) + " ticking");

        EntityCullingMap entityMap = CullingStateManager.ENTITY_CULLING_MAP;
        if (entityMap != null) {
            int slots = CullingStateManager.ENTITY_CULLING_MAP_TARGET.width
                    * CullingStateManager.ENTITY_CULLING_MAP_TARGET.height;
            REPORT.add("Entity culling map: " + entityMap.getEntityTable().size() + "/" + slots + " slots");
        }
    }

    private static void spawnMobs(@NotNull ClientLevel level) {
        Random random = new Random(SPAWN_SEED);
        double minSq = MOB_MIN_RADIUS * MOB_MIN_RADIUS;
        double maxSq = MOB_MAX_RADIUS * MOB_MAX_RADIUS;

        for (int i = 0; i < MOB_COUNT; i++) {
            Mob mob = EntityType.ZOMBIE.create(level);
            if (mob == null) break;

            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = Math.sqrt(minSq + random.nextDouble() * (maxSq - minSq));
            double y = startY + (random.nextDouble() - 0.5) * MOB_Y_SPREAD;

            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setSilent(true);
            mob.setPos(startX + Math.cos(angle) * radius, y, startZ + Math.sin(angle) * radius);
            mob.setOldPosAndRot();

            int id = ENTITY_ID_BASE + i;
            mob.setId(id);
            level.putNonPlayerEntity(id, mob);
            SPAWNED.add(id);
        }
    }

    private static void buildScene(@NotNull Minecraft mc, @NotNull ClientLevel level) {
        if (!mc.hasSingleplayerServer()) return;

        double lowest = level.getMinBuildHeight() + WALL_RADIUS;
        double highest = level.getMaxBuildHeight() - 1 - WALL_RADIUS;
        BlockPos center = BlockPos.containing(startX,
                Math.min(Math.max(startY + 1.0, lowest), highest), startZ);
        placeShell(level, center, WALL_RADIUS, Blocks.STONE.defaultBlockState());
        placeBlockEntities(level, center);
    }

    private static void placeShell(@NotNull ClientLevel level, @NotNull BlockPos center, int radius, BlockState state) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(y) != radius && Math.abs(z) != radius) continue;
                    wallExpected++;
                    if (setTracked(level, center.offset(x, y, z), state)) wallPlaced++;
                }
            }
        }
    }

    private static void placeBlockEntities(@NotNull ClientLevel level, @NotNull BlockPos center) {
        List<BlockPos> shell = new ArrayList<>();
        for (int x = -BLOCK_ENTITY_RADIUS; x <= BLOCK_ENTITY_RADIUS; x++) {
            for (int y = -BLOCK_ENTITY_RADIUS; y <= BLOCK_ENTITY_RADIUS; y++) {
                for (int z = -BLOCK_ENTITY_RADIUS; z <= BLOCK_ENTITY_RADIUS; z++) {
                    if (Math.abs(x) != BLOCK_ENTITY_RADIUS
                            && Math.abs(y) != BLOCK_ENTITY_RADIUS
                            && Math.abs(z) != BLOCK_ENTITY_RADIUS) continue;
                    BlockPos pos = center.offset(x, y, z);
                    if (level.isInWorldBounds(pos)) shell.add(pos);
                }
            }
        }
        if (shell.isEmpty()) return;

        BlockState chest = Blocks.CHEST.defaultBlockState();
        int stride = Math.max(1, shell.size() / BLOCK_ENTITY_COUNT);

        for (int i = 0; i < shell.size() && blockEntitiesPlaced < BLOCK_ENTITY_COUNT; i += stride) {
            BlockPos pos = shell.get(i);
            if (SAVED_BLOCKS.containsKey(pos)) continue;
            if (setTracked(level, pos, chest)) blockEntitiesPlaced++;
        }
    }

    private static boolean setTracked(@NotNull ClientLevel level, @NotNull BlockPos pos, BlockState state) {
        if (!level.isInWorldBounds(pos)) return false;

        BlockPos key = pos.immutable();
        SAVED_BLOCKS.computeIfAbsent(key, level::getBlockState);
        level.setBlock(key, state, Block.UPDATE_ALL);
        return true;
    }

    private static void teardown(@NotNull Minecraft mc) {
        ClientLevel level = mc.level;

        if (level != null) {
            for (int i = 0; i < SPAWNED.size(); i++) {
                level.removeEntity(SPAWNED.getInt(i), Entity.RemovalReason.DISCARDED);
            }
            for (Map.Entry<BlockPos, BlockState> entry : SAVED_BLOCKS.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL);
            }
        }
        SPAWNED.clear();
        SAVED_BLOCKS.clear();

        if (mc.player != null) {
            mc.player.setYRot(startYaw);
            mc.player.setXRot(startPitch);
        }

        CullingStateManager.DEBUG = savedDebug;
        mc.options.enableVsync().set(savedVsync);
        mc.options.framerateLimit().set(savedFramerateLimit);
        mc.getWindow().updateVsync(savedVsync);
        mc.getWindow().setFramerateLimit(savedFramerateLimit);
    }

    private static void reset() {
        BASELINE_PASS.clear();
        CULLED_PASS.clear();
        REPORT.clear();
        SPAWNED.clear();
        SAVED_BLOCKS.clear();
        wallPlaced = 0;
        wallExpected = 0;
        blockEntitiesPlaced = 0;
        lastFrameNanos = 0L;
        round = 0;
    }

    private static void say(@NotNull Minecraft mc, String message) {
        if (mc.gui != null) mc.gui.getChat().addMessage(Component.literal("[BFRC] " + message));
    }

    private static final class Pass {
        private final LongArrayList frames = new LongArrayList(32768);
        private final DoubleArrayList roundMeansMs = new DoubleArrayList();
        private int roundStart;
        private long entityCulled, entityTotal, blockCulled, blockTotal;
        private long chunkCulled, chunkTotal, tickCulled, tickTotal, samples;

        void clear() {
            frames.clear();
            roundMeansMs.clear();
            roundStart = 0;
            entityCulled = entityTotal = blockCulled = blockTotal = 0L;
            chunkCulled = chunkTotal = tickCulled = tickTotal = samples = 0L;
        }

        void sample(long frameNanos) {
            frames.add(frameNanos);
            entityCulled += CullingStateManager.entityCulling;
            entityTotal += CullingStateManager.entityCount;
            blockCulled += CullingStateManager.blockCulling;
            blockTotal += CullingStateManager.blockCount;
            chunkCulled += CullingStateManager.chunkCulling;
            chunkTotal += CullingStateManager.chunkCount;
            tickCulled += CullingStateManager.tickCulling;
            tickTotal += CullingStateManager.tickEntityCount;
            samples++;
        }

        void endRound() {
            int count = frames.size() - roundStart;
            if (count <= 0) return;

            long sum = 0L;
            for (int i = roundStart; i < frames.size(); i++) sum += frames.getLong(i);
            roundMeansMs.add(sum / (double) count / 1e6);
            roundStart = frames.size();
        }

        long avg(long total) {
            return samples == 0L ? 0L : total / samples;
        }

        double roundSpreadPercent() {
            if (roundMeansMs.size() < 2) return 0.0;

            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            double sum = 0.0;
            for (int i = 0; i < roundMeansMs.size(); i++) {
                double v = roundMeansMs.getDouble(i);
                min = Math.min(min, v);
                max = Math.max(max, v);
                sum += v;
            }

            double mean = sum / roundMeansMs.size();
            return mean <= 0.0 ? 0.0 : (max - min) / mean * 100.0;
        }

        @NotNull Stats stats() {
            if (frames.isEmpty()) return new Stats(0.0, 0.0, 0.0, 0.0);

            long[] sorted = frames.toLongArray();
            Arrays.sort(sorted);

            long sum = 0L;
            for (long v : sorted) sum += v;

            return new Stats(sum / (double) sorted.length / 1e6, percentile(sorted, 0.50),
                    percentile(sorted, 0.99), percentile(sorted, 0.999));
        }

        private static double percentile(long @NotNull [] sorted, double p) {
            int index = (int) Math.round(p * (sorted.length - 1));
            return sorted[Math.max(0, Math.min(sorted.length - 1, index))] / 1e6;
        }
    }

    private record Stats(double meanMs, double medianMs, double low1Ms, double low01Ms) {
        @Override
        public @NotNull String toString() {
            if (medianMs <= 0.0) return "no samples";
            return String.format("med %.2fms (%.0f fps)  avg %.2fms  lows %.2f / %.2fms",
                    medianMs, 1000.0 / medianMs, meanMs, low1Ms, low01Ms);
        }
    }

    public static @Nullable String statusLine() {
        return isRunning() ? statusCache : null;
    }
}
