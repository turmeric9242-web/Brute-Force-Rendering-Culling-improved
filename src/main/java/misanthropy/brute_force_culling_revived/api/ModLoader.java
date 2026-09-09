package misanthropy.brute_force_culling_revived.api;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import misanthropy.brute_force_culling_revived.api.data.ChunkCullingMap;
import misanthropy.brute_force_culling_revived.api.impl.IAABBObject;
import misanthropy.brute_force_culling_revived.gui.ConfigScreen;
import misanthropy.brute_force_culling_revived.util.Benchmark;
import misanthropy.brute_force_culling_revived.util.NvidiumUtil;
import misanthropy.brute_force_culling_revived.util.OcclusionCullerThread;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.FrustumIntersection;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Thread.MAX_PRIORITY;
import static misanthropy.brute_force_culling_revived.api.CullingStateManager.*;

@Mod("brute_force_culling_revived")
public class ModLoader {
    private static Field frustumPlanesField;

    private static Set<String> loadedModIds;

    public ModLoader() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {

            loadedModIds = FMLLoader.getLoadingModList().getMods().stream()
                    .map(ModInfo::getModId)
                    .collect(Collectors.toUnmodifiableSet());

            registerShader();
            MinecraftForge.EVENT_BUS.register(this);
            MinecraftForge.EVENT_BUS.register(new CullingRenderEvent());
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_CONFIG);
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerKeyBinding);
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigChanged);

            CullingStateManager.init();
        });
    }

    public static final KeyMapping CONFIG_KEY = new KeyMapping(MOD_ID + ".key.config",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.category." + MOD_ID);

    public static final KeyMapping DEBUG_KEY = new KeyMapping(MOD_ID + ".key.debug",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.category." + MOD_ID);

    public void registerKeyBinding(@NotNull RegisterKeyMappingsEvent event) {
        event.register(CONFIG_KEY);
        event.register(DEBUG_KEY);
    }

    private void onConfigChanged(final ModConfigEvent event) {
        Config.clearTypeSkipCaches();
    }

    private void registerShader() {
        RenderSystem.recordRenderCall(this::initShader);
    }

    private void initShader() {
        LOGGER.debug("try init shader chunk_culling");
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            CHUNK_CULLING_SHADER           = new ShaderInstance(rm, new ResourceLocation(MOD_ID, "chunk_culling"),             DefaultVertexFormat.POSITION);
            INSTANCED_ENTITY_CULLING_SHADER = new ShaderInstance(rm, new ResourceLocation(MOD_ID, "instanced_entity_culling"), DefaultVertexFormat.POSITION);
            COPY_DEPTH_SHADER              = new ShaderInstance(rm, new ResourceLocation(MOD_ID, "copy_depth"),               DefaultVertexFormat.POSITION);
            REMOVE_COLOR_SHADER            = new ShaderInstance(rm, new ResourceLocation(MOD_ID, "remove_color"),             DefaultVertexFormat.POSITION_COLOR_TEX);
        } catch (Throwable t) {
            SHADER_INIT_FAILED = true;
            CHUNK_CULLING_SHADER = null;
            INSTANCED_ENTITY_CULLING_SHADER = null;
            COPY_DEPTH_SHADER = null;
            REMOVE_COLOR_SHADER = null;
            LOGGER.error("Failed to init culling shaders, culling disabled for this session", t);
        }
    }

    @SubscribeEvent
    public void onKeyboardInput(InputEvent.@NotNull Key event) {
        if (event.getAction() != InputConstants.PRESS) return;
        onBindingPressed(InputConstants.getKey(event.getKey(), event.getScanCode()));
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseButton.@NotNull Post event) {
        if (event.getAction() != InputConstants.PRESS) return;
        onBindingPressed(InputConstants.Type.MOUSE.getOrCreate(event.getButton()));
    }

    private static void onBindingPressed(InputConstants.Key pressed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (isTriggered(CONFIG_KEY, pressed)) {
            mc.setScreen(new ConfigScreen(Component.translatable(MOD_ID + ".config")));
        } else if (isTriggered(DEBUG_KEY, pressed)) {
            if (Benchmark.isActive()) {
                Benchmark.cancel();
                return;
            }
            DEBUG = (DEBUG + 1) % 3;
            if (DEBUG == 0) {
                checkCulling = false;
                checkTexture = false;
            }
        }
    }

    private static boolean isTriggered(@NotNull KeyMapping mapping, InputConstants.Key pressed) {
        return mapping.getKey().equals(pressed) && mapping.isDown();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.@NotNull ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getInstance();

        Benchmark.onClientTick();

        tickCulling = 0;
        tickEntityCount = 0;

        if (mc.player != null && mc.level != null) {
            clientTickCount++;
            ChunkCullingMap chunkCullingMap = CHUNK_CULLING_MAP;
            if (mc.player.tickCount > 60 && clientTickCount > 60
                    && chunkCullingMap != null && !chunkCullingMap.isDone()) {
                chunkCullingMap.setDone();
                updateLevelBounds(mc.level);

                OcclusionCullerThread occlusionCullerThread = new OcclusionCullerThread();
                occlusionCullerThread.setName("Chunk Depth Occlusion Cull thread");
                occlusionCullerThread.setPriority(MAX_PRIORITY);
                occlusionCullerThread.start();
            }
            Config.setLoaded();
        } else {
            cleanup();
        }
    }

    public static Vector4f[] getFrustumPlanes(FrustumIntersection frustum) {
        try {
            if (frustumPlanesField == null) {
                frustumPlanesField = FrustumIntersection.class.getDeclaredField("planes");
                frustumPlanesField.setAccessible(true);
            }
            return (Vector4f[]) frustumPlanesField.get(frustum);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOGGER.error("Failed to get frustum planes", e);
        }

        return new Vector4f[0];
    }

    public static boolean hasMod(String id) {
        return loadedModIds != null && loadedModIds.contains(id);
    }

    public static boolean hasSodium() {
        return hasMod("sodium") || hasMod("embeddium");
    }

    public static boolean hasIris() {
        return hasMod("iris") || hasMod("oculus");
    }

    public static boolean hasNvidium() {
        return hasMod("nvidium") && NvidiumUtil.nvidiumBfs();
    }

    public static @Nullable AABB getObjectAABB(Object o) {
        if (o instanceof BlockEntity be)   return be.getRenderBoundingBox();
        if (o instanceof Entity e)         return e.getBoundingBox();
        if (o instanceof IAABBObject aabb) return aabb.getAABB();
        return null;
    }

    public static void pauseAsync() {
        fullChunkUpdateCooldown = 70;
    }
}