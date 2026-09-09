package misanthropy.brute_force_culling_revived.api;

import com.google.common.collect.ImmutableList;
import misanthropy.brute_force_culling_revived.util.Benchmark;
import misanthropy.brute_force_culling_revived.api.data.ChunkCullingMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Config {

    public static ForgeConfigSpec CLIENT_CONFIG;

    private static final ForgeConfigSpec.DoubleValue SAMPLING;
    private static final ForgeConfigSpec.BooleanValue CULL_ENTITY;
    private static final ForgeConfigSpec.BooleanValue CULL_BLOCK_ENTITY;
    private static final ForgeConfigSpec.BooleanValue CULL_CHUNK;
    private static final ForgeConfigSpec.BooleanValue TICK_CULLING;
    private static final ForgeConfigSpec.BooleanValue ASYNC;
    private static final ForgeConfigSpec.BooleanValue AUTO_DISABLE_ASYNC;
    private static final ForgeConfigSpec.IntValue UPDATE_DELAY;
    private static final ForgeConfigSpec.IntValue ASYNC_SIGNAL_HZ;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_SKIP;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_ENTITY_SKIP;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOD_SKIP;

    private static final double DEFAULT_SAMPLING = 0.5;
    private static final double MIN_SAMPLING = 0.05;

    private static final Map<EntityType<?>, Boolean> ENTITY_SKIP_CACHE = new ConcurrentHashMap<>();
    private static final Map<BlockEntityType<?>, Boolean> BLOCK_ENTITY_SKIP_CACHE = new ConcurrentHashMap<>();

    private static volatile boolean loaded = false;

    public static void setLoaded() {
        if (!loaded) loaded = true;
    }

    private static boolean unload() {
        return !loaded;
    }

    private static boolean cullingOff() {
        return !loaded || CullingStateManager.SHADER_INIT_FAILED || Benchmark.cullingDisabled();
    }

    public static double getSampling() {
        return unload() ? DEFAULT_SAMPLING : Math.max(SAMPLING.get(), MIN_SAMPLING);
    }

    public static void setSampling(double value) {
        SAMPLING.set(Math.max(MIN_SAMPLING, value));
        SAMPLING.save();
    }

    public static boolean doEntityCulling() {
        return !cullingOff() && CullingStateManager.gl33() && (CULL_ENTITY.get() || CULL_BLOCK_ENTITY.get());
    }

    public static boolean getCullEntity() {
        return !cullingOff() && CullingStateManager.gl33() && CULL_ENTITY.get();
    }

    public static void setCullEntity(boolean value) {
        CULL_ENTITY.set(value);
        CULL_ENTITY.save();
    }

    public static boolean getCullBlockEntity() {
        return !cullingOff() && CullingStateManager.gl33() && CULL_BLOCK_ENTITY.get();
    }

    public static void setCullBlockEntity(boolean value) {
        CULL_BLOCK_ENTITY.set(value);
        CULL_BLOCK_ENTITY.save();
    }

    public static boolean getTickCulling() {
        return !cullingOff() && TICK_CULLING.get();
    }

    public static void setTickCulling(boolean value) {
        TICK_CULLING.set(value);
        TICK_CULLING.save();
    }

    public static boolean getCullChunk() {
        return !cullingOff() && CULL_CHUNK.get();
    }

    public static boolean shouldCullChunk() {
        if (cullingOff()) return false;
        ChunkCullingMap chunkCullingMap = CullingStateManager.CHUNK_CULLING_MAP;
        return chunkCullingMap != null && chunkCullingMap.isDone() && CULL_CHUNK.get();
    }

    public static void setCullChunk(boolean value) {
        CULL_CHUNK.set(value);
        CULL_CHUNK.save();
    }

    private static boolean asyncAvailable() {
        return !unload()
                && shouldCullChunk()
                && !CullingStateManager.needPauseRebuild()
                && ModLoader.hasSodium()
                && !ModLoader.hasNvidium();
    }

    public static boolean getAsyncChunkRebuild() {
        if (!asyncAvailable()) return false;
        if (getAutoDisableAsync() && CullingStateManager.enabledShader()) return false;
        return ASYNC.get();
    }

    public static void setAsyncChunkRebuild(boolean value) {
        if (!shouldCullChunk() || !ModLoader.hasSodium() || CullingStateManager.needPauseRebuild() || ModLoader.hasNvidium()) return;
        ASYNC.set(value);
        ASYNC.save();
    }

    public static boolean getAutoDisableAsync() {
        return !unload() && AUTO_DISABLE_ASYNC.get();
    }

    public static void setAutoDisableAsync(boolean value) {
        AUTO_DISABLE_ASYNC.set(value);
        AUTO_DISABLE_ASYNC.save();
    }

    public static int getShaderDynamicDelay() {
        return CullingStateManager.enabledShader() ? 1 : 0;
    }

    public static int getDepthUpdateDelay() {
        if (unload()) return 1;
        int delay = UPDATE_DELAY.get();
        return delay <= 9 ? delay + getShaderDynamicDelay() : delay;
    }

    public static long getAsyncSignalIntervalNanos() {
        if (unload()) return 0L;
        int hz = ASYNC_SIGNAL_HZ.get();
        return hz <= 0 ? 0L : 1_000_000_000L / hz;
    }

    public static void setDepthUpdateDelay(int value) {
        UPDATE_DELAY.set(value);
        UPDATE_DELAY.save();
    }

    public static List<? extends String> getEntitiesSkip() {
        return unload() ? ImmutableList.of() : ENTITY_SKIP.get();
    }

    public static List<? extends String> getBlockEntitiesSkip() {
        return unload() ? ImmutableList.of() : BLOCK_ENTITY_SKIP.get();
    }

    public static List<? extends String> getModsSkip() {
        return unload() ? ImmutableList.of() : MOD_SKIP.get();
    }

    public static boolean shouldSkipEntityType(EntityType<?> type) {
        Boolean cached = ENTITY_SKIP_CACHE.get(type);
        if (cached != null) return cached;
        if (unload()) return false;
        boolean skip = isSkipped(ForgeRegistries.ENTITY_TYPES.getKey(type), getEntitiesSkip());
        ENTITY_SKIP_CACHE.put(type, skip);
        return skip;
    }

    public static boolean shouldSkipBlockEntityType(BlockEntityType<?> type) {
        Boolean cached = BLOCK_ENTITY_SKIP_CACHE.get(type);
        if (cached != null) return cached;
        if (unload()) return false;
        boolean skip = isSkipped(BlockEntityType.getKey(type), getBlockEntitiesSkip());
        BLOCK_ENTITY_SKIP_CACHE.put(type, skip);
        return skip;
    }

    private static boolean isSkipped(ResourceLocation key, List<? extends String> typeSkip) {
        return key != null && (getModsSkip().contains(key.getNamespace()) || typeSkip.contains(key.toString()));
    }

    public static void clearTypeSkipCaches() {
        ENTITY_SKIP_CACHE.clear();
        BLOCK_ENTITY_SKIP_CACHE.clear();
    }

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("Sampling multiple");
        SAMPLING = builder.defineInRange("multiple", DEFAULT_SAMPLING, 0.0, 1.0);
        builder.pop();

        builder.push("Culling Map update delay");
        UPDATE_DELAY = builder.defineInRange("delay frame", 1, 0, 10);
        builder.pop();

        builder.push("Async search signal rate");
        builder.comment(
                "Highest rate, in hertz, at which the render thread wakes the occlusion culling thread.",
                "Each wake is a kernel call, so signalling once per frame costs real time at high frame rates",
                "while the culling map itself only refreshes every few frames. 0 disables the limit.");
        ASYNC_SIGNAL_HZ = builder.defineInRange("max hertz", 120, 0, 1000);
        builder.pop();

        builder.push("Cull entity");
        CULL_ENTITY = builder.define("enabled", true);
        builder.pop();

        builder.push("Cull block entity");
        CULL_BLOCK_ENTITY = builder.define("enabled", true);
        builder.pop();

        builder.push("Cull chunk");
        CULL_CHUNK = builder.define("enabled", true);
        builder.pop();

        builder.push("Tick culling");
        builder.comment(
                "Skips the client-side tick of entities that are currently culled.",
                "Saves CPU when many entities are loaded, but culled entities stop producing",
                "sounds, particles and animation until they become visible again.",
                "Entities close to the camera, glowing entities, vehicles and anything on the",
                "skip lists always keep ticking.");
        TICK_CULLING = builder.define("enabled", false);
        builder.pop();

        builder.push("Async chunk rebuild");
        ASYNC = builder.define("enabled", true);
        builder.pop();

        builder.push("Auto disable async rebuild");
        AUTO_DISABLE_ASYNC = builder.define("enabled", true);
        builder.pop();

        builder.comment("Entity skip CULLING").push("Entity ResourceLocation");
        ENTITY_SKIP = builder
                .comment("Entities that skip culling, example: [\"minecraft:creeper\", \"minecraft:zombie\"]")
                .defineList("list", List.of("create:stationary_contraption", "minecraft:warden"), o -> o instanceof String);
        builder.pop();

        builder.comment("Block Entity skip CULLING").push("Block Entity ResourceLocation");
        BLOCK_ENTITY_SKIP = builder
                .comment("Block entities that skip culling, example: [\"minecraft:chest\", \"minecraft:mob_spawner\"]")
                .defineList("list", List.of("minecraft:beacon"), o -> o instanceof String);
        builder.pop();

        builder.comment("Mod namespace skip CULLING").push("Mod Namespace");
        MOD_SKIP = builder
                .comment("Entire mods that skip culling (matched against ResourceLocation namespace). " +
                        "Use this for mods whose blocks/entities flicker or behave incorrectly with culling. " +
                        "Example: [\"ars_nouveau\", \"botania\"]")
                .defineList("list", List.of("ars_nouveau"), o -> o instanceof String);
        builder.pop();

        CLIENT_CONFIG = builder.build();
    }
}
