package misanthropy.brute_force_culling_revived.api;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.*;
import misanthropy.brute_force_culling_revived.api.data.ChunkCullingMap;
import misanthropy.brute_force_culling_revived.api.data.EntityCullingMap;
import misanthropy.brute_force_culling_revived.api.impl.ICullingShader;
import misanthropy.brute_force_culling_revived.instanced.EntityCullingInstanceRenderer;
import misanthropy.brute_force_culling_revived.util.Benchmark;
import misanthropy.brute_force_culling_revived.mixin.AccessorFrustum;
import misanthropy.brute_force_culling_revived.mixin.AccessorMinecraft;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class CullingRenderEvent {

    public static EntityCullingInstanceRenderer ENTITY_CULLING_INSTANCE_RENDERER;

    private static final float[] VEC3_BUFFER = new float[3];
    private static final float[] FRUSTUM_BUFFER = new float[24];
    private static final float[] DEPTH_SIZE_BUFFER = new float[10];

    static {
        RenderSystem.recordRenderCall(() -> ENTITY_CULLING_INSTANCE_RENDERER = new EntityCullingInstanceRenderer());
    }

    private static final long DEBUG_REFRESH_INTERVAL_MS = 100L;
    private static final int PANEL_TOP = 20;

    private long nextDebugUpdateTime;
    private final List<String> monitorTexts = new ArrayList<>();
    private int panelHalfWidth = 80;
    private int panelBottom = 0;

    private final List<String> benchTexts = new ArrayList<>();
    private String cachedBenchStatus = "";
    private int cachedBenchReportSize = -1;
    private int benchHalfWidth = 80;

    protected static void updateCullingMap() {
        if (!CullingStateManager.anyCulling() || CullingStateManager.checkCulling)
            return;

        CullingStateManager.callDepthTexture();

        EntityCullingMap entityCullingMap = CullingStateManager.ENTITY_CULLING_MAP;
        if (Config.doEntityCulling() && entityCullingMap != null && entityCullingMap.readyForDraw()) {
            CullingStateManager.ENTITY_CULLING_MAP_TARGET.clear(Minecraft.ON_OSX);
            CullingStateManager.ENTITY_CULLING_MAP_TARGET.bindWrite(false);
            entityCullingMap.getEntityTable().addEntityAttribute(CullingRenderEvent.ENTITY_CULLING_INSTANCE_RENDERER::addInstanceAttrib);
            ENTITY_CULLING_INSTANCE_RENDERER.drawWithShader(CullingStateManager.INSTANCED_ENTITY_CULLING_SHADER);
            entityCullingMap.markDrawn();
        }

        ChunkCullingMap chunkCullingMap = CullingStateManager.CHUNK_CULLING_MAP;
        if (Config.getCullChunk() && chunkCullingMap != null && chunkCullingMap.readyForDraw()) {
            CullingStateManager.useShader(CullingStateManager.CHUNK_CULLING_SHADER);
            CullingStateManager.CHUNK_CULLING_MAP_TARGET.clear(Minecraft.ON_OSX);
            CullingStateManager.CHUNK_CULLING_MAP_TARGET.bindWrite(false);

            Tesselator tessellator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tessellator.getBuilder();
            bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            bufferbuilder.vertex(-1.0f, -1.0f, 0.0f).endVertex();
            bufferbuilder.vertex(1.0f, -1.0f, 0.0f).endVertex();
            bufferbuilder.vertex(1.0f, 1.0f, 0.0f).endVertex();
            bufferbuilder.vertex(-1.0f, 1.0f, 0.0f).endVertex();
            BufferUploader.drawWithShader(bufferbuilder.end());
            chunkCullingMap.markDrawn();
        }

        CullingStateManager.bindMainFrameTarget();
    }

    private void renderText(@NotNull GuiGraphics guiGraphics, @NotNull List<String> list, int width, int height, Font font) {
        for (int i = 0; i < list.size(); ++i) {
            String text = list.get(i);
            guiGraphics.drawString(font, text, (int) (width - (font.width(text) / 2f)), height + font.lineHeight * i, 0xFFFFFF);
        }
    }

    @SuppressWarnings("resource")
    public static void setUniform(@NotNull ShaderInstance shader) {
        ICullingShader shaderInstance = (ICullingShader) shader;
        if (!shaderInstance.bruteForceRenderingRevived$hasCullingUniforms()) return;

        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();

        Uniform cameraPos = shaderInstance.bruteForceRenderingRevived$getCullingCameraPos();
        if (cameraPos != null) {
            Vec3 pos = camera.getPosition();
            VEC3_BUFFER[0] = (float) pos.x;
            VEC3_BUFFER[1] = (float) pos.y;
            VEC3_BUFFER[2] = (float) pos.z;
            cameraPos.set(VEC3_BUFFER);
        }

        Uniform cameraDir = shaderInstance.bruteForceRenderingRevived$getCullingCameraDir();
        if (cameraDir != null) {
            Vector3f dir = camera.getLookVector();
            VEC3_BUFFER[0] = dir.x;
            VEC3_BUFFER[1] = dir.y;
            VEC3_BUFFER[2] = dir.z;
            cameraDir.set(VEC3_BUFFER);
        }

        Uniform boxScale = shaderInstance.bruteForceRenderingRevived$getBoxScale();
        if (boxScale != null) boxScale.set(4.0f);

        if (CullingStateManager.FRUSTUM != null) {
            Uniform frustumPos = shaderInstance.bruteForceRenderingRevived$getFrustumPos();
            if (frustumPos != null) {
                AccessorFrustum accessor = (AccessorFrustum) CullingStateManager.FRUSTUM;
                VEC3_BUFFER[0] = (float) accessor.camX();
                VEC3_BUFFER[1] = (float) accessor.camY();
                VEC3_BUFFER[2] = (float) accessor.camZ();
                frustumPos.set(VEC3_BUFFER);
            }

            Uniform frustum = shaderInstance.bruteForceRenderingRevived$getCullingFrustum();
            if (frustum != null) {
                Vector4f[] frustumData = ModLoader.getFrustumPlanes(((AccessorFrustum) CullingStateManager.FRUSTUM).frustumIntersection());

                int planeCount = Math.min(frustumData.length, 6);
                if (planeCount < 6) Arrays.fill(FRUSTUM_BUFFER, 0.0f);
                for (int i = 0; i < planeCount; i++) {
                    Vector4f vec = frustumData[i];
                    FRUSTUM_BUFFER[i * 4]     = vec.x();
                    FRUSTUM_BUFFER[i * 4 + 1] = vec.y();
                    FRUSTUM_BUFFER[i * 4 + 2] = vec.z();
                    FRUSTUM_BUFFER[i * 4 + 3] = vec.w();
                }
                frustum.set(FRUSTUM_BUFFER);
            }
        }

        Uniform viewMat = shaderInstance.bruteForceRenderingRevived$getCullingViewMat();
        if (viewMat != null) viewMat.set(CullingStateManager.VIEW_MATRIX);

        Uniform projMat = shaderInstance.bruteForceRenderingRevived$getCullingProjMat();
        if (projMat != null) projMat.set(CullingStateManager.PROJECTION_MATRIX);

        Uniform renderDist = shaderInstance.bruteForceRenderingRevived$getRenderDistance();
        if (renderDist != null) {
            float distance = mc.options.getEffectiveRenderDistance();
            if (shader == CullingStateManager.COPY_DEPTH_SHADER) {
                distance = (CullingStateManager.DEPTH_INDEX > 0) ? 2.0f : 0.0f;
            }
            renderDist.set(distance);
        }

        if (shader == CullingStateManager.COPY_DEPTH_SHADER && shader.SCREEN_SIZE != null) {
            int srcWidth;
            int srcHeight;
            if (CullingStateManager.DEPTH_INDEX > 0) {
                srcWidth = CullingStateManager.DEPTH_BUFFER_TARGET[CullingStateManager.DEPTH_INDEX - 1].width;
                srcHeight = CullingStateManager.DEPTH_BUFFER_TARGET[CullingStateManager.DEPTH_INDEX - 1].height;
            } else {
                srcWidth = mc.getMainRenderTarget().width;
                srcHeight = mc.getMainRenderTarget().height;
            }
            shader.SCREEN_SIZE.set((float) Math.max(srcWidth, 1), (float) Math.max(srcHeight, 1));
        }

        Uniform depthSize = shaderInstance.bruteForceRenderingRevived$getDepthSize();
        if (depthSize != null) {
            if (shader == CullingStateManager.COPY_DEPTH_SHADER) {
                Arrays.fill(DEPTH_SIZE_BUFFER, 0.0f);
                RenderTarget target = CullingStateManager.DEPTH_BUFFER_TARGET[CullingStateManager.DEPTH_INDEX];
                DEPTH_SIZE_BUFFER[0] = (float) target.width;
                DEPTH_SIZE_BUFFER[1] = (float) target.height;
            } else {
                for (int i = 0; i < CullingStateManager.DEPTH_SIZE; ++i) {
                    RenderTarget target = CullingStateManager.DEPTH_BUFFER_TARGET[i];
                    DEPTH_SIZE_BUFFER[i * 2]     = (float) target.width;
                    DEPTH_SIZE_BUFFER[i * 2 + 1] = (float) target.height;
                }
            }
            depthSize.set(DEPTH_SIZE_BUFFER);
        }

        Uniform cullingSize = shaderInstance.bruteForceRenderingRevived$getCullingSize();
        if (cullingSize != null) {
            cullingSize.set(
                    (float) CullingStateManager.CHUNK_CULLING_MAP_TARGET.width,
                    (float) CullingStateManager.CHUNK_CULLING_MAP_TARGET.height);
        }

        Uniform entityCullingSize = shaderInstance.bruteForceRenderingRevived$getEntityCullingSize();
        if (entityCullingSize != null) {
            entityCullingSize.set(
                    (float) CullingStateManager.ENTITY_CULLING_MAP_TARGET.width,
                    (float) CullingStateManager.ENTITY_CULLING_MAP_TARGET.height);
        }

        Uniform levelHeightOffset = shaderInstance.bruteForceRenderingRevived$getLevelHeightOffset();
        if (levelHeightOffset != null) {
            levelHeightOffset.set(CullingStateManager.LEVEL_SECTION_RANGE);
        }

        Uniform levelMinSection = shaderInstance.bruteForceRenderingRevived$getLevelMinSection();
        if (levelMinSection != null) {
            Level level = mc.level;
            if (level != null) levelMinSection.set(level.getMinSection());
        }
    }

    @SubscribeEvent
    public void onOverlayRender(RenderGuiEvent.@NotNull Post event) {
        Minecraft mc = Minecraft.getInstance();
        Benchmark.onFrame();

        if (mc.player == null) return;

        if (Benchmark.isActive()) {
            renderBenchmark(event.getGuiGraphics(), mc);
            return;
        }

        if (CullingStateManager.DEBUG <= 0) return;

        Font font = mc.font;
        long currentTime = Util.getMillis();

        if (currentTime >= nextDebugUpdateTime) {
            nextDebugUpdateTime = currentTime + DEBUG_REFRESH_INTERVAL_MS;
            rebuildMonitorTexts(mc, font);
        }

        if (monitorTexts.isEmpty()) return;

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int centerX = mc.getWindow().getGuiScaledWidth() / 2;

        guiGraphics.fill(centerX - panelHalfWidth - 2, PANEL_TOP - 2, centerX + panelHalfWidth + 2, panelBottom + 2, 0x66000000);
        renderText(guiGraphics, monitorTexts, centerX, PANEL_TOP, font);

        if (CullingStateManager.checkTexture) {
            renderTexturePreviews(guiGraphics, mc);
        }
    }

    private void rebuildMonitorTexts(@NotNull Minecraft mc, @NotNull Font font) {
        boolean profiling = CullingStateManager.DEBUG > 1;
        monitorTexts.clear();

        monitorTexts.add("FPS: " + ((AccessorMinecraft) mc).getFps());

        String on = I18n.get("brute_force_culling_revived.enable");
        String off = I18n.get("brute_force_culling_revived.disable");

        monitorTexts.add(I18n.get("brute_force_culling_revived.cull_entity") + ": " + (Config.getCullEntity() ? on : off));
        monitorTexts.add(I18n.get("brute_force_culling_revived.entity_culling") + ": " +
                CullingStateManager.entityCulling + "/" + CullingStateManager.entityCount +
                (profiling ? " (" + formatMillis(CullingStateManager.entityCullingTime) + ")" : ""));

        if (Config.getTickCulling()) {
            monitorTexts.add(I18n.get("brute_force_culling_revived.tick_culling_count") + ": " +
                    CullingStateManager.tickCulling + "/" + CullingStateManager.tickEntityCount);
        }

        monitorTexts.add(I18n.get("brute_force_culling_revived.cull_block_entity") + ": " + (Config.getCullBlockEntity() ? on : off));
        monitorTexts.add(I18n.get("brute_force_culling_revived.block_culling") + ": " +
                CullingStateManager.blockCulling + "/" + CullingStateManager.blockCount +
                (profiling ? " (" + formatMillis(CullingStateManager.blockCullingTime) + ")" : ""));

        if (Config.doEntityCulling()) {
            monitorTexts.add(I18n.get("brute_force_culling_revived.entity_culling_init") + ": " +
                    formatMillis(CullingStateManager.entityCullingInitTime));
        }

        monitorTexts.add(I18n.get("brute_force_culling_revived.cull_chunk") + ": " + (Config.getCullChunk() ? on : off));
        if (Config.getCullChunk()) {
            monitorTexts.add(I18n.get("brute_force_culling_revived.chunk_culling") + ": " +
                    CullingStateManager.chunkCulling + "/" + CullingStateManager.chunkCount);
        }
        if (Config.getAsyncChunkRebuild() && ModLoader.hasSodium()) {
            monitorTexts.add(I18n.get("brute_force_culling_revived.chunk_culling_time") + ": " +
                    formatMillis(CullingStateManager.chunkCullingTime));
        }
        monitorTexts.add(I18n.get("brute_force_culling_revived.chunk_culling_init") + ": " +
                formatMillis(CullingStateManager.chunkCullingInitTime) + " (" + CullingStateManager.cullingInitCount + ")");

        int maxTextWidth = 0;
        for (String s : monitorTexts) {
            maxTextWidth = Math.max(maxTextWidth, font.width(s));
        }
        panelHalfWidth = Math.max(80, maxTextWidth / 2 + 10);
        panelBottom = PANEL_TOP + font.lineHeight * monitorTexts.size();
    }

    private static @NotNull String formatMillis(long nanos) {
        return String.format("%.2fms", nanos / 1_000_000.0);
    }

    private void renderBenchmark(@NotNull GuiGraphics guiGraphics, @NotNull Minecraft mc) {
        Font font = mc.font;
        String status = Benchmark.statusLine();
        List<String> report = Benchmark.getReport();

        if (!Objects.equals(status, cachedBenchStatus) || report.size() != cachedBenchReportSize) {
            cachedBenchStatus = status;
            cachedBenchReportSize = report.size();
            benchTexts.clear();
            if (status != null) benchTexts.add(status);
            benchTexts.addAll(report);

            int maxTextWidth = 0;
            for (String s : benchTexts) {
                maxTextWidth = Math.max(maxTextWidth, font.width(s));
            }
            benchHalfWidth = Math.max(80, maxTextWidth / 2 + 10);
        }

        if (benchTexts.isEmpty()) return;

        int centerX = mc.getWindow().getGuiScaledWidth() / 2;
        int bottom = PANEL_TOP + font.lineHeight * benchTexts.size();

        guiGraphics.fill(centerX - benchHalfWidth - 2, PANEL_TOP - 2, centerX + benchHalfWidth + 2, bottom + 2, 0xC0000000);
        renderText(guiGraphics, benchTexts, centerX, PANEL_TOP, font);
    }

    private void renderTexturePreviews(GuiGraphics guiGraphics, Minecraft mc) {
        float scale = 0.4f;
        int screenH = mc.getWindow().getGuiScaledHeight();
        int screenW = mc.getWindow().getGuiScaledWidth();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        for (int i = 0; i < CullingStateManager.DEPTH_TEXTURE.length; i++) {
            int size = (int) (screenH * scale);
            drawTextureId(guiGraphics, CullingStateManager.DEPTH_TEXTURE[i], 0, screenH - size, size, size);
            scale *= 0.5f;
        }

        int mapSize = (int) (screenH * 0.25f);
        if (Config.doEntityCulling()) {
            drawTextureId(guiGraphics, CullingStateManager.ENTITY_CULLING_MAP_TARGET.getColorTextureId(), screenW - mapSize, 0, mapSize, mapSize);
        }
        if (Config.getCullChunk()) {
            drawTextureId(guiGraphics, CullingStateManager.CHUNK_CULLING_MAP_TARGET.getColorTextureId(), screenW - mapSize, mapSize, mapSize, mapSize);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void drawTextureId(GuiGraphics guiGraphics, int textureId, int x, int y, int width, int height) {
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Matrix4f matrix = guiGraphics.pose().last().pose();
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(matrix, (float) x,         (float) y + height, 0.0F).uv(0.0F, 1.0F).endVertex();
        bufferbuilder.vertex(matrix, (float) x + width, (float) y + height, 0.0F).uv(1.0F, 1.0F).endVertex();
        bufferbuilder.vertex(matrix, (float) x + width, (float) y,          0.0F).uv(1.0F, 0.0F).endVertex();
        bufferbuilder.vertex(matrix, (float) x,         (float) y,          0.0F).uv(0.0F, 0.0F).endVertex();
        BufferUploader.drawWithShader(bufferbuilder.end());
    }
}